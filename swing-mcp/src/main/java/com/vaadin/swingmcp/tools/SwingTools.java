/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.swingmcp.tools;

import com.github.mvysny.tinymcpserver.ToolDescriptor;
import com.github.mvysny.tinymcpserver.InputSchemaBuilder;

import java.util.List;

/**
 * Everything the model reads about this server, in one place: the server
 * identity, the instructions, and one {@link ToolDescriptor} per tool. A tool
 * binds its descriptor rather than declaring its own name, description and
 * schema:
 *
 * <pre>{@code
 * public SwingClickTool() {
 *     super(SwingTools.SWING_CLICK);
 * }
 * }</pre>
 *
 * <p>{@link #ALL} is the manifest: every descriptor, each of which must be
 * registered by the server. {@code SwingToolsCoherenceTest} holds the two
 * together (D_shared_tool_manifest).
 */
public final class SwingTools {

    private SwingTools() {}

    /** Server name advertised in {@code initialize.serverInfo.name}. */
    public static final String SERVER_NAME = "Swing MCP";

    /** Server version advertised in {@code initialize.serverInfo.version}. */
    public static final String SERVER_VERSION = "0.0.1";

    /** Multi-paragraph instructions advertised in {@code initialize.instructions}. */
    public static final String INSTRUCTIONS =
            "This server provides tools to inspect and interact with a running Java Swing application.\n" +
            "The MCP server runs in-process with the Swing application: if the application exits, this\n" +
            "server becomes unreachable. To restore the connection, ask the human operator to restart\n" +
            "the Swing application.\n" +
            "\n" +
            "## Workflow\n" +
            "\n" +
            "1. Call `swing_snapshot` first to get the current UI state as an accessibility tree. Each component has a `ref` ID used by all interaction tools.\n" +
            "2. Use the `ref` values from the snapshot to target specific components for interaction (click, set_text, etc.).\n" +
            "3. After each interaction, call `swing_snapshot` again to verify the UI has updated as expected.\n" +
            "4. Use `swing_screenshot` only when the accessibility tree alone is ambiguous — it returns a PNG image that may consume many tokens.\n" +
            "\n" +
            "## Key behaviors\n" +
            "\n" +
            "- `swing_snapshot` and `swing_screenshot` return the current state immediately.\n" +
            "- Interaction tools (`swing_click`, `swing_set_text`, etc.) dispatch the action to the Swing event thread asynchronously and return a one-line echo of the form `Dispatched <action> on ref=N [to <value>] — call swing_snapshot to verify the outcome`. The echo does NOT mean the UI changed — listeners can veto, revert, or open a dialog.\n" +
            "- `ref` values may change after UI transitions (dialogs opening/closing, navigation). Re-snapshot after significant state changes before using stale refs.\n" +
            "- Do not call mutation tools in parallel — each successful mutation clears the ref map, so the second call will fail with a stale-ref error. Issue tool calls sequentially.\n" +
            "- `swing_close` on a window with unsaved changes may trigger a confirmation dialog — snapshot afterward to detect it.";

    // ===== Tool descriptors (alphabetical by constant name) =====

