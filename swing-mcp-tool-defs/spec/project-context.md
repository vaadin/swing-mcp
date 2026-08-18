# Project Context

`swing-mcp-tool-defs` is the **shared contract artifact** for the
Swing MCP server: identity, instructions, and the full tool
manifest, expressed as plain Java constants. Two consumers depend
on it:

- **`swing-mcp`** — the in-process HTTP server embedded inside the
  Swing application. Uses these constants for its own server-info
  block, instructions, and tool registration.
- **`swing-mcp-proxy`** — the stdio forwarding proxy spawned by
  Claude Code. Uses the same constants as the static manifest it
  presents to Claude (`tools/list`) and as the drift-probe oracle
  for the upstream `swing-mcp`.

Because both consumers source the same constants, drift on
identity (server name, version, instructions, tool names /
descriptions / schemas) is structurally impossible. The only
remaining drift surface is "did both modules get rebuilt and
shipped together?" — which is the question the proxy's runtime
drift probe (DR-forwarding-proxy) catches.

## 1. Vision

The contract between Claude and the Swing MCP server is a single
artifact, not a pair of definitions that drift apart over time.
Pulling everything that names a tool or describes the server into
this module makes "what does Claude see?" a one-import question.

## 2. Users

Internal. Two intended consumers (above). No external dependents.

## 3. Module shape

Pure-data module, Java only — no Swing classes, no resources, no
JSON files. Declarations live in code so the build catches typos
and the IDE makes refactors safe.

- Package `com.vaadin.swingmcp.tools` hosts `SwingTools`
  (per-tool `ToolDescriptor` constants and the aggregate `ALL`)
  plus the shared message constants and server-identity
  constants.
- `ToolDescriptor` itself lives in the parent package
  `com.vaadin.swingmcp` inside `tiny-mcp-server` (DR-settable-listeners) — it's
  a generic protocol type and belongs with the rest of the
  protocol POJOs.

Exported constants (target shape):

```java
package com.vaadin.swingmcp.tools;

public final class SwingTools {
    public static final ToolDescriptor SWING_CLICK = ...;
    public static final ToolDescriptor SWING_TYPE = ...;
    // ...one constant per tool...

    public static final List<ToolDescriptor> ALL = List.of(
        SWING_CLICK, SWING_TYPE, /* ... */);

    public static final String SERVER_NAME    = "Swing MCP";
    public static final String SERVER_VERSION = "0.0.1";
    public static final String INSTRUCTIONS   = """
        ...the multi-paragraph block currently in SwingMCP.java...
        """;

    /** Shared between in-process and proxy transports
     *  (the only error wording common to both — see grilling
     *  Sub-item 1). */
    public static final String SESSION_LOST_MESSAGE =
        "Swing application session was lost — call swing_snapshot to re-orient and retry.";
}
```

Per-tool constants are `public static final` (not lazy-method
forms) — the list is small, descriptors are immutable, and the
field form keeps grep-ability.

`SwingTools.ALL` is **only the proxy's manifest**.
`SwingMCP.registerTools()` (in `swing-mcp`) keeps its current
per-subclass `addTool` shape and is **not** rewired to iterate
`ALL` (Q31). Coherence between the two is enforced by a unit test
that boots `SwingMCP`, calls `listTools`, and deep-equals against
`SwingTools.ALL` — drift between manifest and registration is a
developer bug, caught at test time.

## 4. Constraints

- Depends only on `tiny-mcp-server` (for `ToolDescriptor` and
  `MCPProtocol.InputSchema`). No Swing classes.
- Pure Java, no resources, no reflection.
- Java 17+ (matches the rest of the project).
- Published to Maven Central via `configureMavenCentral(...)`
  because it is a transitive dependency of the published
  `swing-mcp` artifact (Q32a).

## 5. Out of scope

This module has no `architecture.md`, no `decisions.md`, and no
`tools/` directory. It's a pure data module — the cross-cutting
decisions that govern its content live in
`tiny-mcp-server/spec/decisions.md` (DR-settable-listeners for `ToolDescriptor`,
DR-structural-schema-equality for schema equality) and in `swing-mcp/spec/tools/`
(per-tool specs, which generate the descriptor values mechanically
once their fields are locked).

---

# Related Documents

- [`tiny-mcp-server/spec/decisions.md`](../../tiny-mcp-server/spec/decisions.md) — DR-settable-listeners (`ToolDescriptor`), DR-structural-schema-equality (schema equality)
- [`swing-mcp/spec/tools/`](../../swing-mcp/spec/tools/) — per-tool specs (source of truth for each `ToolDescriptor`'s fields)
- [`swing-mcp-proxy/spec/project-context.md`](../../swing-mcp-proxy/spec/project-context.md) — proxy consumer
