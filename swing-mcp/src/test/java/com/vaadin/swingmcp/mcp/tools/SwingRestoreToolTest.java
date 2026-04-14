package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class SwingRestoreToolTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingRestoreTool restoreTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        restoreTool = new SwingRestoreTool();
        context = new SwingToolContext();
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void restore(int ref) throws Exception {
        try {
            restoreTool.execute(new Parameters(Map.of("ref", ref)), context);
        } finally {
            context.clearRefMap();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Invalid ref
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> restore(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("swing_snapshot"),
                "Error should suggest calling swing_snapshot, got: " + ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — non-Frame/JDesktopIcon components all return MCP error
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JButton() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(button)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        JCheckBox cb = new JCheckBox("Accept");
        snapshot(cb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(cb)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(field)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(slider)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        snapshot(combo);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(combo)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(list)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        snapshot(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(tp)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(spinner)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        snapshot(pb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(pb)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        JPasswordField field = new JPasswordField("secret");
        snapshot(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> restore(context.getRefOf(field)));
        assertTrue(ex.getMessage().contains("does not support restore"));
    }
}