    public static final ToolDescriptor SWING_CLEAR_SELECTION = new ToolDescriptor(
            "swing_clear_selection",
            "Clear the selection of a UI component by ref. Works with multi-select components (JList, JTable) and some single-select components (JComboBox). JTabbedPane does not allow an empty selection. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_CLICK = new ToolDescriptor(
            "swing_click",
            "Click a UI component by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_CLOSE = new ToolDescriptor(
            "swing_close",
            "Close a window, dialog, internal frame, or desktop icon (iconified internal frame) by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.\n  Note: Closing a window may terminate the app; since Swing-MCP runs as a part of that app it will be killed too, and\n  the client will see a dropped HTTP connection. If this happens, the only way to recover is to re-run the Swing app",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_DECREMENT = new ToolDescriptor(
            "swing_decrement",
            "Decrement the value of a UI component (e.g. JSpinner, JSlider) by one step. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_DRAG = new ToolDescriptor(
            "swing_drag",
            "Drag a UI component to another location. Dispatches mouse drag events (PRESSED → DRAGGED → RELEASED). Source: source_ref identifies the component; by default drags from its center. Provide optional source_x/source_y (component-relative pixel offsets) to start from a specific point within the component (e.g. a painted node on a canvas). Target: target_ref identifies the drop component; by default drops at its center. Provide optional target_x/target_y (component-relative pixel offsets) to drop at a specific point within the target component. Optional via: flat array of [ref, x, y, ...] triplets defining intermediate waypoints the drag passes through (e.g. for self-edges that must exit and re-enter a node). Refs are obtained from swing_snapshot or swing_get_cells. A drag source or drop target with no actions, such as a JTree leaf or a JLabel, has no ref by default: call swing_snapshot with all_refs=true first.",
            new InputSchemaBuilder()
                    .requiredInteger("source_ref",
                            "The element reference number of the component to drag from. By default drags from the component's center.")
                    .optionalInteger("source_x",
                            "Component-relative X pixel offset for the drag start position within the source component. Defaults to the component's center X. Must be provided together with source_y.")
                    .optionalInteger("source_y",
                            "Component-relative Y pixel offset for the drag start position within the source component. Defaults to the component's center Y. Must be provided together with source_x.")
                    .requiredInteger("target_ref",
                            "The element reference number of the component to drop onto. By default drops at the component's center.")
                    .optionalInteger("target_x",
                            "Component-relative X pixel offset for the drop position within the target component. Defaults to the component's center X. Must be provided together with target_y.")
                    .optionalInteger("target_y",
                            "Component-relative Y pixel offset for the drop position within the target component. Defaults to the component's center Y. Must be provided together with target_x.")
                    .optionalArray("via",
                            "Flat array of [ref, x, y, ...] triplets defining intermediate waypoints the drag passes through before reaching the target. Each triplet: ref identifies the component, x/y are component-relative pixel offsets. Length must be divisible by 3. Example for a self-edge: [canvasRef, 200, 100] to route the drag outside a node and back.")
                    .build());

    public static final ToolDescriptor SWING_GET_CELL_COUNT = new ToolDescriptor(
            "swing_get_cell_count",
            "Get the total number of accessible children (cells) of a large data component (JList, JTree) by ref. Returns the count as a plain integer in the same index space as swing_get_cells. For JList, this is the item count. For JTree, this is the top-level visible node count. For JTable, use swing_get_item_count instead — table cells are plain text labels with no actionable children. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_GET_CELLS = new ToolDescriptor(
            "swing_get_cells",
            "Enumerate accessible children of a large data component (JList, JTree) by ref, returning refs for any actionable children inside. Returns a paged accessibility tree (same format as swing_snapshot) rooted at the requested children. Parameters: ref (integer), offset (0-based integer), length (integer). Indices are in the accessible children index space (not the selection item index space — use swing_get_items for selection). Use this when you need to click or otherwise interact with a component nested inside a list item or tree node. For JTable, use swing_get_items instead — table cells are plain text labels with no actionable children. WARNING: this tool replaces the ref map — refs from prior swing_snapshot or swing_get_cells calls become invalid. The parent component gets ref=1 so you can call get_cells again with a different offset. Call swing_snapshot to restore the full-tree ref map. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .requiredInteger("offset", "0-based start index for paging").withMinimum(0)
                    .requiredInteger("length", "Number of children to return").withMinimum(0)
                    .optionalBoolean("all_refs",
                            "If true, every node on the page gets a ref, not just the ones with actions, as in swing_snapshot.")
                    .build());

    public static final ToolDescriptor SWING_GET_DESCRIPTION = new ToolDescriptor(
            "swing_get_description",
            "Read the full description of a UI component by ref. Returns the complete text that was truncated in the snapshot's description slot. The description is resolved from the accessibility API (accessibleDescription, or tooltip fallback). Requires a ref obtained from swing_snapshot.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot")
                    .build());

