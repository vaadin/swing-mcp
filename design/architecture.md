# Architecture

How the pieces compose — what no single symbol can say and what would be expensive to overturn.
**Normative: the code conforms.** Change this file first, then the code. Not here: why
(`decisions.md` — cite the `D_`), what Swing and `javax.accessibility` do (`research.md` — cite
the `R_`), one symbol's behaviour (its doc comment), one tool's rules
(`swing-mcp/spec/tools/`), the module map (`AGENTS.md`).

---

## Wiring

- Dependencies point one way: `swing-mcp` and `swing-mcp-proxy` → `swing-mcp-tool-defs` →
  `tiny-mcp-server`. Nothing depends on `swing-mcp`, and the proxy must never do so — it runs in
  a JVM with no UI in it.
- `SwingMCP` owns the `MCPHandler`, the `HttpMCPServer` wrapping it, the ref map, and `toolLock`.
  It composes the transport rather than extending it, and registers each tool through a wrapper
  that is the same for every tool.
- Every tool extends `AbstractSwingTool` and binds a `ToolDescriptor` from
  `swing-mcp-tool-defs` at construction; `getName` / `getDescription` / `getInputSchema` are
  final and delegate to it (`D_shared_tool_manifest`).
- `getConsideredComponents()` is the single seam that decides what any tool can see. It returns
  `List<Component>`, not `List<Window>`, precisely so a headless test can substitute a panel
  hierarchy (`D_interactable_windows_only`).
- `SwingUtils` holds every capability probe — `supportsClick`, `supportsGetText`,
  `supportsSetValue`, `isEffectivelyEnabled`, `sanitizeForQuotedSlot`. The snapshot and the tools
  call the same probe, so what is advertised and what is accepted cannot diverge.
- `Parameters` wraps the raw argument map; no tool reads the map directly
  (`D_coerce_string_numbers`).

## Flows

**A tool call** (on an HTTP-server thread):

1. The wrapper registered by `SwingMCP.registerTool` acquires `toolLock` and holds it for the
   whole call, dispatch included (`D_tool_call_wide_lock`).
2. It calls `runInEDT`, which posts with `invokeLater` plus a `CountDownLatch` and a 10-second
   timeout; a timeout throws with the EDT's own stack trace, so a hung UI names itself.
3. On the EDT: resolve the considered components, build `Parameters`, call
   `AbstractSwingTool.execute`.
4. A read tool does its whole job here and returns a result. A mutation validates here — ref,
   capability, enabled — then posts the action with `invokeLater` and returns null
   (`D_fire_and_forget_dispatch`).
5. If the tool was a mutation and `execute` returned normally, the ref map is cleared. A
   validation failure clears nothing: the UI did not change, so the model can retry without
   paying for another snapshot.
6. The wrapper composes the echo (`D_dispatched_echo`), releases the lock, and the response goes
   out — still before the action has run.

**The snapshot pipeline**, four passes over the considered components:

1. **Build** — walk accessible children into `SnapshotNode`s. Hard exclusions drop a subtree
   outright; `JDesktopIcon` children are never constructed at all (`D_desktop_icon_as_itself`).
2. **Prune** — transparent containers (`JRootPane`, `JLayeredPane`, `JViewport`, unnamed
   `JPanel`) promote their children into the parent; semantic roles are always kept.
3. **Assign refs** — every node with at least one action takes the next integer, starting at 1.
   Children of an iconified frame are skipped entirely (`D_iconified_children_hidden`).
4. **Render** — one line per node, `JClass (role) "name" [states] text="…" actions: …`
   (`D_role_in_snapshot_only`, `D_inline_value_preview`). Every quoted slot is sanitized exactly
   once on the way out (`D_quoted_slot_sanitizing`); a modal root gets its header line first
   (`D_modal_stack_header`).

**The ref lifecycle**, which spans every tool:

1. `swing_snapshot` replaces the ref map wholesale and hands out fresh numbers.
2. Refs stay valid until the next *successful* mutation. `swing_get_cells` also replaces the map
   — it is a read, but it renumbers within its own output window, so refs from before it are gone.
