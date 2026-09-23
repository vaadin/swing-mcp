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
 * The {@link SwingUtils} cases that need a real {@code JFrame} or {@code JDialog}.
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