    public static final ToolDescriptor SWING_GET_ITEM_COUNT = new ToolDescriptor(
            "swing_get_item_count",
            "Get the total number of items of a UI component by ref. Supported components: JList, JComboBox, JTable. Returns the count as a plain integer. For JTable, this is the canonical way to get the row count regardless of selection mode (use this instead of swing_get_cell_count, which does not support JTable). Note: swing_set_selection still requires the table to be in row-selection mode. For JTabbedPane, count the tabs directly from the snapshot — each tab renders as `- (page_tab) N \"title\"` with its 0-based index. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_GET_ITEMS = new ToolDescriptor(
            "swing_get_items",
            "List items of a UI component by ref. Supported components: JList, JComboBox, JTable. Returns a paged JSON array of items (0-based index + name). Indices are in the selection item index space — pass them directly to swing_set_selection. For JTable, this is the canonical way to page through rows regardless of selection mode: index is the row index and name is a pipe-separated summary of cell values (use this instead of swing_get_cells, which does not support JTable). For JTabbedPane, use the swing_snapshot tool — each tab already renders as `- (page_tab) N \"title\"` with its 0-based index and [disabled] / [selected] state; pass the index straight to swing_set_selection as [N]. Requires offset and length parameters for paging. If offset+length is bigger than the amount of data available, fewer items than requested may be returned. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .requiredInteger("offset", "0-based start index for paging").withMinimum(0)
                    .requiredInteger("length", "Number of items to return").withMinimum(0)
                    .build());

    public static final ToolDescriptor SWING_GET_SELECTION = new ToolDescriptor(
            "swing_get_selection",
            "Read the current selection of a UI component by ref. Returns JSON with selectedCount and selected items (0-based index + name). selectedCount is the whole selection; past 100 items the list stops and \"truncated\":true is added. For JTable, index is the row index (not cell index) and name is a pipe-separated summary of cell values. For a JComboBox showing a value that is not one of its items (typed into an editable combo), index is -1 and name is that value. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_GET_TEXT = new ToolDescriptor(
            "swing_get_text",
            "Read the text content of a UI component by ref. Requires a ref obtained from swing_snapshot or swing_get_cells. JPasswordField contents are not readable — use swing_set_text if you need to write a known value.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_GET_VALUE = new ToolDescriptor(
            "swing_get_value",
            "Read the numeric value of a UI component by ref. Returns JSON with current, min, max. Missing min/max means unbounded. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_ICONIFY = new ToolDescriptor(
            "swing_iconify",
            "Iconify (minimize) a Frame (including JFrame) or JInternalFrame by ref. Frame is minimized to the OS taskbar; JInternalFrame is replaced by a JDesktopIcon on its JDesktopPane. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_INCREMENT = new ToolDescriptor(
            "swing_increment",
            "Increment the value of a UI component (e.g. JSpinner, JSlider) by one step. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_RESTORE = new ToolDescriptor(
            "swing_restore",
            "Restore (de-iconify) an iconified Frame (including JFrame), JDesktopIcon (iconified JInternalFrame) or iconified JInternalFrame by ref. Frame is restored from the OS taskbar; JDesktopIcon is replaced by its JInternalFrame on the JDesktopPane. The resulting window state depends on the pre-iconification state and the platform window manager — the window may be restored to normal or maximized. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_SCREENSHOT = new ToolDescriptor(
            "swing_screenshot",
            "Captures a screenshot of the Swing application. By default returns it as an inline PNG image. If save_to is provided, writes the PNG to that absolute path on the MCP server's filesystem and returns a text confirmation (path + dimensions + format) instead of the inline image — useful for retaining a reference image across follow-up turns or sessions without spending tokens on multimodal context each time.",
            new InputSchemaBuilder()
                    .optionalString("save_to",
                            "Absolute file path to write the PNG to. Relative paths are rejected — the MCP server's working directory is not visible to the caller, so the agent cannot reliably guess where you mean. Pass an absolute path under your project root. The parent directory must already exist (it is not auto-created). When this parameter is set, the inline image is not returned.")
                    .build());

