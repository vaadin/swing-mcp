package com.vaadin.swingmcp.mcp;

import javax.accessibility.*;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.JWindow;
import javax.swing.WindowConstants;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * A collection of static utility methods for Swing-related operations.
 */
public final class SwingUtils {

    private SwingUtils() {
    }

    // ── Roles where AccessibleValue is not user-meaningful ──────────────────
    // AbstractButton subclasses (buttons, menus) expose AccessibleValue for
    // their selected/pressed state (0 = off, 1 = on), but this is already
    // communicated via CHECKED/SELECTED states in the snapshot. Both
    // get_value and set_value are suppressed.
    static final Set<AccessibleRole> SUPPRESSED_VALUE_ROLES = Set.of(
            AccessibleRole.PUSH_BUTTON,
            AccessibleRole.TOGGLE_BUTTON,
            AccessibleRole.CHECK_BOX,
            AccessibleRole.RADIO_BUTTON,
            AccessibleRole.MENU,
            AccessibleRole.MENU_ITEM,
            AccessibleRole.PAGE_TAB,
            // JInternalFrame and JDesktopIcon expose AccessibleValue for the
            // JLayeredPane Z-order layer — a programmatic concept, not a
            // user-controlled value. See D_desktop_icon_as_itself.
            AccessibleRole.INTERNAL_FRAME,
            AccessibleRole.DESKTOP_ICON
    );

    // ── Roles whose AccessibleValue is read-only ─────────────────────────────
    static final Set<AccessibleRole> READ_ONLY_VALUE_ROLES = Set.of(
            AccessibleRole.PROGRESS_BAR
    );

    // ── Interactive roles — Tier 2 MouseListener fallback is skipped for these ──
    // These components have well-defined accessibility contracts and should use
    // AccessibleAction for click detection. A MouseListener on a JButton is L&F
    // plumbing, not application click behaviour.
    static final Set<AccessibleRole> INTERACTIVE_ROLES = Set.of(
            AccessibleRole.PUSH_BUTTON,
            AccessibleRole.TOGGLE_BUTTON,
            AccessibleRole.CHECK_BOX,
            AccessibleRole.RADIO_BUTTON,
            AccessibleRole.TEXT,
            AccessibleRole.PASSWORD_TEXT,
            AccessibleRole.COMBO_BOX,
            AccessibleRole.LIST,
            AccessibleRole.TABLE,
            AccessibleRole.TREE,
            AccessibleRole.MENU_BAR,
            AccessibleRole.MENU,
            AccessibleRole.MENU_ITEM,
            AccessibleRole.POPUP_MENU,
            AccessibleRole.SLIDER,
            AccessibleRole.SPIN_BOX,
            AccessibleRole.PROGRESS_BAR,
            AccessibleRole.SCROLL_BAR,
            AccessibleRole.COLOR_CHOOSER,
            AccessibleRole.FILE_CHOOSER,
            AccessibleRole.DATE_EDITOR
    );

    // ── Roles where AccessibleSelection is internal or non-functional ────────
    // MENU_BAR / MENU: selection is internal keyboard navigation.
    // TREE: tree-level AccessibleSelection is non-functional
    //   (getAccessibleSelectionCount() always returns 0); selection lives on
    //   tree nodes, not the tree itself. See T-014 design notes.
    static final Set<AccessibleRole> SUPPRESSED_SELECTION_ROLES = Set.of(
            AccessibleRole.MENU_BAR,
            AccessibleRole.MENU,
            AccessibleRole.TREE
    );

    /**
     * Returns a {@link Runnable} that performs a click on the given accessible,
     * or {@code null} if the accessible does not support clicking.
     * <p>
     * Uses a two-tier approach:
     * <ul>
     *   <li><b>Tier 1 — AccessibleAction:</b> checks both
     *       {@link AccessibleAction#CLICK} (AWT literal) and
     *       {@link UIManager#getString(Object)} for {@code "AbstractButton.clickText"}
     *       (Swing UIManager). The returned Runnable calls
     *       {@code doAccessibleAction(i)}.</li>
     *   <li><b>Tier 2 — MouseListener fallback:</b> if no AccessibleAction click
     *       is found and the component's role is not in {@link #INTERACTIVE_ROLES},
     *       checks for application-installed {@link MouseListener}s (filtering out
     *       framework listeners by package prefix). The returned Runnable synthesizes
     *       a mouse click event sequence via {@link Component#dispatchEvent}.</li>
     * </ul>
     *
     * @return a Runnable that performs the click, or null if click is not supported
     * @see <a href="architecture.md">`design/architecture.md` § Click detection</a>
     */
    public static Runnable supportsClick(Accessible a) {
        // --- D_jmenu_not_clickable: JMenu is a structural container, not a click target ---
        // The JMenu's JMenuItem children are already directly clickable via
        // their own refs; exposing click on the menu title would only add
        // duplication (open popup surfaces the same items a second time),
        // round-trips (click menu → snapshot → click item), and a broken
        // toggle (doClick opens on the first call but not on subsequent ones).
        // See design/decisions.md, D_jmenu_not_clickable.
        if (a instanceof JMenu) {
            return null;
        }
        // --- Tier 1: AccessibleAction ---
        AccessibleContext ac = a.getAccessibleContext();
        if (ac != null) {
            AccessibleAction aa = ac.getAccessibleAction();
            if (aa != null) {
                String clickText = UIManager.getString("AbstractButton.clickText");
                for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
                    String desc = aa.getAccessibleActionDescription(i);
                    if (AccessibleAction.CLICK.equals(desc)
                            || (clickText != null && clickText.equals(desc))) {
                        final int idx = i;
                        return () -> aa.doAccessibleAction(idx);
                    }
                }
            }
        }
        // --- Tier 2: MouseListener fallback ---
        // Skip for interactive roles — these should use AccessibleAction (Tier 1).
        AccessibleRole role = (ac != null) ? ac.getAccessibleRole() : null;
        if (role != null && INTERACTIVE_ROLES.contains(role)) return null;

