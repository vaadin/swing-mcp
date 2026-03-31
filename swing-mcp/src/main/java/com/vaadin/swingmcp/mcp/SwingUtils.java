package com.vaadin.swingmcp.mcp;

import javax.accessibility.*;
import javax.swing.UIManager;
import java.awt.Dialog;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
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

    // ── Roles where AccessibleSelection is internal, not user-facing ─────────
    static final Set<AccessibleRole> SUPPRESSED_SELECTION_ROLES = Set.of(
            AccessibleRole.MENU_BAR,
            AccessibleRole.MENU
    );

    /**
     * Returns the action index for the click action on the given accessible,
     * or {@code -1} if the accessible does not support click.
     * <p>
     * Checks both {@link AccessibleAction#CLICK} (AWT literal) and
     * {@link UIManager#getString(Object)} for {@code "AbstractButton.clickText"}
     * (Swing UIManager) to handle locale-safe matching.
     *
     * @see <a href="architecture.md">architecture.md § 4 — Detecting Click Support</a>
     */
    public static int supportsClick(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return -1;
        AccessibleAction aa = ac.getAccessibleAction();
        if (aa == null) return -1;
        String clickText = UIManager.getString("AbstractButton.clickText");
        for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
            String desc = aa.getAccessibleActionDescription(i);
            if (AccessibleAction.CLICK.equals(desc)
                    || (clickText != null && clickText.equals(desc))) {
                return i;
            }
        }
        return -1;
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
     * (i.e. its text content can be written).
     */
    public static boolean supportsSetText(Accessible a) {
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
        if (ac.getAccessibleValue() == null) return false;
        return !SUPPRESSED_VALUE_ROLES.contains(ac.getAccessibleRole());
    }

    /**
     * Returns {@code true} if the accessible exposes a user-meaningful
     * {@link AccessibleValue} that can be both read and written.
     * <p>
     * In addition to the suppression rules of {@link #supportsGetValue},
     * read-only value roles (e.g. progress bars) are excluded.
     */
    public static boolean supportsSetValue(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        if (ac.getAccessibleValue() == null) return false;
        AccessibleRole role = ac.getAccessibleRole();
        return !SUPPRESSED_VALUE_ROLES.contains(role)
                && !READ_ONLY_VALUE_ROLES.contains(role);
    }

    /**
     * Returns {@code true} if the accessible exposes user-facing
     * {@link AccessibleSelection}.
     * <p>
     * Roles whose AccessibleSelection is internal (e.g. menu bars, menus)
     * are suppressed.
     */
    public static boolean supportsSelection(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        if (ac.getAccessibleSelection() == null) return false;
        return !SUPPRESSED_SELECTION_ROLES.contains(ac.getAccessibleRole());
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
}