    public static final ToolDescriptor SWING_SELECT_ALL = new ToolDescriptor(
            "swing_select_all",
            "Select all items in a multi-selection UI component by ref. Only works on components marked multi-selection in the snapshot (JList, JTable). Single-selection components are rejected. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_SET_SELECTION = new ToolDescriptor(
            "swing_set_selection",
            "Set the selection of a UI component by ref. Pass 0-based item indices (as returned by swing_get_selection). For single-selection components, pass at most one index. For JTable, pass row indices — the tool translates to cell indices internally. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .requiredArray("indices", "Array of 0-based item indices to select (empty array clears selection)")
                    .build());

    public static final ToolDescriptor SWING_SET_TEXT = new ToolDescriptor(
            "swing_set_text",
            "Set the text content of a UI component by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .requiredString("text", "The text to set")
                    .build());

    public static final ToolDescriptor SWING_SET_VALUE = new ToolDescriptor(
            "swing_set_value",
            "Set the numeric value of a UI component by ref. Call swing_get_value first to check the current value and valid range. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .requiredNumber("value", "The numeric value to set")
                    .build());

    public static final ToolDescriptor SWING_SNAPSHOT = new ToolDescriptor(
            "swing_snapshot",
            "Returns an accessibility tree snapshot of the Swing application. Use this to understand the current UI structure and identify components for interaction via their numeric refs. Mutation actions prefixed with ! are unavailable because the component is disabled or read-only.",
            new InputSchemaBuilder()
                    .optionalString("filter_substring",
                            "If provided, returns a pruned tree: nodes whose text contains the substring (case-insensitive) are included together with their ancestors (for context) and all descendants (e.g. table rows, list items). Non-matching sibling branches are dropped.")
                    .optionalBoolean("all_refs",
                            "If true, every node gets a ref, not just the ones with actions. Use it before swing_drag, whose source, target or waypoint may have no actions, such as a JTree leaf or a JLabel. Default false.")
                    .build());

    public static final ToolDescriptor SWING_TOGGLE_EXPAND = new ToolDescriptor(
            "swing_toggle_expand",
            "Toggles (expand or collapses based on current state) a JTree node by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    public static final ToolDescriptor SWING_TOGGLE_POPUP = new ToolDescriptor(
            "swing_toggle_popup",
            "Open or close the popup of a UI component by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.",
            new InputSchemaBuilder()
                    .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                    .build());

    /**
     * The full Swing MCP tool manifest. Order matches the registration
     * order in {@code SwingMCP.registerTools()} so a deep-equal coherence
     * test produces useful diffs when something drifts.
     */
    public static final List<ToolDescriptor> ALL = List.of(
            SWING_SNAPSHOT,
            SWING_SCREENSHOT,
            SWING_CLICK,
            SWING_TOGGLE_POPUP,
            SWING_INCREMENT,
            SWING_DECREMENT,
            SWING_GET_TEXT,
            SWING_GET_DESCRIPTION,
            SWING_SET_TEXT,
            SWING_GET_VALUE,
            SWING_SET_VALUE,
            SWING_TOGGLE_EXPAND,
            SWING_CLOSE,
            SWING_GET_SELECTION,
            SWING_SET_SELECTION,
            SWING_CLEAR_SELECTION,
            SWING_GET_ITEMS,
            SWING_GET_ITEM_COUNT,
            SWING_SELECT_ALL,
            SWING_GET_CELLS,
            SWING_GET_CELL_COUNT,
            SWING_ICONIFY,
            SWING_RESTORE,
            SWING_DRAG);
}
