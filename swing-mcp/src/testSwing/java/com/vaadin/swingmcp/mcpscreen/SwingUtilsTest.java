package com.vaadin.swingmcp.mcpscreen;

import com.vaadin.swingmcp.mcp.SwingUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Screen-mode tests for {@link SwingUtils}.
 * Covers getTopmostModalDialog, plus supportsClick and isEffectivelyEnabled
 * for top-level window components (JFrame, JDialog) that require a display.
 */

class SwingUtilsTest extends AbstractScreenTest {

    private final List<Window> createdWindows = new ArrayList<>();

    @AfterEach
    void disposeCreatedWindows() throws InterruptedException {
        for (Window w : createdWindows) {
            if (w.isVisible()) {
                SwingUtilities.invokeLater(() -> w.setVisible(false));
                awaitVisibility(w, false);
            }
            SwingUtilities.invokeLater(w::dispose);
        }
        createdWindows.clear();
    }

    private JDialog newModalDialog() {
        JDialog d = new JDialog((Frame) null, "Modal", true);
        d.setSize(100, 100);
        createdWindows.add(d);
        return d;
    }

    private JDialog newNonModalDialog() {
        JDialog d = new JDialog((Frame) null, "NonModal", false);
        d.setSize(100, 100);
        createdWindows.add(d);
        return d;
    }

    /** Posts setVisible(true/false) on the EDT and waits until the dialog reaches that state. */
    private void setVisible(JDialog dialog, boolean visible) throws InterruptedException {
        SwingUtilities.invokeLater(() -> dialog.setVisible(visible));
        awaitVisibility(dialog, visible);
    }

