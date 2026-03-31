package com.vaadin.swingmcp.mcp;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleState;
import javax.swing.UIManager;
import java.awt.Dialog;
import java.awt.KeyboardFocusManager;
import java.awt.Window;

/**
 * A collection of static utility methods for Swing-related operations.
 */
public final class SwingUtils {

    private SwingUtils() {
    }

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
