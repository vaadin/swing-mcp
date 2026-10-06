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
package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetDescriptionTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetDescriptionTool getDescTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        getDescTool = new SwingGetDescriptionTool();
        context = new SwingToolContext(Runnable::run);
    }

    private String snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        MCPProtocol.Content result = snapshotTool.execute(new Parameters(Map.of()), context);
        return result.getText();
    }

    private String getDescription(int ref) throws Exception {
        MCPProtocol.Content result = getDescTool.execute(new Parameters(Map.of("ref", ref)), context);
        return result == null ? null : result.getText();
    }

    /** A string of exactly the given length, built from repeated 'a'. */
    private static String chars(int length) {
        return "a".repeat(length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tool tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void longDescriptionReturnedInFull() throws Exception {
        String desc = chars(150);
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(desc);
        snapshot(button);

        assertEquals(desc, getDescription(context.getRefOf(button)));
    }

    @Test
    void shortDescriptionReturnedAsIs() throws Exception {
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription("Short tooltip");
        snapshot(button);

        assertEquals("Short tooltip", getDescription(context.getRefOf(button)));
    }

    @Test
    void noDescriptionReturnsEmptyString() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        assertEquals("", getDescription(context.getRefOf(button)));
    }

    @Test
    void invalidRefReturnsError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> getDescription(9999));
        assertEquals("Component with ref 9999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    @Test
    void refMapPreservedAfterCall() throws Exception {
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription("Some description");
        snapshot(button);

        int ref = context.getRefOf(button);
        assertEquals("Some description", getDescription(ref));
        assertEquals("Some description", getDescription(ref));
    }

    @Test
    void disabledComponentReturnsDescription() throws Exception {
        JButton button = new JButton("OK");
        button.setEnabled(false);
        button.getAccessibleContext().setAccessibleDescription("Help text");
        snapshot(button);

        assertEquals("Help text", getDescription(context.getRefOf(button)));
    }

    @Test
    void descriptionExceedingMaxLengthIsTruncated() throws Exception {
        String desc = chars(1100);
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(desc);
        snapshot(button);

        assertEquals(
                chars(1000) + "\n... (truncated, 1100 total characters)",
                getDescription(context.getRefOf(button)));
    }

    @Test
    void descriptionExactlyAtMaxLengthIsNotTruncated() throws Exception {
        String desc = chars(1000);
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(desc);
        snapshot(button);

        assertEquals(desc, getDescription(context.getRefOf(button)));
    }

    @Test
    void htmlTooltipDescriptionIsCleaned() throws Exception {
        JButton button = new JButton("OK");
        button.setToolTipText("<html><b>Bold</b> text &amp; more <i>italic</i> content that goes on and on "
                + "to make this tooltip exceed the 120 character limit in the snapshot description slot</html>");
        snapshot(button);

        assertEquals(
                "Bold text & more italic content that goes on and on "
                        + "to make this tooltip exceed the 120 character limit in the snapshot description slot",
                getDescription(context.getRefOf(button)));
    }

    @Test
    void tooltipFallbackReturnsFullTooltip() throws Exception {
        String longTooltip = chars(150);
        JButton button = new JButton("OK");
        button.setToolTipText(longTooltip);
        snapshot(button);

        assertEquals(longTooltip, getDescription(context.getRefOf(button)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Snapshot integration tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void cappedDescriptionShowsGetDescriptionInActions() throws Exception {
        String desc = chars(150);
        JButton button = new JButton("Save");
        button.getAccessibleContext().setAccessibleDescription(desc);

        assertEquals(
                "- JButton (push_button) \"Save\" \"" + chars(120) + "\u2026\" [ref=1] actions: click, get_description",
                snapshot(button));
    }

    @Test
    void shortDescriptionDoesNotShowGetDescription() throws Exception {
        JButton button = new JButton("Save");
        button.getAccessibleContext().setAccessibleDescription("Short help");

        assertEquals(
                "- JButton (push_button) \"Save\" \"Short help\" [ref=1] actions: click",
                snapshot(button));
    }

    @Test
    void labelWithLongTooltipGetsRefFromGetDescription() throws Exception {
        String longTooltip = chars(150);
        JLabel label = new JLabel("Warning");
        label.setToolTipText(longTooltip);

        assertEquals(
                "- JLabel (label) \"Warning\" \"" + chars(120) + "\u2026\" [ref=1] actions: get_description",
                snapshot(label));

        assertEquals(longTooltip, getDescription(context.getRefOf(label)));
    }

    @Test
    void panelWithLongDescriptionGetsRefFromGetDescription() throws Exception {
        String longDesc = chars(150);
        JPanel panel = new JPanel();
        panel.getAccessibleContext().setAccessibleDescription(longDesc);

        assertEquals(
                "- JPanel (panel) \"" + chars(120) + "\u2026\" [ref=1] actions: get_description",
                snapshot(panel));
    }

    @Test
    void descriptionExactly120CharsIsNotCapped() throws Exception {
        String desc = chars(120);
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(desc);

        assertEquals(
                "- JButton (push_button) \"OK\" \"" + desc + "\" [ref=1] actions: click",
                snapshot(button));
    }

    @Test
    void descriptionAt121CharsIsCapped() throws Exception {
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(chars(121));

        assertEquals(
                "- JButton (push_button) \"OK\" \"" + chars(120) + "\u2026\" [ref=1] actions: click, get_description",
                snapshot(button));
    }

    @Test
    void buttonWithLongTooltipShowsGetDescriptionAlongsideClick() throws Exception {
        JButton button = new JButton("OK");
        button.setToolTipText(chars(150));

        assertEquals(
                "- JButton (push_button) \"OK\" \"" + chars(120) + "\u2026\" [ref=1] actions: click, get_description",
                snapshot(button));
    }
}
