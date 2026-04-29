package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.util.Arrays;
import java.util.Map;
import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

class SwingIconifyToolTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingIconifyTool iconifyTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        iconifyTool = new SwingIconifyTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void iconify(int ref) throws Exception {
        iconifyTool.execute(new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Invalid ref
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> iconify(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — non-Frame/JInternalFrame components all return MCP error
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JButton() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(button)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        JCheckBox cb = new JCheckBox("Accept");
        snapshot(cb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(cb)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(field)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(slider)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        snapshot(combo);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(combo)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(list)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        snapshot(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(tp)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(spinner)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        snapshot(pb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(pb)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        JPasswordField field = new JPasswordField("secret");
        snapshot(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> iconify(context.getRefOf(field)));
        assertTrue(ex.getMessage().contains("does not support swing_iconify"));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> iconify(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> iconify(99));
    }
}