        if (a instanceof Component) {
            Component c = (Component) a;
            for (MouseListener ml : c.getMouseListeners()) {
                String cls = ml.getClass().getName();
                if (!cls.startsWith("javax.swing.")
                        && !cls.startsWith("java.awt.")
                        && !cls.startsWith("sun.")
                        && !cls.startsWith("com.sun.")) {
                    return () -> {
                        int x = c.getWidth() / 2;
                        int y = c.getHeight() / 2;
                        long now = System.currentTimeMillis();
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED,
                                now, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED,
                                now + 1, 0, x, y, 1, false, MouseEvent.BUTTON1));
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_CLICKED,
                                now + 2, 0, x, y, 1, false, MouseEvent.BUTTON1));
                    };
                }
            }
        }
        return null;
    }

    /**
     * Returns the action index for the toggle-popup action on the given
     * accessible, or {@code -1} if the accessible does not support it.
     * <p>
     * Checks both {@link AccessibleAction#TOGGLE_POPUP} (AWT literal) and
     * {@link UIManager#getString(Object)} for {@code "ComboBox.togglePopupText"}
     * (Swing UIManager) to handle locale-safe matching.
     */
    public static int supportsTogglePopup(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return -1;
        AccessibleAction aa = ac.getAccessibleAction();
        if (aa == null) return -1;
        String togglePopupText = UIManager.getString("ComboBox.togglePopupText");
        for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
            String desc = aa.getAccessibleActionDescription(i);
            if (AccessibleAction.TOGGLE_POPUP.equals(desc)
                    || (togglePopupText != null && togglePopupText.equals(desc))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns {@code true} iff reading the accessible's text content via
     * {@code swing_get_text} yields <em>meaningful</em> content.
     *
     * <p>This is not merely a structural "exposes {@link AccessibleText}" check.
     * Two role-based exclusions apply:
     * <ul>
     *   <li>{@link AccessibleRole#PASSWORD_TEXT} (D_password_not_readable) — the JDK returns
     *       echo characters rather than the real password. Returning echo
     *       chars to the AI is misleading and leaks password length.</li>
     *   <li>{@link AccessibleRole#LABEL} (D_label_not_readable) — HTML-backed {@code JLabel}s
     *       accidentally expose {@link AccessibleText} via the JDK's HTML
     *       rendering plumbing, while plain JLabels do not. Excluding the
     *       role uniformly gives every LABEL-role accessible (JLabel, JList
     *       cell, JTree node, and custom LABEL-role components) the same
     *       surface: no {@code get_text}, read the content from the snapshot
     *       name slot. See D_label_not_readable for the full rationale.</li>
     * </ul>
     * Other components whose accessibility-API read yields garbage
     * (e.g. filter combo boxes that clear themselves on apply) may be added
     * here in the future.
     *
     * <p>Decoupled from {@link #supportsSetText}: a component may support
     * {@code set_text} without {@code supportsGetText} returning {@code true}
     * (JPasswordField is the canonical example). Callers that need the raw
     * structural fact (exposes {@code AccessibleText}) should inspect
     * {@code AccessibleContext.getAccessibleText()} directly; this helper
     * answers the domain question "can I show the AI real content?".
     */
    public static boolean supportsGetText(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        AccessibleRole role = ac.getAccessibleRole();
        // D_password_not_readable: password-role accessibles return echo chars, not real content.
        if (AccessibleRole.PASSWORD_TEXT.equals(role)) return false;
        // D_label_not_readable: LABEL-role content is redundant with the snapshot name slot;
        // excluding here uniformises JLabel behaviour across plain and HTML forms.
        if (AccessibleRole.LABEL.equals(role)) return false;
        return ac.getAccessibleText() != null;
    }

    /**
     * Returns {@code true} if the accessible's role is
     * {@link AccessibleRole#PASSWORD_TEXT} — i.e. {@link javax.swing.JPasswordField}
     * or any component that adopts the password role. Per D_password_not_readable, password-role
     * accessibles must not advertise {@code get_text} and {@code swing_get_text}
     * must refuse to read them. Used directly by {@code SwingGetTextTool} to
     * emit the dedicated error message distinct from the generic "does not
     * support get_text".
     */
    public static boolean hasPasswordRole(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        return AccessibleRole.PASSWORD_TEXT.equals(ac.getAccessibleRole());
    }

    /**
     * Returns {@code true} if the accessible exposes {@link AccessibleEditableText}
     * <em>and</em> is currently editable (has the {@link AccessibleState#EDITABLE} state).
     * A text component with {@code setEditable(false)} returns {@code false} here.
     */
    public static boolean supportsSetText(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        return ac.getAccessibleEditableText() != null
                && ac.getAccessibleStateSet() != null
                && ac.getAccessibleStateSet().contains(AccessibleState.EDITABLE);
    }

    /**
     * Returns {@code true} if the accessible exposes {@link AccessibleEditableText},
     * regardless of the current editable state. Use this to detect text components
     * that are structurally capable of text editing (e.g. for emitting {@code read_only}).
     */
    public static boolean hasEditableText(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        return ac.getAccessibleEditableText() != null;
    }

    /**
     * Returns {@code true} if the accessible exposes a user-meaningful
     * {@link AccessibleValue} that can be read.
     * <p>
     * Roles whose AccessibleValue is not user-meaningful (e.g. buttons
     * exposing selected/pressed state) are suppressed.
     */
    public static boolean supportsGetValue(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        AccessibleValue av = ac.getAccessibleValue();
        if (av == null) return false;
        if (SUPPRESSED_VALUE_ROLES.contains(ac.getAccessibleRole())) return false;
        // Some components (e.g. JSpinner with SpinnerDateModel) expose a non-null
        // AccessibleValue but return null from getCurrentAccessibleValue().
        return av.getCurrentAccessibleValue() != null;
    }

    /**
     * Returns {@code true} if the accessible exposes a user-meaningful
     * {@link AccessibleValue} that can be both read and written.
     * <p>
     * In addition to the suppression rules of {@link #supportsGetValue},
     * read-only value roles (e.g. progress bars) are excluded.
     */
    public static boolean supportsSetValue(Accessible a) {
        if (!supportsGetValue(a)) return false;
        AccessibleRole role = a.getAccessibleContext().getAccessibleRole();
        return !READ_ONLY_VALUE_ROLES.contains(role);
    }

    /**
     * Returns {@code true} if the accessible exposes user-facing
     * {@link AccessibleSelection}.
     * <p>
     * Roles whose AccessibleSelection is internal or non-functional
     * (menu bars, menus, trees) are suppressed. For {@code JTable},
     * only row-selection mode is supported (BR-10): the table must have
     * {@code rowSelectionAllowed == true} and
     * {@code columnSelectionAllowed == false}.
     *
     * @see <a href="tool-014-swing-get-selection.md">T-014 BR-10</a>
     */
    public static boolean supportsSelection(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        if (ac.getAccessibleSelection() == null) return false;
        if (SUPPRESSED_SELECTION_ROLES.contains(ac.getAccessibleRole())) return false;
        // BR-10: JTable row-selection gate
        if (a instanceof JTable) {
            JTable table = (JTable) a;
            return table.getRowSelectionAllowed() && !table.getColumnSelectionAllowed();
        }
        return true;
    }

    /**
     * Returns {@code true} if the accessible is a valid target for the read-only
     * selection-item tools {@code swing_get_items} and
     * {@code swing_get_item_count}.
     *
     * <p>Accepts {@code JList}, {@code JComboBox}, and any {@code JTable} only.
     * Two deliberate deviations from {@link #supportsSelection}:</p>
     * <ul>
     *   <li><b>{@code JTable} is accepted in any selection mode</b> (row /
     *       column / cell / no-selection). Rationale: row enumeration is a
     *       read-only observation that does not require a working selection
     *       model, and after T-020's JTable ban on {@code swing_get_cells}
     *       these two tools are the only paged content-access path for
     *       JTables in non-row-selection modes. The write-path selection
     *       tools ({@code swing_set_selection}, {@code swing_clear_selection},
     *       {@code swing_select_all}) keep the strict
     *       {@link #supportsSelection} gate — they genuinely need a working
     *       row selection model.</li>
     *   <li><b>{@code JTabbedPane} is rejected</b> (dropped per P-001 Wave A).
     *       Tabs are UI structure, not data, and are already rendered in the
     *       snapshot with their 0-based index and {@code [selected]} /
     *       {@code [disabled]} state (T-002 SC-2). The AI passes the inline
     *       index straight to {@code swing_set_selection}.</li>
     * </ul>
     *
     * @see <a href="tool-017-swing-get-items.md">T-017 BR-03</a>
     * @see <a href="tool-018-swing-get-item-count.md">T-018 BR-03</a>
     */
    public static boolean supportsGetItems(Accessible a) {
        if (a instanceof JTabbedPane) {
            return false;
        }
        if (a instanceof JTable) {
            AccessibleContext ac = a.getAccessibleContext();
            return ac != null;
        }
        return supportsSelection(a);
    }

    /**
     * Returns {@code true} if the accessible supports multi-selection.
     * <p>
     * Detection: {@link AccessibleState#MULTISELECTABLE} in the state set,
     * OR ({@code instanceof JTable} with
     * {@code getSelectionModel().getSelectionMode() != SINGLE_SELECTION}).
     * The JTable fallback is needed because JTable does not report
     * {@code MULTISELECTABLE} in its {@code AccessibleStateSet} even in
     * multi-selection mode (verified by probe test, 2026-04-07).
     * <p>
     * Note: this method does <em>not</em> check {@link #supportsSelection}.
     * Use {@link #supportsMultiSelection} for a combined check.
     *
     * @see <a href="architecture.md">R_selection_index_spaces — Selection Action Groups</a>
     */
    public static boolean isMultiSelectable(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        if (ac.getAccessibleStateSet().contains(AccessibleState.MULTISELECTABLE)) {
            return true;
        }
        // JTable fallback: JTable does not report MULTISELECTABLE in its state set
        if (a instanceof JTable) {
            JTable table = (JTable) a;
            return table.getSelectionModel().getSelectionMode() != ListSelectionModel.SINGLE_SELECTION;
        }
        return false;
    }

    /**
     * Returns {@code true} if the accessible supports user-facing selection
     * and is in single-selection mode.
     *
     * @see #supportsSelection
     * @see #isMultiSelectable
     */
    public static boolean supportsSingleSelection(Accessible a) {
        return supportsSelection(a) && !isMultiSelectable(a);
    }

    /**
     * Returns {@code true} if the accessible supports user-facing selection
     * and is in multi-selection mode.
     *
     * @see #supportsSelection
     * @see #isMultiSelectable
     */
    public static boolean supportsMultiSelection(Accessible a) {
        return supportsSelection(a) && isMultiSelectable(a);
    }

    /**
     * Returns the action index for the increment action on the given accessible,
     * or {@code -1} if the accessible does not support increment.
     * <p>
     * Matches by {@link AccessibleAction#INCREMENT} static constant directly.
     * {@code JSlider} and {@code JSpinner} use the static field without any
     * UIManager indirection, so no locale-specific fallback is needed.
     *
     * @see <a href="tool-008-swing-increment.md">T-008 BR-03</a>
     */
    public static int supportsIncrement(Accessible a) {
        return supportsAction(a, AccessibleAction.INCREMENT);
    }

    /**
     * Returns the action index for the decrement action on the given accessible,
     * or {@code -1} if the accessible does not support decrement.
     * <p>
     * Matches by {@link AccessibleAction#DECREMENT} static constant directly.
     * {@code JSlider} and {@code JSpinner} use the static field without any
     * UIManager indirection, so no locale-specific fallback is needed.
     *
     * @see <a href="tool-009-swing-decrement.md">T-009 BR-03</a>
     */
    public static int supportsDecrement(Accessible a) {
        return supportsAction(a, AccessibleAction.DECREMENT);
    }

    /**
     * Returns the action index for the toggle-expand action on the given accessible,
     * or {@code -1} if the accessible does not support it.
     * <p>
     * Matches by {@link AccessibleAction#TOGGLE_EXPAND} static constant directly.
     * The standard {@code JTree} implementation uses the static field without any
     * UIManager indirection, so no locale-specific fallback is needed.
     *
     * @see <a href="tool-010-swing-toggle-expand.md">T-010 BR-03</a>
     */
    public static int supportsToggleExpand(Accessible a) {
        return supportsAction(a, AccessibleAction.TOGGLE_EXPAND);
    }

    private static int supportsAction(Accessible a, String actionName) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return -1;
        AccessibleAction aa = ac.getAccessibleAction();
        if (aa == null) return -1;
        for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
            if (actionName.equals(aa.getAccessibleActionDescription(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns {@code true} if the accessible supports the synthetic {@code close} action.
     * <p>
     * Close is available for top-level windows (JFrame, JDialog) that are showing,
     * have OS decorations, and will not terminate the JVM on close; for
     * {@link JInternalFrame}s that are showing, closable, and do not have
     * {@code EXIT_ON_CLOSE}; and for {@link JInternalFrame.JDesktopIcon}s
     * (iconified internal frames) where the icon is showing and the underlying
     * frame passes the JInternalFrame rules.
     *
     * @see <a href="architecture.md">`SwingUtils.supportsClose`</a>
     */
    public static boolean supportsClose(Accessible a) {
        if (a instanceof Window) {
            Window window = (Window) a;
            if (!window.isShowing()) return false;
            if (window instanceof Frame && ((Frame) window).isUndecorated()) return false;
            if (window instanceof Dialog && ((Dialog) window).isUndecorated()) return false;
            if (window instanceof JFrame &&
                    ((JFrame) window).getDefaultCloseOperation() == WindowConstants.EXIT_ON_CLOSE) return false;
            return true;
        }
        if (a instanceof JInternalFrame) {
            JInternalFrame iframe = (JInternalFrame) a;
            if (!iframe.isShowing()) return false;
            if (!iframe.isClosable()) return false;
            if (iframe.getDefaultCloseOperation() == WindowConstants.EXIT_ON_CLOSE) return false;
            return true;
        }
        // BR-11: JDesktopIcon — isShowing() on the icon, frame rules on the underlying frame
        if (a instanceof JInternalFrame.JDesktopIcon) {
            JInternalFrame.JDesktopIcon icon = (JInternalFrame.JDesktopIcon) a;
            if (!icon.isShowing()) return false;
            JInternalFrame frame = icon.getInternalFrame();
            if (frame == null) return false;
            if (!frame.isClosable()) return false;
            if (frame.getDefaultCloseOperation() == WindowConstants.EXIT_ON_CLOSE) return false;
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if the accessible supports the synthetic {@code iconify} action.
     * <p>
     * Iconify is available for {@link Frame}s (including {@link JFrame}) that are showing,
     * decorated, and not already iconified; and for {@link JInternalFrame}s that are showing,
     * iconifiable, and not already iconified.
     *
     * @see <a href="tool-022-swing-iconify.md">T-022</a>
     */
    public static boolean supportsIconify(Accessible a) {
        if (a instanceof Frame) {
            Frame frame = (Frame) a;
            if (!frame.isShowing()) return false;
            if (frame.isUndecorated()) return false;
            if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) return false;
            return true;
        }
        if (a instanceof JInternalFrame) {
            JInternalFrame iframe = (JInternalFrame) a;
            if (!iframe.isShowing()) return false;
            if (!iframe.isIconifiable()) return false;
            if (iframe.isIcon()) return false;
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if the accessible is a window in an iconified (minimized) state
     * and is currently showing. A hidden window is never considered iconified.
     * JDesktopIcon is a component, not a window — it is not considered iconified.
     * <ul>
     *   <li><b>Frame (including JFrame):</b> {@code isShowing() && (getExtendedState() & Frame.ICONIFIED) != 0}</li>
     *   <li><b>JInternalFrame:</b> {@code isShowing() && isIcon()}</li>
     *   <li><b>All other types (including JDesktopIcon):</b> {@code false}</li>
     * </ul>
     */
    public static boolean isIconified(Accessible a) {
        if (a instanceof Frame) {
            Frame frame = (Frame) a;
            return frame.isShowing()
                    && (frame.getExtendedState() & Frame.ICONIFIED) != 0;
        }
        if (a instanceof JInternalFrame) {
            JInternalFrame iframe = (JInternalFrame) a;
            return iframe.isShowing() && iframe.isIcon();
        }
        return false;
    }

    /**
     * Returns {@code true} if the accessible supports the synthetic {@code restore} action.
     * <p>
     * Restore (de-iconify) is available for iconified windows ({@link #isIconified(Accessible)})
     * and for showing {@link JInternalFrame.JDesktopIcon}s (the visible representation of an
     * iconified JInternalFrame — a component, not a window, so not covered by {@code isIconified}).
     *
     * @see <a href="tool-023-swing-restore.md">T-023</a>
     */
    public static boolean supportsRestore(Accessible a) {
        if (a instanceof JInternalFrame.JDesktopIcon) {
            return ((JInternalFrame.JDesktopIcon) a).isShowing();
        }
        return isIconified(a);
    }

    /**
     * Returns the effective accessible name for the given accessible, or
     * {@code null} if none is available.
     * <p>
     * For most components this delegates to
     * {@code getAccessibleContext().getAccessibleName()}. For
     * {@link JInternalFrame.JDesktopIcon}, a three-step fallback is used
     * (matching the BR-10 "respect explicit, fall back to derived" pattern):
     * <ol>
     *   <li>The icon's own {@code getAccessibleName()} (if someone set it
     *       explicitly — in practice always {@code null}).</li>
     *   <li>The underlying frame's {@code getAccessibleName()} (may differ
     *       from the title if explicitly overridden).</li>
     *   <li>The underlying frame's {@code getTitle()} (last resort, always
     *       available).</li>
     * </ol>
     *
     * @see <a href="tool-002-swing-snapshot.md">T-002 SC-5</a>
     */
    public static String getEffectiveAccessibleName(Accessible a) {
        if (a instanceof JInternalFrame.JDesktopIcon) {
            JInternalFrame.JDesktopIcon icon = (JInternalFrame.JDesktopIcon) a;

            // Step 1: icon's own accessible name
            AccessibleContext iconCtx = icon.getAccessibleContext();
            if (iconCtx != null) {
                String name = iconCtx.getAccessibleName();
                if (name != null && !name.isEmpty()) return name;
            }

            // Steps 2–3: recurse into the underlying frame
            JInternalFrame frame = icon.getInternalFrame();
            return frame != null ? getEffectiveAccessibleName(frame) : null;
        }

        if (a instanceof JInternalFrame) {
            JInternalFrame frame = (JInternalFrame) a;
            AccessibleContext ctx = frame.getAccessibleContext();
            if (ctx != null) {
                String name = ctx.getAccessibleName();
                if (name != null && !name.isEmpty()) return name;
            }
            return frame.getTitle();
        }

        // Default path: standard accessible name
        AccessibleContext ctx = a.getAccessibleContext();
        return ctx != null ? ctx.getAccessibleName() : null;
    }

    /**
     * Returns the tooltip text associated with the given accessible as plain
     * text, or {@code null} if none is available.
     * <p>
     * Two sources are consulted:
     * <ol>
     *   <li><b>JTabbedPane per-tab tooltips</b> — when {@code a} is a tab
     *       (role {@link AccessibleRole#PAGE_TAB}) whose accessible parent is
     *       a {@link JTabbedPane}, the tooltip stored via
     *       {@link JTabbedPane#setToolTipTextAt(int, String)} is returned.
     *       These per-tab tooltips are not reachable via
     *       {@link JComponent#getToolTipText()} — they live in a separate
     *       per-index map on the tabbed pane.</li>
     *   <li><b>JComponent component-level tooltip</b> — for any other
     *       {@link JComponent}, the value of
     *       {@link JComponent#getToolTipText()} is returned.</li>
     * </ol>
     * <p>
     * If the resolved tooltip starts with {@code <html>} (case-insensitive,
     * Swing's HTML rendering trigger), the result is converted to plain text:
     * each tag is replaced with a single space, the four standard entities
     * ({@code &amp;}, {@code &lt;}, {@code &gt;}, {@code &nbsp;}) are decoded,
     * runs of whitespace are collapsed, and the string is trimmed. Tooltips
     * without the {@code <html>} prefix are returned verbatim — Swing only
     * renders strings starting with {@code <html>} as HTML, so a literal
     * tooltip such as {@code "List<String>"} must be preserved unchanged.
     * <p>
     * Blank results (empty or whitespace-only, including HTML that strips
     * down to nothing) are normalized to {@code null} so callers can use a
     * single null check.
     * <p>
     * The result is not truncated — callers that need a length cap apply it
     * themselves.
     * <p>
     * Per-cell, per-row, per-node and per-item tooltips on
     * {@link JTable}, {@link javax.swing.JList}, {@link javax.swing.JTree}
     * and {@link JTableHeader} are <strong>not</strong> exposed: those are
     * computed on the fly by the cell renderer in response to a
     * {@link MouseEvent}, and there is no MouseEvent available here.
     */
    public static String getTooltipAsText(Accessible a) {
        if (a == null) return null;
        AccessibleContext ctx = a.getAccessibleContext();

        String raw = null;

        // JTabbedPane per-tab tooltip. The tab itself is exposed as an
        // Accessible child (the package-private JTabbedPane$Page) with role
        // PAGE_TAB; its tooltip is stored by index on the parent JTabbedPane.
        // We gate on the role so a tab-content JComponent (whose accessible
        // parent is also the JTabbedPane) is NOT misidentified as a tab.
        if (ctx != null && ctx.getAccessibleRole() == AccessibleRole.PAGE_TAB) {
            Accessible parent = ctx.getAccessibleParent();
            if (parent instanceof JTabbedPane) {
                int index = ctx.getAccessibleIndexInParent();
                if (index >= 0) {
                    raw = ((JTabbedPane) parent).getToolTipTextAt(index);
                }
            }
        } else if (a instanceof JComponent) {
            raw = ((JComponent) a).getToolTipText();
        }

        return htmlToPlainText(raw);
    }

    /**
     * Converts a Swing-style HTML string to plain text, or returns the
     * argument verbatim if it is not HTML. Blank results are normalized to
     * {@code null}.
     * <p>
     * Swing only renders a string as HTML when it starts with {@code <html>}
     * (case-insensitive — this is the
     * {@link javax.swing.plaf.basic.BasicHTML#isHTMLString} check). For any
     * other string, the input is returned verbatim — a literal value such as
     * {@code "List<String>"} must not have its angle brackets stripped.
     * <p>
     * For HTML strings, every tag is replaced by a single space (so
     * {@code "Save<br>file"} becomes {@code "Save file"} rather than
     * {@code "Savefile"}), the four standard entities — {@code &amp;},
     * {@code &lt;}, {@code &gt;}, {@code &nbsp;} — are decoded, runs of
     * whitespace are collapsed, and the result is trimmed. {@code &amp;} is
     * decoded last so source text like {@code "&amp;lt;"} round-trips to the
     * literal {@code "&lt;"} rather than being double-decoded to {@code "<"}.
     * Numeric and exotic entities are left as-is.
     * <p>
     * If the input is {@code null}, or the cleanup produces an empty or
     * whitespace-only result, returns {@code null} so callers need only a
     * single null check.
     */
    public static String htmlToPlainText(String raw) {
        if (raw == null) return null;

        String text;
        if (raw.regionMatches(true, 0, "<html>", 0, 6)) {
            text = raw.replaceAll("<[^>]*>", " ");
            text = text.replace("&nbsp;", " ")
                       .replace("&lt;", "<")
                       .replace("&gt;", ">")
                       .replace("&amp;", "&");
            text = text.replaceAll("\\s+", " ").trim();
        } else {
            text = raw;
        }

        return text.isBlank() ? null : text;
    }

    /**
     * Sanitises a string for emission inside a double-quoted snapshot slot —
     * the {@code "name"} slot, the {@code "description"} slot, and the
     * {@code text="..."} inline preview (BR-13 / D_quoted_slot_sanitizing).
     *
     * <p>The helper:
     * <ol>
     *   <li>Replaces any run of whitespace characters with a single ASCII
     *       space. Covers Java's {@code \s} (ASCII: {@code \n}, {@code \r},
     *       {@code \t}, vertical tab, form feed) plus the Unicode line
     *       separators U+0085 (NEL), U+2028 (LINE SEPARATOR), and U+2029
     *       (PARAGRAPH SEPARATOR). The Unicode separators are included
     *       explicitly because Java's {@code \s} is ASCII-only by default —
     *       and the snapshot's one-line-per-node invariant must not
     *       survive <em>any</em> line-break character.</li>
     *   <li>Escapes embedded {@code "} as {@code \"}.</li>
     *   <li>Strips leading/trailing whitespace.</li>
     *   <li>Returns {@code null} if the result is empty or blank, so callers
     *       need only their existing null-check.</li>
     * </ol>
     *
     * <p>Backslashes are <strong>not</strong> escaped — a literal {@code \}
     * passes through unchanged. See D_quoted_slot_sanitizing "Alternatives considered" for
     * why full JSON-style escaping was rejected. Consequence: running the
     * sanitiser twice is <em>not</em> a no-op — the second pass would
     * double-escape quotes (so {@code say "hi"} → {@code say \"hi\"} →
     * {@code say \\"hi\\"}). The snapshot render path calls the sanitiser
     * exactly once per slot, so double-escape is avoided by call-site
     * discipline rather than by the helper.
     *
     * <p>{@code null} input returns {@code null}.
     *
     * <p><strong>Why sanitise.</strong> The snapshot's one-line-per-node
     * invariant depends on quoted slots not containing raw newlines; a
     * {@code JLabel("Line 1\nLine 2")} would otherwise render as two lines
     * and corrupt the indent-based tree structure. Embedded {@code "} would
     * similarly terminate a quoted slot visually.
     */
    public static String sanitizeForQuotedSlot(String raw) {
        if (raw == null) return null;
        // Collapse ASCII whitespace + explicit Unicode line separators that
        // Java's default \s does not match (U+0085 / U+2028 / U+2029).
        String collapsed = raw.replaceAll("[\\s\\u0085\\u2028\\u2029]+", " ").strip();
        if (collapsed.isEmpty()) return null;
        return collapsed.replace("\"", "\\\"");
    }

    /**
     * Resolves the description of a component using the same logic as the
     * snapshot description slot (T-002 BR-10): tries
     * {@code accessibleDescription} first (with HTML cleanup), then falls
     * back to tooltip text. The result is sanitised via
     * {@link #sanitizeForQuotedSlot} but <strong>not</strong> capped at 120
     * characters — callers that need the cap should apply it separately.
     *
     * @return the resolved, sanitised description, or {@code null} if the
     *         component has no description from either source.
     */
    public static String resolveDescription(Accessible a) {
        if (a == null) return null;
        AccessibleContext ctx = a.getAccessibleContext();
        String desc = ctx != null
                ? htmlToPlainText(ctx.getAccessibleDescription())
                : null;
        if (desc == null) {
            desc = getTooltipAsText(a);
        }
        return sanitizeForQuotedSlot(desc);
    }

    /**
     * Returns whether the given accessible is effectively enabled — i.e.
     * whether the user (or AI client) may actually interact with it in the
     * running Swing app.
     * <p>
     * Swing's {@link Component#setEnabled(boolean) setEnabled(false)}
     * <em>does not propagate to children</em> — this is the documented,
     * by-design behaviour (see the Component.setEnabled javadoc and
     * <a href="https://bugs.openjdk.org/browse/JDK-4177727">JDK-4177727</a>,
     * closed as won't-fix). A button inside a disabled {@code JPanel},
     * {@code JScrollPane}, {@code JToolBar} or even a disabled
     * {@code JTabbedPane} tab is still mechanically clickable. We therefore
     * do <strong>not</strong> walk the parent chain for real
     * {@link Component} accessibles — we trust each component's own
     * {@link AccessibleState#ENABLED} state.
     * <p>
     * Two Swing quirks need explicit handling:
     * <ul>
     *   <li><b>{@link JTabbedPane} tabs disabled via
     *       {@link JTabbedPane#setEnabledAt(int, boolean)}</b> — the
     *       {@code AccessiblePage} virtual child does NOT omit
     *       {@code ENABLED} from its state set even though the tab is
     *       disabled. We consult {@link JTabbedPane#isEnabledAt(int)}
     *       directly when the parent is a {@code JTabbedPane}.</li>
     *   <li><b>Virtual accessible children (not {@link Component}
     *       instances)</b> — some virtual children (notably
     *       {@code JTable} cells) keep {@code ENABLED} in their state set
     *       even when the host component is disabled. For any accessible
     *       that is not itself a {@code Component}, we recurse into its
     *       accessible parent until a {@code Component} ancestor is found.
     *       ({@code JList} items happen to do this correctly already, but
     *       the recursion costs nothing and is uniformly safe.)</li>
     * </ul>
     * <p>
     * The disabled-{@code Window} case (an {@code OS}-level peer dropping
     * input on a {@code Frame.setEnabled(false)}) is intentionally
     * <strong>not</strong> handled here — its visual behaviour is
     * platform/L&amp;F-dependent and unreliable across OSes.
     *
     * @see <a href="architecture.md">D_mirror_swing_semantics</a>
     */
    public static boolean isEffectivelyEnabled(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        if (!ac.getAccessibleStateSet().contains(AccessibleState.ENABLED)) return false;

        Accessible parent = ac.getAccessibleParent();

        // Quirk 1: JTabbedPane.setEnabledAt is not reflected in the AccessiblePage state set.
        if (parent instanceof JTabbedPane) {
            JTabbedPane tp = (JTabbedPane) parent;
            int idx = ac.getAccessibleIndexInParent();
            if (idx >= 0 && idx < tp.getTabCount() && !tp.isEnabledAt(idx)) {
                return false;
            }
        }

        // Quirk 2: virtual children (e.g. JTable cells) may not reflect the host's disabled
        // state — walk up until we reach a real Component ancestor.
        if (!(a instanceof Component) && parent != null) {
            return isEffectivelyEnabled(parent);
        }

        // Real Components: trust the component's own ENABLED state. Swing's setEnabled
        // does not propagate to children (source: Component.setEnabled() javadoc), so neither do we.
        // Also: JDK-4177727 closed as won't-fix.
        return true;
    }

    /**
     * Returns {@code true} if the given accessible is effectively visible:
     * it must be a {@link Component} that is {@linkplain Component#isVisible() visible}
     * and has both width &gt; 0 and height &gt; 0.
     * Non-{@code Component} accessibles are considered visible.
     *
     * @param accessible the accessible to check
     * @return {@code true} if effectively visible
     */
    public static boolean isVisible(Accessible accessible) {
        if (accessible instanceof Component) {
            Component c = (Component) accessible;
            if (!c.isVisible()) return false;
            // A showing component with zero width or height is effectively invisible.
            // We only check size when isShowing() is true, because unrealized
            // components (e.g. in unit tests) legitimately have zero size.
            if (c.isShowing() && (c.getWidth() <= 0 || c.getHeight() <= 0)) return false;
        }
        return true;
    }

    /**
     * Returns a human-readable class name for the given component, suitable
     * for inclusion in error messages and log lines.
     *
     * <p>{@link Class#getSimpleName()} returns the empty string for anonymous
     * classes (e.g. {@code new JButton() { ... }}) — emitting that in an
     * error message yields {@code "No ref assigned to ."}, which is what
     * triggered this helper. For anonymous classes the fully-qualified name
     * (e.g. {@code com.example.LoginForm$1}) is returned instead — the
     * enclosing-class prefix and {@code $N} index let a developer jump
     * straight to the offending site, which a superclass walk
     * (just {@code "JButton"}) would obscure. The result is always non-empty.
     */
    public static String getComponentClassName(Component component) {
        String name = component.getClass().getSimpleName();
        return name.isEmpty() ? component.getClass().getName() : name;
    }

    /**
     * Returns {@code true} if the window is a heavyweight popup container
     * whose content is already exposed in the accessibility tree of the
     * invoking component — including it as a separate snapshot root would
     * duplicate the popup subtree.
     * <p>
     * Swing's {@code PopupFactory} creates a {@link JWindow} and adds the
     * {@link JPopupMenu} directly to its content pane. When the invoker is
     * a {@link JComboBox} or {@link JMenu}, the popup items are already
     * accessible children of the invoker, so the standalone window is
     * redundant. Context-menu popups (whose invoker is something else) are
     * kept, because the popup window is the <em>only</em> place the content
     * appears.
     *
     * @param w the window to check
     * @return {@code true} if the window is a redundant popup container
     */
    public static boolean isRedundantPopupWindow(Window w) {
        if (!(w instanceof JWindow)) {
            return false;
        }
        Container contentPane = ((JWindow) w).getContentPane();
        for (int i = 0; i < contentPane.getComponentCount(); i++) {
            Component child = contentPane.getComponent(i);
            if (child instanceof JPopupMenu) {
                Component invoker = ((JPopupMenu) child).getInvoker();
                return invoker instanceof JComboBox || invoker instanceof JMenu;
            }
        }
        return false;
    }

    /**
     * Returns the topmost visible modal dialog, or {@code null} if no modal
     * dialog is currently visible.
     * <p>
     * First consults {@link KeyboardFocusManager} which knows the active window
     * — if that window is a visible modal dialog, it is returned immediately.
     * As a fallback, iterates all known windows (via {@link Window#getWindows()})
     * and returns the last visible modal {@link Dialog} found (last-opened is
     * last in the array).
     *
     * @return the topmost visible modal dialog, or {@code null}
     */
    public static Dialog getTopmostModalDialog() {
        // Primary: KeyboardFocusManager knows the active (topmost) window
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active instanceof Dialog) {
            Dialog d = (Dialog) active;
            if (d.isVisible() && d.isModal()) {
                return d;
            }
        }

        // Fallback: scan backwards — last-opened is last in the array
        Window[] windows = Window.getWindows();
        if (windows == null) {
            return null;
        }
        for (int i = windows.length - 1; i >= 0; i--) {
            if (windows[i] instanceof Dialog) {
                Dialog d = (Dialog) windows[i];
                if (d.isVisible() && d.isModal()) {
                    return d;
                }
            }
        }
        return null;
    }

    // ── Selection item helpers ─────────────────────────────────────────────

    /** Maximum columns included in a JTable row name summary. */
    public static final int MAX_ROW_NAME_COLUMNS = 10;

    /**
     * Returns the number of items for the given accessible.
     * <p>
     * The count depends on the component type:
     * <ul>
     *   <li><b>JTable</b> — number of rows ({@code AccessibleTable.getAccessibleRowCount()}).</li>
     *   <li><b>JComboBox</b> — {@code JComboBox.getItemCount()} (not
     *       {@code getAccessibleChildrenCount()}, which returns 1 — the popup menu).</li>
     *   <li><b>JList</b> — {@code getAccessibleChildrenCount()}.</li>
     * </ul>
     * <p>
     * The caller must verify that the accessible is a valid target
     * ({@link #supportsGetItems}) before calling this method.
     * {@code JTabbedPane} is not a supported target (dropped per P-001);
     * its tab count is derivable from the snapshot instead.
     *
     * @param a an accessible that passes {@link #supportsGetItems}
     * @return the number of items
     */
    public static int getItemCount(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (a instanceof JTable) {
            return ac.getAccessibleTable().getAccessibleRowCount();
        } else if (a instanceof JComboBox) {
            return ((JComboBox<?>) a).getItemCount();
        } else {
            return ac.getAccessibleChildrenCount();
        }
    }

    /**
     * Serializes a {@link Number} for AI-readable output: returns a {@code long}
     * when the value is a whole number, otherwise a {@code double}.
     *
     * @see <a href="tool-012-swing-get-value.md">T-012 BR-10</a>
     */
    /**
     * Returns {@code true} if the accessible is a large data component
     * (JTable, JList, or JTree) — i.e. its accessible role is TABLE, LIST,
     * or TREE.
     *
     * <p>Used by the snapshot pipeline for SC-3 row-count truncation. The
     * {@code swing_get_cells} / {@code swing_get_cell_count} eligibility gate
     * is narrower — see {@link #isGetCellsSupported(Accessible)}.</p>
     */
    public static boolean isLargeDataComponent(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        AccessibleRole role = ac.getAccessibleRole();
        return role == AccessibleRole.TABLE
                || role == AccessibleRole.LIST
                || role == AccessibleRole.TREE;
    }

    /**
     * Returns {@code true} if the accessible is a valid target for
     * {@code swing_get_cells} / {@code swing_get_cell_count} — i.e. its
     * accessible role is LIST or TREE.
     *
     * <p>JTable is deliberately excluded. Table cell renderers are stamp-painted
     * via {@code CellRendererPane} and surface as plain text {@code LABEL}s
     * with no {@code AccessibleAction}, so {@code get_cells} can never return
     * an actionable ref for a JTable. The canonical row-access tools for
     * JTable are {@code swing_get_items} /
     * {@code swing_get_item_count} (T-017 BR-09).</p>
     *
     * @see <a href="tool-020-swing-get-cells.md">T-020 BR-03</a>
     * @see <a href="tool-021-swing-get-cell-count.md">T-021 BR-03</a>
     */
    public static boolean isGetCellsSupported(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        AccessibleRole role = ac.getAccessibleRole();
        return role == AccessibleRole.LIST
                || role == AccessibleRole.TREE;
    }

    // ── JTable header & row helpers ──────────────────────────────────────────

    /**
     * Returns {@code true} if the given JTable's column header is effectively
     * visible to the user.
     * <p>
     * A header is visible when all of the following hold:
     * <ol>
     *   <li>{@code table.getTableHeader()} is non-null,</li>
     *   <li>the header component is {@linkplain Component#isVisible() visible},</li>
     *   <li>the table is inside a {@link JScrollPane} (Swing only displays the
     *       table header when it occupies the scroll pane's column-header viewport).</li>
     * </ol>
     * Zero-size headers (e.g. preferred size set to 0×0) are also treated as
     * invisible via {@link #isVisible(Accessible)}.
     */
    public static boolean isTableHeaderVisible(JTable table) {
        JTableHeader header = table.getTableHeader();
        if (header == null) return false;
        if (!header.isVisible()) return false;
        // The table header is only rendered when the table sits inside a
        // JScrollPane.  Standard Swing layout: JScrollPane → JViewport → JTable.
        Container parent = table.getParent();
        if (!(parent instanceof JViewport)) return false;
        Container grandparent = parent.getParent();
        if (!(grandparent instanceof JScrollPane)) return false;
        // Final check: if the header has been realized with zero size, treat
        // it as invisible (consistent with isVisible()).
        if (header instanceof Accessible && !isVisible((Accessible) header)) return false;
        return true;
    }

    /**
     * Returns the column header names for the given JTable, in display order.
     * <p>
     * Names are read from the {@link TableColumnModel} (which respects column
     * reordering by the user) via {@link javax.swing.table.TableColumn#getHeaderValue()}.
     * If a column's header value is {@code null}, the string {@code "null"} is used.
     *
     * @return a list of column names; empty if the table has no columns
     */
    public static List<String> getTableColumnNames(JTable table) {
        TableColumnModel cm = table.getColumnModel();
        int cols = cm.getColumnCount();
        List<String> names = new ArrayList<>(cols);
        for (int i = 0; i < cols; i++) {
            Object headerValue = cm.getColumn(i).getHeaderValue();
            names.add(headerValue != null ? headerValue.toString() : "null");
        }
        return names;
    }

    /**
     * Builds a pipe-separated summary of a single JTable row, suitable for
     * snapshot rendering.
     * <p>
     * Each cell is represented by its accessible name.  At most
     * {@link #MAX_ROW_NAME_COLUMNS} columns are included; if the table has
     * more, a trailing {@code "…"} is appended.
     * <p>
     * Example output: {@code "1 | Acme Corp | Manufacturing | Active"}.
     *
     * @param at   the accessible table
     * @param row  the 0-based row index
     * @param cols the total number of columns in the table
     * @return a pipe-separated summary of cell values
     */
    public static String buildTableRowText(AccessibleTable at, int row, int cols) {
        int colLimit = Math.min(cols, MAX_ROW_NAME_COLUMNS);
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < colLimit; col++) {
            if (col > 0) sb.append(" | ");
            Accessible cell = at.getAccessibleAt(row, col);
            sb.append(describeTableCell(cell));
        }
        if (cols > MAX_ROW_NAME_COLUMNS) {
            sb.append(" | \u2026");
        }
        return sb.toString();
    }

    /**
     * Returns a text description for a single table cell accessible.
     * <p>
     * JTable cells are virtual accessible children whose names come from
     * {@code toString()} of the cell value — renderers (even JButton renderers)
     * are just "rubber stamps" and don't appear in the accessibility tree.
     *
     * @return the cell's accessible name, or {@code "null"} if the cell or its name is null
     */
    static String describeTableCell(Accessible cell) {
        if (cell == null) return "null";
        AccessibleContext ac = cell.getAccessibleContext();
        if (ac == null) return "null";
        String name = ac.getAccessibleName();
        return (name != null) ? name : "null";
    }

    // ── Drag support ─────────────────────────────────────────────────────

    /**
     * A resolved component and a point within it.
     * Used by the drag tool to resolve both real Components and virtual
     * accessible children (JList items, JTree nodes) to a host Component
     * and a point within that component's local coordinate system.
     */
    public static class ComponentAndPoint {
        public final Component component;
        public final int x;
        public final int y;

        public ComponentAndPoint(Component component, int x, int y) {
            this.component = component;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Resolves an {@link Accessible} to a host {@link Component} and a point
     * within that component's local coordinate system.
     * <ul>
     *   <li>If the accessible IS a Component, returns it with its center as
     *       the point.</li>
     *   <li>If the accessible is a virtual child (e.g., JList item, JTree node),
     *       walks up via {@code getAccessibleParent()} to find the host Component,
     *       then uses the child's {@code AccessibleComponent.getBounds()} to compute
     *       the child's center within the host.</li>
     * </ul>
     *
     * @return the resolved component and point, or {@code null} if no Component
     *         ancestor can be found
     */
    public static ComponentAndPoint resolveComponentAndPoint(Accessible a) {
        if (a instanceof Component) {
            Component c = (Component) a;
            return new ComponentAndPoint(c, c.getWidth() / 2, c.getHeight() / 2);
        }

        // Virtual accessible child — get bounds within parent and walk up to host Component
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return null;

        AccessibleComponent accessibleComponent = ac.getAccessibleComponent();
        if (accessibleComponent != null) {
            java.awt.Rectangle bounds = accessibleComponent.getBounds();
            if (bounds != null) {
                int childCenterX = bounds.x + bounds.width / 2;
                int childCenterY = bounds.y + bounds.height / 2;

                // Walk up to find the host Component
                Accessible parent = ac.getAccessibleParent();
                while (parent != null) {
                    if (parent instanceof Component) {
                        return new ComponentAndPoint((Component) parent, childCenterX, childCenterY);
                    }
                    AccessibleContext parentAc = parent.getAccessibleContext();
                    if (parentAc == null) break;
                    parent = parentAc.getAccessibleParent();
                }
            }
        }

        return null;
    }

    /**
     * Creates a {@link Runnable} that synthesizes a drag event sequence,
     * all dispatched to the given source component.
     * <p>
     * Without waypoints: 7 events (PRESSED + 5 DRAGGED + RELEASED).
     * With waypoints: PRESSED, then interpolated DRAGGED events per segment
     * (source→wp1, wp1→wp2, ..., wpN→target), then RELEASED.
     * Timestamps increment by 16ms per event.
     *
     * @param source     the component to dispatch all events to
     * @param pressX     the press X coordinate in source-local coords
     * @param pressY     the press Y coordinate in source-local coords
     * @param targetX    the release X coordinate in source-local coords
     * @param targetY    the release Y coordinate in source-local coords
     * @param waypoints  intermediate points in source-local coords (may be empty);
     *                   each element is {@code int[]{x, y}}
     * @return a Runnable that dispatches the full drag event sequence
     */
    public static Runnable createDragAction(Component source, int pressX, int pressY,
                                            int targetX, int targetY,
                                            java.util.List<int[]> waypoints) {
        return () -> {
            long now = System.currentTimeMillis();

            // 1. MOUSE_PRESSED at press point
            source.dispatchEvent(new MouseEvent(source, MouseEvent.MOUSE_PRESSED,
                    now, InputEvent.BUTTON1_DOWN_MASK,
                    pressX, pressY, 0, false, MouseEvent.BUTTON1));

            // 2. MOUSE_DRAGGED — interpolate through segments
            int fromX = pressX, fromY = pressY;

            // Build segment endpoints: waypoints + target
            java.util.List<int[]> segments = new java.util.ArrayList<>(waypoints);
            segments.add(new int[]{targetX, targetY});

            int stepsPerSegment = waypoints.isEmpty() ? 5 : 3;
            for (int[] seg : segments) {
                int toX = seg[0], toY = seg[1];
                for (int i = 1; i <= stepsPerSegment; i++) {
                    int x = fromX + (toX - fromX) * i / stepsPerSegment;
                    int y = fromY + (toY - fromY) * i / stepsPerSegment;
                    now += 16;
                    source.dispatchEvent(new MouseEvent(source, MouseEvent.MOUSE_DRAGGED,
                            now, InputEvent.BUTTON1_DOWN_MASK,
                            x, y, 0, false, MouseEvent.NOBUTTON));
                }
                fromX = toX;
                fromY = toY;
            }

            // 3. MOUSE_RELEASED at target point
            now += 16;
            source.dispatchEvent(new MouseEvent(source, MouseEvent.MOUSE_RELEASED,
                    now, 0,
                    targetX, targetY, 0, false, MouseEvent.BUTTON1));
        };
    }

    /**
     * Creates a {@link Runnable} that performs a drag using {@link java.awt.Robot},
     * generating real OS-level mouse events.
     * <p>
     * Without waypoints: moves in 10 steps from press to target.
     * With waypoints: moves through each waypoint with 3 intermediate steps
     * per segment before reaching the target.
     * All coordinates are in screen-absolute space.
     *
     * @param pressScreenX   the press X coordinate in screen coords
     * @param pressScreenY   the press Y coordinate in screen coords
     * @param targetScreenX  the release X coordinate in screen coords
     * @param targetScreenY  the release Y coordinate in screen coords
     * @param waypoints      intermediate points in screen coords (may be empty);
     *                       each element is {@code int[]{screenX, screenY}}
     * @return a Runnable that performs the full drag via Robot
     */
    public static Runnable createRobotDragAction(int pressScreenX, int pressScreenY,
                                                  int targetScreenX, int targetScreenY,
                                                  java.util.List<int[]> waypoints) {
        return () -> {
            try {
                java.awt.Robot robot = new java.awt.Robot();
                robot.setAutoDelay(50);

                // 1. Move to press point and press
                robot.mouseMove(pressScreenX, pressScreenY);
                robot.delay(100);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.delay(100);

                // 2. Drag through segments: waypoints + target
                int fromX = pressScreenX, fromY = pressScreenY;

                java.util.List<int[]> segments = new java.util.ArrayList<>(waypoints);
                segments.add(new int[]{targetScreenX, targetScreenY});

                int stepsPerSegment = waypoints.isEmpty() ? 10 : 3;
                for (int[] seg : segments) {
                    int toX = seg[0], toY = seg[1];
                    for (int i = 1; i <= stepsPerSegment; i++) {
                        int x = fromX + (toX - fromX) * i / stepsPerSegment;
                        int y = fromY + (toY - fromY) * i / stepsPerSegment;
                        robot.mouseMove(x, y);
                    }
                    fromX = toX;
                    fromY = toY;
                }

                // 3. Release at target
                robot.delay(100);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            } catch (java.awt.AWTException e) {
                throw new RuntimeException("Robot could not be created: " + e.getMessage(), e);
            }
        };
    }

    public static Number serializeNumber(Number value) {
        double d = value.doubleValue();
        if (d % 1 == 0) {
            return (long) d;
        }
        return d;
    }

    /**
     * Reads up to {@code maxChars} characters of the accessible's text content
     * via the accessibility API.
     *
     * <p>Returns {@code ""} if the accessible exposes no {@link AccessibleText},
     * its content is empty, or the JDK returns {@code null} for any character
     * — matching what the user sees for an empty
     * {@link javax.swing.JTextField}. Per T-002 BR-12 / D_inline_value_preview, used both by
     * the snapshot inline preview and by {@code swing_get_text} so the two
     * paths share a single read.
     *
     * <p>Primary read path is
     * {@link AccessibleEditableText#getTextRange(int, int)} when available
     * (efficient bulk retrieval on all
     * {@link javax.swing.text.JTextComponent} subclasses); otherwise falls
     * back to character-by-character via
     * {@link AccessibleText#getAtIndex(int, int)}.
     *
     * @param a        the accessible to read from
     * @param maxChars maximum number of characters to read; the returned
     *                 string is at most this length. Use
     *                 {@link Integer#MAX_VALUE} for no cap.
     * @return the text content, never {@code null}
     */
    public static String readText(Accessible a, int maxChars) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return "";
        AccessibleText at = ac.getAccessibleText();
        if (at == null) return "";
        int len = at.getCharCount();
        if (len <= 0) return "";
        int readLen = Math.min(len, maxChars);

        // Primary path: AccessibleEditableText.getTextRange (efficient bulk)
        AccessibleEditableText editable = ac.getAccessibleEditableText();
        if (editable != null) {
            String text = editable.getTextRange(0, readLen);
            return text != null ? text : "";
        }

        // Fallback: character-by-character (rare — a read-only AccessibleText
        // that does not implement AccessibleEditableText). Kept as defensive
        // code per T-005 Algorithm step 8.
        StringBuilder sb = new StringBuilder(readLen);
        for (int i = 0; i < readLen; i++) {
            String ch = at.getAtIndex(AccessibleText.CHARACTER, i);
            if (ch != null) sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Reads the current numeric value of an accessible via
     * {@link AccessibleValue#getCurrentAccessibleValue()}.
     *
     * <p>Callers must first gate on {@link #supportsGetValue(Accessible)}; this
     * method assumes the gate has passed and throws {@link IllegalStateException}
     * if the accessibility API returns unexpected nulls. The return value is
     * the raw {@link Number} from the JDK — apply {@link #serializeNumber} to
     * convert to an int/long/double for output formatting.
     *
     * <p>Per T-002 BR-12 / D_inline_value_preview, used both by the snapshot inline preview
     * and by {@code swing_get_value} so the two paths share a single read.
     *
     * @throws IllegalStateException if the accessible does not expose a
     *         usable {@link AccessibleValue} (gate contract violation)
     */
    public static Number readValue(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) {
            throw new IllegalStateException("readValue called on accessible with no AccessibleContext");
        }
        AccessibleValue av = ac.getAccessibleValue();
        if (av == null) {
            throw new IllegalStateException("readValue called on accessible with no AccessibleValue (gate violation)");
        }
        Number current = av.getCurrentAccessibleValue();
        if (current == null) {
            throw new IllegalStateException("AccessibleValue.getCurrentAccessibleValue() returned null (gate violation)");
        }
        return current;
    }
}
