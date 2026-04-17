package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.AbstractHeadlessTest;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPServerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SwingCloseToolTest extends AbstractHeadlessTest {

    private SwingSnapshotTool snapshotTool;
    private SwingCloseTool closeTool;
    private SwingToolContext context;

    @BeforeEach
    void setUp() {
        snapshotTool = new SwingSnapshotTool();
        closeTool = new SwingCloseTool();
        context = new SwingToolContext(Runnable::run);
    }

    private void snapshot(Component... roots) throws Exception {
        context.setConsideredComponents(Arrays.asList(roots));
        snapshotTool.execute(new Parameters(Map.of()), context);
    }

    private void close(int ref) throws Exception {
        closeTool.execute(new Parameters(Map.of("ref", ref)), context);
        context.clearRefMap();
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Invalid ref
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void invalidRefReturnsMcpErrorWithRecoveryMessage() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);

        MCPServerException ex = assertThrows(MCPServerException.class, () -> close(999));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("Component with ref 999 does not exist (valid refs: 1\u20131).", ex.getMessage());
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Component matrix — non-window components all return MCP error
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void componentMatrix_JButton() throws Exception {
        JButton button = new JButton("OK");
        snapshot(button);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(button)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JCheckBox() throws Exception {
        JCheckBox cb = new JCheckBox("Accept");
        snapshot(cb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(cb)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JTextField() throws Exception {
        JTextField field = new JTextField("text");
        snapshot(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(field)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JTextArea() throws Exception {
        JTextArea area = new JTextArea("text");
        snapshot(area);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(area)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JSlider() throws Exception {
        JSlider slider = new JSlider(0, 100, 50);
        snapshot(slider);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(slider)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JSpinner() throws Exception {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        snapshot(spinner);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(spinner)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JComboBox() throws Exception {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        snapshot(combo);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(combo)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JProgressBar() throws Exception {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        snapshot(pb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(pb)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JSplitPane() throws Exception {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        snapshot(sp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(sp)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JPasswordField() throws Exception {
        JPasswordField field = new JPasswordField("secret");
        snapshot(field);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(field)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JRadioButton() throws Exception {
        JRadioButton rb = new JRadioButton("Option A");
        snapshot(rb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(rb)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JToggleButton() throws Exception {
        JToggleButton tb = new JToggleButton("Toggle");
        snapshot(tb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(tb)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JPanel() throws Exception {
        JPanel panel = new JPanel();
        panel.add(new JLabel("inside"));
        // JPanel has no actions → no ref in snapshot; force one to test error path
        context.putRef(99, (javax.accessibility.Accessible) panel);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> closeTool.execute(new Parameters(Map.of("ref", 99)), context));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JScrollPane() throws Exception {
        JScrollPane sp = new JScrollPane(new JTextArea("text"));
        // JScrollPane has no actions → no ref in snapshot; force one to test error path
        context.putRef(99, (javax.accessibility.Accessible) sp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> closeTool.execute(new Parameters(Map.of("ref", 99)), context));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JTabbedPane() throws Exception {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        snapshot(tp);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(tp)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JLabel() throws Exception {
        JLabel label = new JLabel("Hello");
        // JLabel has no actions → no ref in snapshot; force one to test error path
        context.putRef(99, (javax.accessibility.Accessible) label);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> closeTool.execute(new Parameters(Map.of("ref", 99)), context));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JMenuBar() throws Exception {
        JMenuBar mb = new JMenuBar();
        mb.add(new JMenu("File"));
        // JMenuBar has no actions → no ref in snapshot; force one to test error path
        context.putRef(99, (javax.accessibility.Accessible) mb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> closeTool.execute(new Parameters(Map.of("ref", 99)), context));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JMenu() throws Exception {
        // DR-012: JMenu has no actions (no ref); register under a test ref to
        // exercise the tool error path.
        JMenu menu = new JMenu("File");
        menu.add(new JMenuItem("Open"));
        context.putRef(99, (javax.accessibility.Accessible) menu);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> closeTool.execute(new Parameters(Map.of("ref", 99)), context));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JMenuItem() throws Exception {
        JMenuItem item = new JMenuItem("Open");
        snapshot(item);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(item)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JToolBar() throws Exception {
        JToolBar tb = new JToolBar();
        tb.add(new JButton("B"));
        // JToolBar has no actions → no ref in snapshot; force one to test error path
        context.putRef(99, (javax.accessibility.Accessible) tb);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> closeTool.execute(new Parameters(Map.of("ref", 99)), context));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JList() throws Exception {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        snapshot(list);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> close(context.getRefOf(list)));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JTree() throws Exception {
        JTree tree = new JTree();
        snapshot(tree);
        // JTree itself has no actions (selection suppressed, not truncated) — no ref
        assertThrows(IllegalStateException.class, () -> context.getRefOf(tree));
    }

    @Test
    void componentMatrix_JOptionPane() throws Exception {
        JOptionPane optionPane = new JOptionPane("Test");
        // JOptionPane has no close action — force a ref to test the error path
        context.putRef(99, (javax.accessibility.Accessible) optionPane);
        MCPErrorResponseException ex = assertThrows(MCPErrorResponseException.class,
                () -> closeTool.execute(new Parameters(Map.of("ref", 99)), context));
        assertTrue(ex.getMessage().contains("does not support swing_close"));
    }

    @Test
    void componentMatrix_JDesktopPane() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        context.putRef(99, desktop);
        assertThrows(MCPErrorResponseException.class, () -> close(99));
    }

    @Test
    void componentMatrix_JInternalFrame() throws Exception {
        JDesktopPane desktop = new JDesktopPane();
        JInternalFrame iframe = new JInternalFrame("Doc", true, true);
        iframe.setSize(150, 80);
        iframe.setVisible(true);
        desktop.add(iframe);
        context.putRef(99, iframe);
        assertThrows(MCPErrorResponseException.class, () -> close(99));
    }
}
