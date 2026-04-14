package com.vaadin.swingmcp.mcpscreen.tools;

import com.vaadin.swingmcp.mcp.tools.Parameters;
import com.vaadin.swingmcp.mcp.tools.SwingGetValueTool;
import com.vaadin.swingmcp.mcp.tools.SwingSnapshotTool;
import com.vaadin.swingmcp.mcp.tools.SwingToolContext;
import com.vaadin.swingmcp.mcpscreen.AbstractScreenTest;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingGetValueScreenTest extends AbstractScreenTest {

    private SwingSnapshotTool snapshotTool;
    private SwingGetValueTool getValueTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        getValueTool = new SwingGetValueTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        executeOnEDT(() -> snapshotTool.execute(new Parameters(Map.of()), context));
    }

    private String getValue(int ref) throws Exception {
        MCPProtocol.Content result = executeOnEDT(() -> getValueTool.execute(new Parameters(Map.of("ref", ref)), context));
        return result == null ? null : result.getText();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readSliderInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JSlider slider = new JSlider(0, 100, 42);
        frame.getContentPane().add(slider);

        snapshot(frame);
        String json = getValue(context.getRefOf(slider));
        assertEquals("{\"current\":42,\"min\":0,\"max\":100}", json);
    }

    @Test
    void readSpinnerInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        frame.getContentPane().add(spinner);

        snapshot(frame);
        String json = getValue(context.getRefOf(spinner));
        assertEquals("{\"current\":5,\"min\":0,\"max\":10}", json);
    }

    @Test
    void readProgressBarInsideJFrame() throws Exception {
        JFrame frame = new JFrame("Test");
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(75);
        frame.getContentPane().add(pb);

        snapshot(frame);
        String json = getValue(context.getRefOf(pb));
        assertEquals("{\"current\":75,\"min\":0,\"max\":100}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JDialog
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readSliderInsideJDialog() throws Exception {
        JDialog dialog = new JDialog();
        dialog.setTitle("Test");
        JSlider slider = new JSlider(0, 50, 25);
        dialog.getContentPane().add(slider);

        snapshot(dialog);
        String json = getValue(context.getRefOf(slider));
        assertEquals("{\"current\":25,\"min\":0,\"max\":50}", json);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JInternalFrame
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readSliderInsideJInternalFrame() throws Exception {
        JFrame host = new JFrame("Host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        JSlider slider = new JSlider(0, 100, 42);
        iframe.getContentPane().add(slider);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);

        snapshot(host);
        String json = getValue(context.getRefOf(slider));
        assertEquals("{\"current\":42,\"min\":0,\"max\":100}", json);
    }
}
