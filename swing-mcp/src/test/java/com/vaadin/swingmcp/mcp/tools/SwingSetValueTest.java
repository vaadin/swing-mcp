package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSetValueTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingSetValueTool setValueTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        setValueTool = new SwingSetValueTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void setValue(int ref, Number value) throws Exception {
        setValueTool.execute(new Parameters(Map.of("ref", ref, "value", value)), context);
        context.clearRefMap();
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Acceptance criteria
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setJSliderValueWithinRange() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        setValue(context.getRefOf(slider), 75.0);
        assertEquals(75, slider.getValue());
    }

    @Test
    void setJSpinnerNumberModelValue() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        setValue(context.getRefOf(spinner), 8.0);
        assertEquals(8, ((Number) spinner.getValue()).intValue());
    }

    @Test
    void setJSplitPaneDividerLocation() throws Exception {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        snapshot(sp);
        int ref = context.getRefOf(sp);
        // Un-laid-out JSplitPane has current=-1 which is below its own min; use min as a safe value
        Number min = sp.getAccessibleContext().getAccessibleValue().getMinimumAccessibleValue();
        assertDoesNotThrow(() -> setValue(ref, min.doubleValue()));
    }

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> setValue(999, 50.0));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    @Test
    void componentWithoutValueSupportReturnsMcpError() throws Exception {
        JButton button = new JButton("Click me");
        snapshot(button);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(context.getRefOf(button), 42.0));
        assertTrue(ex.getMessage().contains("does not support swing_set_value"),
                "Error should mention set_value, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    @Test
    void progressBarReturnsMcpError() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        snapshot(pb);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(context.getRefOf(pb), 75.0));
        assertTrue(ex.getMessage().contains("does not support swing_set_value"));
    }

    @Test
    void disabledComponentReturnsMcpError() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        slider.setEnabled(false);
        snapshot(slider);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(context.getRefOf(slider), 75.0));
        assertTrue(ex.getMessage().toLowerCase().contains("disabled"),
                "Error should mention disabled, got: " + ex.getMessage());
    }

    @Test
    void valueBelowMinReturnsRangeError() throws Exception {
        JSlider slider = new JSlider(10, 100, 50);
        snapshot(slider);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(context.getRefOf(slider), 5.0));
        assertTrue(ex.getMessage().contains("below the minimum"),
                "Error should mention below minimum, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("swing_get_value"),
                "Error should suggest swing_get_value, got: " + ex.getMessage());
    }

    @Test
    void valueAboveMaxReturnsRangeError() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(context.getRefOf(slider), 150.0));
        assertTrue(ex.getMessage().contains("above the maximum"),
                "Error should mention above maximum, got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("swing_get_value"),
                "Error should suggest swing_get_value, got: " + ex.getMessage());
    }

    @Test
    void unboundedSpinnerAcceptsAnyValue() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, null, null, 1));
        snapshot(spinner);
        setValue(context.getRefOf(spinner), 9999.0);
        assertEquals(9999, ((Number) spinner.getValue()).intValue());
    }

    @Test
    void valueExactlyAtMinIsAllowed() throws Exception {
        JSlider slider = new JSlider(10, 100, 50);
        snapshot(slider);
        setValue(context.getRefOf(slider), 10.0);
        assertEquals(10, slider.getValue());
    }

    @Test
    void valueExactlyAtMaxIsAllowed() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        setValue(context.getRefOf(slider), 100.0);
        assertEquals(100, slider.getValue());
    }

    @Test
    void refMapClearedAfterSuccessfulSetValue() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        int ref = context.getRefOf(slider);

        setValue(ref, 75.0);

        // ref map was cleared — next use of same ref fails with INVALID_PARAMS
        MCPServerException ex = assertThrows(MCPServerException.class,
                () -> setValue(ref, 80.0));
        assertTrue(ex.getMessage().contains("swing_snapshot"));
    }

    @Test
    void refMapPreservedAfterFailedSetValueOnDisabledComponent() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        slider.setEnabled(false);
        snapshot(slider);
        int ref = context.getRefOf(slider);

        assertThrows(MCPErrorResponseException.class, () ->
                setValueTool.execute(new Parameters(Map.of("ref", ref, "value", 75.0)), context));

        // Validation error — ref map must still be intact so the AI can retry
        slider.setEnabled(true);
        setValue(ref, 80.0);
        assertEquals(80, slider.getValue());
    }

    @Test
    void returnsEchoOnSuccess() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        int ref = context.getRefOf(slider);
        var result = setValueTool.execute(
                new Parameters(Map.of("ref", ref, "value", 75.0)), context);
        // JSlider uses Integer model → 75.0 is converted to Integer 75 → echoed as "75"
        assertEquals("Dispatched set-value on ref=" + ref + " to 75 — call swing_snapshot to verify the outcome", result.getText());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Type preservation
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void typePreservation_Integer() throws Exception {
        SpinnerNumberModel model = new SpinnerNumberModel(5, 0, 10, 1);
        JSpinner spinner = new JSpinner(model);
        snapshot(spinner);
        setValue(context.getRefOf(spinner), 8.0);
        assertInstanceOf(Integer.class, model.getValue(),
                "Model value should remain Integer, got: " + model.getValue().getClass().getName());
    }

    @Test
    void typePreservation_Double() throws Exception {
        SpinnerNumberModel model = new SpinnerNumberModel(5.0, 0.0, 10.0, 0.5);
        JSpinner spinner = new JSpinner(model);
        snapshot(spinner);
        setValue(context.getRefOf(spinner), 7.5);
        assertInstanceOf(Double.class, model.getValue(),
                "Model value should remain Double, got: " + model.getValue().getClass().getName());
    }

    @Test
    void typePreservation_BigDecimal() throws Exception {
        SpinnerNumberModel model = new SpinnerNumberModel(
                new BigDecimal("5.00"), new BigDecimal("0.00"),
                new BigDecimal("10.00"), new BigDecimal("0.01"));
        JSpinner spinner = new JSpinner(model);
        snapshot(spinner);
        setValue(context.getRefOf(spinner), 7.5);
        assertInstanceOf(BigDecimal.class, model.getValue(),
                "Model value should remain BigDecimal, got: " + model.getValue().getClass().getName());
    }

    @Test
    void fractionalValueOnIntegerModelReturnsError() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(context.getRefOf(spinner), 7.5));
        assertTrue(ex.getMessage().contains("whole number"),
                "Error should mention whole number, got: " + ex.getMessage());
    }

    @Test
    void fractionalValueOnSliderReturnsError() throws Exception {
        // JSlider uses Integer values
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);

        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(context.getRefOf(slider), 42.5));
        assertTrue(ex.getMessage().contains("whole number"),
                "Error should mention whole number, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — supported
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JSlider() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        setValue(context.getRefOf(slider), 75.0);
        assertEquals(75, slider.getValue());
    }

    @Test
    void componentMatrix_JSpinnerNumberModel() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        setValue(context.getRefOf(spinner), 8.0);
        assertEquals(8, ((Number) spinner.getValue()).intValue());
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        snapshot(sp);
        Number min = sp.getAccessibleContext().getAccessibleValue().getMinimumAccessibleValue();
        assertDoesNotThrow(() -> setValue(context.getRefOf(sp), min.doubleValue()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — not supported
    // ══════════════════════════════════════════════════════════════════════════

    private void assertSetValueNotSupported(Component component) throws Exception {
        snapshot(component);
        int ref;
        try {
            ref = context.getRefOf(component);
        } catch (IllegalStateException e) {
            // Component has no ref (no actions) — cannot call set_value, skip
            return;
        }
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(ref, 42.0));
        assertTrue(ex.getMessage().contains("does not support swing_set_value"),
                "Expected set_value not supported for " + component.getClass().getSimpleName()
                        + ", got: " + ex.getMessage());
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertSetValueNotSupported(pb);
    }

    @Test
    void componentMatrix_JSpinnerDateModel() throws Exception {
        assertSetValueNotSupported(new JSpinner(new SpinnerDateModel()));
    }

    @Test
    void componentMatrix_JSpinnerListModel() throws Exception {
        assertSetValueNotSupported(new JSpinner(new SpinnerListModel(new String[]{"A", "B", "C"})));
    }

    @Test
    void componentMatrix_JButton() throws Exception {
        assertSetValueNotSupported(new JButton("Click me"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        assertSetValueNotSupported(new JCheckBox("Check"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        assertSetValueNotSupported(new JRadioButton("Option"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        assertSetValueNotSupported(new JTextField("text"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        assertSetValueNotSupported(new JTextArea("text"));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        assertSetValueNotSupported(new JComboBox<>(new String[]{"A", "B"}));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        assertSetValueNotSupported(new JToggleButton("Toggle"));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        JLabel label = new JLabel("Hello");
        snapshot(label);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(label));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        panel.setName("TestPanel");
        snapshot(panel);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(panel));
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        JScrollPane sp = new JScrollPane(new JTextArea("content"));
        snapshot(sp);
        assertThrows(IllegalStateException.class, () -> context.getRefOf(sp));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        snapshot(tp);
        try {
            int ref = context.getRefOf(tp);
            MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                    () -> setValue(ref, 1.0));
            assertTrue(ex.getMessage().contains("does not support swing_set_value"));
        } catch (IllegalStateException e) {
            // No ref assigned — acceptable
        }
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        // D_jmenu_not_clickable: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(99, 42.0));
        assertTrue(ex.getMessage().contains("does not support swing_set_value"));
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // D_jmenu_not_clickable: JMenu has no ref; register under a test ref to exercise the tool error path.
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        mb.add(menu);
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(99, 42.0));
        assertTrue(ex.getMessage().contains("does not support swing_set_value"));
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuBar mb = new JMenuBar();
        JMenu menu = new JMenu("File");
        JMenuItem item = new JMenuItem("Open");
        menu.add(item);
        mb.add(menu);
        snapshot(mb);
        int ref = context.getRefOf(item);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(ref, 42.0));
        assertTrue(ex.getMessage().contains("does not support swing_set_value"));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        JButton button = new JButton("Tool");
        tb.add(button);
        snapshot(tb);
        int ref = context.getRefOf(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(ref, 42.0));
        assertTrue(ex.getMessage().contains("does not support swing_set_value"));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        int ref = context.getRefOf(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(ref, 42.0));
        assertTrue(ex.getMessage().contains("does not support swing_set_value"));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> setValue(99, 42.0));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> setValue(99, 42.0));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // MCP client smoke test
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void swingSetValueViaMcpClient() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        mcpServer.setConsideredComponents(List.of(slider));

        mcpClient.callTool(new McpSchema.CallToolRequest("swing_snapshot", Map.of()));

        McpSchema.CallToolResult result = mcpClient.callTool(
                new McpSchema.CallToolRequest("swing_set_value", Map.of("ref", 1, "value", 75)));
        SwingUtilities.invokeAndWait(() -> {}); // drain EDT so fire-and-forget action has run

        assertNotEquals(Boolean.TRUE, result.isError(), "set_value should succeed");
        assertEquals(75, slider.getValue());
    }
}