3. After a successful mutation the map is cleared, and a stale ref returns an `isError` naming
   `swing_snapshot` as the recovery.
4. The expected loop is snapshot → interact → snapshot. Calls must be sequential: `toolLock`
   keeps the server consistent under parallel calls, but the second of two parallel mutations
   always meets a cleared map. The server's `INSTRUCTIONS`, sent once at `initialize`, tell the
   client this.

**Click detection**, two tiers, because not every clickable thing says so:

1. Match the `AccessibleAction` description — against `AccessibleAction.CLICK` for AWT, and
   against the `UIManager` lookup for Swing, which is localized (`R_accessible_action_impls`).
2. Failing that, look for an application-installed `MouseListener` on the underlying
   `Component`, skipping listeners from `javax.swing.*`, `java.awt.*`, `sun.*` and `com.sun.*`,
   and skipping components whose role is already interactive — a `MouseListener` on a `JButton`
   is look-and-feel plumbing, not application behaviour.
3. Either way the result is a `Runnable` that performs the click, so no caller branches on which
   tier matched. Tier 2 synthesizes a press/release/click event sequence through
   `dispatchEvent`, which goes through the real AWT pipeline including any `processMouseEvent`
   override.
4. Known gap: a component that overrides `processMouseEvent` without registering a listener is
   undetectable, though the synthesized events would reach it if it were.

## Testing

Two source sets, because `javax.accessibility` works headless and `Window` does not.

- `src/test` — headless, the default `test` task. `java.awt.headless=true`. `runInEDT` would
  hang waiting for an EDT that does not exist, so tests substitute a `FakeSwingMCP` that runs the
  block on the calling thread, and override `getConsideredComponents()` to return their own
  panel hierarchy.
- `src/testSwing` — `java.awt.headless=false`, needs a real display (Xvfb in CI), in the
  `verification` group and part of `check`. Anything that must instantiate a `JFrame`, `JDialog`
  or a visible `JDesktopPane` lives here.

Every tool test walks the **component matrix**, and a correct refusal counts as a pass:

- *Inputs* — `JButton`, `JTextField`, `JPasswordField`, `JTextArea`, `JCheckBox`, `JRadioButton`
  in a `ButtonGroup`, `JComboBox`, `JToggleButton`, `JSpinner`, `JSlider`
- *Containers* — `JPanel`, `JScrollPane`, `JTabbedPane`, `JSplitPane`, `JDesktopPane`
- *Display* — `JLabel`, `JProgressBar`
- *Menus* — `JMenuBar`, `JMenu`, `JMenuItem`; a tool that could apply to a menu title must assert
  the `D_jmenu_not_clickable` refusal
- *Other* — `JToolBar`, `JList`, `JTree`; *internal windows* — `JInternalFrame` inside a
  `JDesktopPane`
- *Top-level* — `JFrame`, `JDialog`, `JOptionPane`, in `testSwing` only. Build a `JOptionPane`
  into a `JDialog`'s content pane; never call `showXxxDialog()`, which blocks the calling thread.

A component with no ref is tested by registering it under a known id and calling the tool with
that id. Asserting that `getRefOf` throws tests the harness, not the tool.

## Where to start reading

`SwingMCP.registerTool` — the wrapper is twenty lines and every invariant in `AGENTS.md` passes
through it: the lock, the EDT hop, the ref-map clear, the echo. Then `SwingUtils`, which is where
every question about what a component can do gets answered.

## What is deliberately absent

- **No batch form-filling** (`D_no_batch_fill_form`).
- **No cell-indexed access to `JTable`** (`D_no_jtable_cells`).
- **No refs on windows blocked by a modal** (`D_interactable_windows_only`).
- **No `get_text` on password or label roles** (`D_password_not_readable`,
  `D_label_not_readable`).
- **No `click` on a `JMenu`** (`D_jmenu_not_clickable`).
- **No synchronous outcome from a mutation** (`D_fire_and_forget_dispatch`).
