package com.vaadin.swingmcp.mcp;

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
