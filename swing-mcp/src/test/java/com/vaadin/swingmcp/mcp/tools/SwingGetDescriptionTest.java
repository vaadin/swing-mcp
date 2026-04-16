package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
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
        context = new SwingToolContext();
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

    /** A long description that exceeds the 120-char snapshot cap. */
    private static String longDescription() {
        return "This is a very long description that exceeds the 120-character limit " +
               "set in the snapshot for description capping. It continues well beyond " +
               "that boundary to test the get_description tool.";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Tool tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void longDescriptionReturnedInFull() throws Exception {
        String desc = longDescription();
        assertTrue(desc.length() > 120, "Test setup: description must exceed 120 chars");

        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(desc);
        snapshot(button);

        String result = getDescription(context.getRefOf(button));
        assertEquals(desc, result);
    }

    @Test
    void shortDescriptionReturnedAsIs() throws Exception {
        String desc = "Short tooltip";
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(desc);
        snapshot(button);

        String result = getDescription(context.getRefOf(button));
        assertEquals(desc, result);
    }

    @Test
    void noDescriptionReturnsEmptyString() throws Exception {
        JButton button = new JButton("OK");
        // No description, no tooltip
        snapshot(button);

        String result = getDescription(context.getRefOf(button));
        assertEquals("", result);
    }

    @Test
    void invalidRefReturnsError() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> getDescription(9999));
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot");
    }

    @Test
    void refMapPreservedAfterCall() throws Exception {
        String desc = "Some description";
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(desc);
        snapshot(button);

        int ref = context.getRefOf(button);
        assertEquals(desc, getDescription(ref));
        // Second call with same ref should still work
        assertEquals(desc, getDescription(ref));
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

        String result = getDescription(context.getRefOf(button));
        assertTrue(result.startsWith(chars(1000)));
        assertTrue(result.contains("(truncated, 1100 total characters)"));
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
        button.setToolTipText("<html><b>Bold</b> text &amp; more <i>italic</i> content that goes on and on " +
                "to make this tooltip exceed the 120 character limit in the snapshot description slot</html>");
        snapshot(button);

        String result = getDescription(context.getRefOf(button));
        // HTML tags should be stripped, entities decoded
        assertFalse(result.contains("<b>"));
        assertFalse(result.contains("&amp;"));
        assertTrue(result.contains("Bold"));
        assertTrue(result.contains("text & more"));
    }

    @Test
    void tooltipFallbackReturnsFullTooltip() throws Exception {
        String longTooltip = "This is a tooltip that exceeds 120 characters. " +
                "It provides detailed help about the button functionality including " +
                "edge cases and usage instructions for the user.";
        assertTrue(longTooltip.length() > 120);

        JButton button = new JButton("OK");
        // No accessibleDescription set — tooltip is the fallback
        button.setToolTipText(longTooltip);
        snapshot(button);

        assertEquals(longTooltip, getDescription(context.getRefOf(button)));
    }

    @Test
    void buttonWithLongTooltipHasGetDescriptionInActions() throws Exception {
        String longTooltip = "This is a tooltip that exceeds 120 characters. " +
                "It provides detailed help about the button functionality including " +
                "edge cases and usage instructions for the user.";
        JButton button = new JButton("OK");
        button.setToolTipText(longTooltip);

        String output = snapshot(button);
        assertTrue(output.contains("get_description"), "Actions should include get_description: " + output);
        assertTrue(output.contains("click"), "Existing actions should be preserved: " + output);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Snapshot integration tests
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void cappedDescriptionShowsGetDescriptionInActions() throws Exception {
        String desc = longDescription();
        JButton button = new JButton("Save");
        button.getAccessibleContext().setAccessibleDescription(desc);

        String output = snapshot(button);
        assertTrue(output.contains("get_description"),
                "Capped description should advertise get_description: " + output);
        // Description should be capped with ellipsis
        assertTrue(output.contains("\u2026"),
                "Description should be capped with ellipsis: " + output);
    }

    @Test
    void shortDescriptionDoesNotShowGetDescription() throws Exception {
        JButton button = new JButton("Save");
        button.getAccessibleContext().setAccessibleDescription("Short help");

        String output = snapshot(button);
        assertFalse(output.contains("get_description"),
                "Short description should not advertise get_description: " + output);
    }

    @Test
    void labelWithLongTooltipGetsRefFromGetDescription() throws Exception {
        JLabel label = new JLabel("Warning");
        String longTooltip = "This is a very long warning message that exceeds 120 characters. " +
                "It explains in detail what the user should be aware of before proceeding " +
                "with the potentially dangerous operation.";
        label.setToolTipText(longTooltip);

        String output = snapshot(label);
        // Label should get a ref solely from get_description
        assertTrue(output.contains("ref="),
                "Label with capped description should get a ref: " + output);
        assertTrue(output.contains("get_description"),
                "Label should advertise get_description: " + output);

        // Verify we can actually call the tool on the label's ref
        int ref = context.getRefOf(label);
        assertEquals(longTooltip, getDescription(ref));
    }

    @Test
    void panelWithLongDescriptionGetsRefFromGetDescription() throws Exception {
        JPanel panel = new JPanel();
        String longDesc = "This panel contains all the configuration settings for the advanced mode. " +
                "Be careful when changing these values as they affect the entire system " +
                "behavior and cannot be undone easily.";
        panel.getAccessibleContext().setAccessibleDescription(longDesc);

        String output = snapshot(panel);
        assertTrue(output.contains("ref="),
                "Panel with capped description should get a ref: " + output);
        assertTrue(output.contains("get_description"),
                "Panel should advertise get_description: " + output);
    }

    @Test
    void descriptionExactly120CharsIsNotCapped() throws Exception {
        String desc = chars(120);
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(desc);

        String output = snapshot(button);
        assertFalse(output.contains("get_description"),
                "Description at exactly 120 chars should not be capped: " + output);
        assertFalse(output.contains("\u2026"),
                "No ellipsis expected at exactly 120 chars: " + output);
    }

    @Test
    void descriptionAt121CharsIsCapped() throws Exception {
        String desc = chars(121);
        JButton button = new JButton("OK");
        button.getAccessibleContext().setAccessibleDescription(desc);

        String output = snapshot(button);
        assertTrue(output.contains("get_description"),
                "Description at 121 chars should trigger get_description: " + output);
        assertTrue(output.contains("\u2026"),
                "Description at 121 chars should be capped with ellipsis: " + output);
    }
}
