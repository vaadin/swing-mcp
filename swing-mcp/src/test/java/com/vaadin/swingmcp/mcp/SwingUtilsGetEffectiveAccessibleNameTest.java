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
package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JInternalFrame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Headless tests for {@link SwingUtils#getEffectiveAccessibleName}.
 * <p>
 * JDesktopIcon is a JComponent (not a Window), so it can be instantiated
 * headlessly. The three-step fallback is testable without a display.
 *
 * @see <a href="tool-002-swing-snapshot.md">design/snapshot-format.md</a>
 */
class SwingUtilsGetEffectiveAccessibleNameTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Default path — non-JDesktopIcon components
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void regularComponent_returnsAccessibleName() {
        JButton button = new JButton("Save");
        assertEquals("Save", SwingUtils.getEffectiveAccessibleName(button));
    }

    @Test
    void regularComponent_noText_returnsEmptyString() {
        // JButton with no text has getAccessibleName() == "" (not null)
        JButton button = new JButton();
        assertEquals("", SwingUtils.getEffectiveAccessibleName(button));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame — accessible name first, then title
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void internalFrame_returnsTitle() {
        JInternalFrame frame = new JInternalFrame("Doc1");
        assertEquals("Doc1", SwingUtils.getEffectiveAccessibleName(frame));
    }

    @Test
    void internalFrame_respectsExplicitAccessibleName() {
        JInternalFrame frame = new JInternalFrame("Doc1");
        frame.getAccessibleContext().setAccessibleName("Custom Name");
        assertEquals("Custom Name", SwingUtils.getEffectiveAccessibleName(frame));
    }

    @Test
    void internalFrame_nullTitle_returnsNull() {
        JInternalFrame frame = new JInternalFrame(null);
        assertNull(SwingUtils.getEffectiveAccessibleName(frame));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDesktopIcon — three-step fallback
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void desktopIcon_step3_fallsBackToFrameTitle() {
        JInternalFrame frame = new JInternalFrame("Doc1");
        JInternalFrame.JDesktopIcon icon = frame.getDesktopIcon();

        // Icon's own accessible name is null, frame's accessible name defaults
        // to the title — so step 2 returns the title.
        assertEquals("Doc1", SwingUtils.getEffectiveAccessibleName(icon));
    }

    @Test
    void desktopIcon_step2_respectsFrameAccessibleName() {
        JInternalFrame frame = new JInternalFrame("Doc1");
        frame.getAccessibleContext().setAccessibleName("Custom Frame Name");
        JInternalFrame.JDesktopIcon icon = frame.getDesktopIcon();

        // Step 1 (icon name) is null, step 2 (frame accessible name) wins.
        assertEquals("Custom Frame Name", SwingUtils.getEffectiveAccessibleName(icon));
    }

    @Test
    void desktopIcon_step1_respectsIconAccessibleName() {
        JInternalFrame frame = new JInternalFrame("Doc1");
        frame.getAccessibleContext().setAccessibleName("Custom Frame Name");
        JInternalFrame.JDesktopIcon icon = frame.getDesktopIcon();
        icon.getAccessibleContext().setAccessibleName("Custom Icon Name");

        // Step 1 (icon name) wins over step 2 and 3.
        assertEquals("Custom Icon Name", SwingUtils.getEffectiveAccessibleName(icon));
    }

    @Test
    void desktopIcon_nullTitle_returnsNull() {
        JInternalFrame frame = new JInternalFrame(null);
        JInternalFrame.JDesktopIcon icon = frame.getDesktopIcon();

        // All three steps return null.
        assertNull(SwingUtils.getEffectiveAccessibleName(icon));
    }

    @Test
    void desktopIcon_emptyTitle_frameAccessibleNameIsEmpty_returnsNull() {
        JInternalFrame frame = new JInternalFrame("");
        JInternalFrame.JDesktopIcon icon = frame.getDesktopIcon();

        // Step 2: frame accessible name is "" (empty) — skipped.
        // Step 3: frame title is "" — returned as-is (empty string).
        assertEquals("", SwingUtils.getEffectiveAccessibleName(icon));
    }
}