    private void awaitVisibility(Window window, boolean expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (window.isVisible() != expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(expected, window.isVisible(),
                "Window visibility did not reach " + expected + " within 2 s");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Null cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void returnsNullWhenNoDialogsAreVisible() {
        // Only a JFrame from AbstractScreenTest.assertScreenPresent() exists,
        // and it is not a modal Dialog.
        assertNull(SwingUtils.getTopmostModalDialog());
    }

    @Test
    void returnsNullForVisibleNonModalDialog() throws InterruptedException {
        JDialog dialog = newNonModalDialog();
        setVisible(dialog, true);

        assertNull(SwingUtils.getTopmostModalDialog());
    }

    @Test
    void returnsNullForHiddenModalDialog() {
        JDialog dialog = newModalDialog();
        assertFalse(dialog.isVisible(), "dialog must not be visible yet");

        assertNull(SwingUtils.getTopmostModalDialog());
    }

    @Test
    void returnsNullAfterModalDialogIsHidden() throws InterruptedException {
        JDialog dialog = newModalDialog();
        setVisible(dialog, true);
        setVisible(dialog, false);

        assertNull(SwingUtils.getTopmostModalDialog());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Positive cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void returnsVisibleModalDialog() throws InterruptedException {
        JDialog dialog = newModalDialog();
        setVisible(dialog, true);

        assertSame(dialog, SwingUtils.getTopmostModalDialog());
    }

    @Test
    void returnsLastOpenedModalDialog() throws InterruptedException {
        // Two modal dialogs created in order; only the second is shown.
        // Window.getWindows() is ordered by creation time; the fallback scan
        // goes backwards, so the second (last-created visible) dialog is returned.
        JDialog first = newModalDialog();
        JDialog second = newModalDialog();
        setVisible(second, true);

        assertFalse(first.isVisible());
        assertSame(second, SwingUtils.getTopmostModalDialog());
    }

    @Test
    void ignoresNonModalDialogWhenModalDialogIsAlsoVisible() throws InterruptedException {
        JDialog nonModal = newNonModalDialog();
        JDialog modal = newModalDialog();
        setVisible(nonModal, true);
        setVisible(modal, true);

        assertSame(modal, SwingUtils.getTopmostModalDialog());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // supportsClick — top-level windows (JFrame, JDialog)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jFrame_doesNotSupportClick() {
        JFrame frame = new JFrame("Test");
        createdWindows.add(frame);
        assertNull(SwingUtils.supportsClick(frame));
    }

    @Test
    void jDialog_doesNotSupportClick() {
        JDialog dialog = newNonModalDialog();
        assertNull(SwingUtils.supportsClick(dialog));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // isEffectivelyEnabled — top-level windows (JFrame, JDialog)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jFrame_enabledByDefault() {
        JFrame frame = new JFrame("Test");
        createdWindows.add(frame);
        assertTrue(SwingUtils.isEffectivelyEnabled(frame));
    }

    @Test
    void jFrame_disabled() {
        JFrame frame = new JFrame("Test");
        createdWindows.add(frame);
        frame.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(frame));
    }

    @Test
    void jDialog_enabledByDefault() {
        JDialog dialog = newNonModalDialog();
        assertTrue(SwingUtils.isEffectivelyEnabled(dialog));
    }

    @Test
    void jDialog_disabled() {
        JDialog dialog = newNonModalDialog();
        dialog.setEnabled(false);
        assertFalse(SwingUtils.isEffectivelyEnabled(dialog));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // supportsTogglePopup — top-level windows (JFrame, JDialog)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jFrame_doesNotSupportTogglePopup() {
        JFrame frame = new JFrame("Test");
        createdWindows.add(frame);
        assertEquals(-1, SwingUtils.supportsTogglePopup(frame));
    }

    @Test
    void jDialog_doesNotSupportTogglePopup() {
        JDialog dialog = newNonModalDialog();
        assertEquals(-1, SwingUtils.supportsTogglePopup(dialog));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // supportsGetText / supportsSetText — top-level windows (JFrame, JDialog)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jFrame_doesNotSupportGetText() {
        JFrame frame = new JFrame("Test");
        createdWindows.add(frame);
        assertFalse(SwingUtils.supportsGetText(frame));
    }

    @Test
    void jFrame_doesNotSupportSetText() {
        JFrame frame = new JFrame("Test");
        createdWindows.add(frame);
        assertFalse(SwingUtils.supportsSetText(frame));
    }

    @Test
    void jDialog_doesNotSupportGetText() {
        JDialog dialog = newNonModalDialog();
        assertFalse(SwingUtils.supportsGetText(dialog));
    }

    @Test
    void jDialog_doesNotSupportSetText() {
        JDialog dialog = newNonModalDialog();
        assertFalse(SwingUtils.supportsSetText(dialog));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // supportsGetValue / supportsSetValue — top-level windows (JFrame, JDialog)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jFrame_doesNotSupportGetValue() {
        JFrame frame = new JFrame("Test");
        createdWindows.add(frame);
        assertFalse(SwingUtils.supportsGetValue(frame));
    }

    @Test
    void jFrame_doesNotSupportSetValue() {
        JFrame frame = new JFrame("Test");
        createdWindows.add(frame);
        assertFalse(SwingUtils.supportsSetValue(frame));
    }

    @Test
    void jDialog_doesNotSupportGetValue() {
        JDialog dialog = newNonModalDialog();
        assertFalse(SwingUtils.supportsGetValue(dialog));
    }

    @Test
    void jDialog_doesNotSupportSetValue() {
        JDialog dialog = newNonModalDialog();
        assertFalse(SwingUtils.supportsSetValue(dialog));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // supportsSelection — top-level windows (JFrame, JDialog)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jFrame_doesNotSupportSelection() {
        JFrame frame = new JFrame("Test");
        createdWindows.add(frame);
        assertFalse(SwingUtils.supportsSelection(frame));
    }

    @Test
    void jDialog_doesNotSupportSelection() {
        JDialog dialog = newNonModalDialog();
        assertFalse(SwingUtils.supportsSelection(dialog));
    }
}
