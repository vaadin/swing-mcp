package com.vaadin.swingmcp.mcp;

import javax.accessibility.*;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
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
            AccessibleRole.PAGE_TAB
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
    //   tree nodes, not the tree itself. See UC-014 design notes.
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
     * @see <a href="architecture.md">architecture.md § 4 — Detecting Click Support</a>
     */
    public static Runnable supportsClick(Accessible a) {
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
     * Returns {@code true} if the accessible exposes {@link AccessibleText}
     * (i.e. its text content can be read).
     */
    public static boolean supportsGetText(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        return ac.getAccessibleText() != null;
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
     * @see <a href="use-case-014-swing-get-selection.md">UC-014 BR-10</a>
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
     * @see <a href="architecture.md">architecture.md § 6 — Selection Action Groups</a>
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
     * @see <a href="use-case-008-swing-increment.md">UC-008 BR-03</a>
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
     * @see <a href="use-case-009-swing-decrement.md">UC-009 BR-03</a>
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
     * @see <a href="use-case-010-swing-toggle-expand.md">UC-010 BR-03</a>
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
     * have OS decorations, and will not terminate the JVM on close.
     *
     * @see <a href="architecture.md">architecture.md § 4 — Detecting Close Support</a>
     */
    public static boolean supportsClose(Accessible a) {
        if (!(a instanceof Window)) return false;
        Window window = (Window) a;
        if (!window.isShowing()) return false;
        if (window instanceof Frame && ((Frame) window).isUndecorated()) return false;
        if (window instanceof Dialog && ((Dialog) window).isUndecorated()) return false;
        if (window instanceof JFrame &&
                ((JFrame) window).getDefaultCloseOperation() == WindowConstants.EXIT_ON_CLOSE) return false;
        return true;
    }

    /**
     * Returns whether the given accessible is effectively enabled: the
     * accessible itself must have {@link AccessibleState#ENABLED} in its
     * state set, and its parent (if any) must also be effectively enabled.
     * <p>
     * Virtual accessible children (e.g. JList items, JTable cells) may not
     * propagate the parent component's disabled state, so this method walks
     * the entire parent chain.
     *
     * @see <a href="architecture.md">architecture.md § 4 — Effectively Enabled Check</a>
     */
    public static boolean isEffectivelyEnabled(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        if (!ac.getAccessibleStateSet().contains(AccessibleState.ENABLED)) return false;
        Accessible parent = ac.getAccessibleParent();
        return parent == null || isEffectivelyEnabled(parent);
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
     * Returns the number of selectable items for the given accessible.
     * <p>
     * The count depends on the component type:
     * <ul>
     *   <li><b>JTable</b> — number of rows ({@code AccessibleTable.getAccessibleRowCount()}).</li>
     *   <li><b>JComboBox</b> — {@code JComboBox.getItemCount()} (not
     *       {@code getAccessibleChildrenCount()}, which returns 1 — the popup menu).</li>
     *   <li><b>All others</b> (JList, JTabbedPane) — {@code getAccessibleChildrenCount()}.</li>
     * </ul>
     * <p>
     * The caller must verify that the accessible supports selection
     * ({@link #supportsSelection}) before calling this method.
     *
     * @param a an accessible that supports selection
     * @return the number of selectable items
     */
    public static int getSelectableItemsCount(Accessible a) {
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
     * @see <a href="use-case-012-swing-get-value.md">UC-012 BR-10</a>
     */
    /**
     * Returns {@code true} if the accessible is a large data component
     * (JTable, JList, or JTree) — i.e. its accessible role is TABLE, LIST,
     * or TREE.
     *
     * @see <a href="use-case-020-swing-get-cells.md">UC-020 BR-03</a>
     */
    public static boolean isLargeDataComponent(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        AccessibleRole role = ac.getAccessibleRole();
        return role == AccessibleRole.TABLE
                || role == AccessibleRole.LIST
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

    public static Number serializeNumber(Number value) {
        double d = value.doubleValue();
        if (d % 1 == 0) {
            return (long) d;
        }
        return d;
    }
}
