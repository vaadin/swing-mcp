package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingSetValueTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingSetValueScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingSetValueTool setValueTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        setValueTool = new SwingSetValueTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private void setValue(int ref, Number value) throws Exception {
        executeOnEDT(() -> {
            setValueTool.execute(new Parameters(Map.of("ref", ref, "value", value)), context);
            return null;
        });
        // drain EDT so fire-and-forget action has run
        SwingUtilities.invokeAndWait(() -> {});
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setSliderValueInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JSlider slider = new JSlider(0, 100, 50);
        frame.getContentPane().add(slider);

        snapshot(frame);
        setValue(context.getRefOf(slider), 75.0);
        assertEquals(75, slider.getValue());
    }

    @Test
    void setSpinnerValueInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        frame.getContentPane().add(spinner);

        snapshot(frame);
        setValue(context.getRefOf(spinner), 8.0);
        assertEquals(8, ((Number) spinner.getValue()).intValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setSliderValueInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JSlider slider = new JSlider(0, 50, 25);
        dialog.getContentPane().add(slider);

        snapshot(dialog);
        setValue(context.getRefOf(slider), 40.0);
        assertEquals(40, slider.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void setSliderValueInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JSlider slider = new JSlider(0, 100, 50);
        iframe.getContentPane().add(slider);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        setValue(context.getRefOf(slider), 75.0);
        assertEquals(75, slider.getValue());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Error cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledSliderInsideJFrameReturnsMcpError() throws Exception {
        JFrame frame = new JFrame("Test");
        JSlider slider = new JSlider(0, 100, 50);
        slider.setEnabled(false);
        frame.getContentPane().add(slider);

        snapshot(frame);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> setValue(context.getRefOf(slider), 75.0));
        assertTrue(ex.getMessage().toLowerCase().contains("disabled"));
    }
}
