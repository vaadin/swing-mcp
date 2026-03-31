package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#supportsClick(Accessible)}.
 * Covers every component in the verification component matrix.
 */
class SwingUtilsSupportsClickTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_supportsClick() {
        JButton button = new JButton("OK");
        assertTrue(SwingUtils.supportsClick(button) >= 0);
    }

    @Test
    void jTextField_doesNotSupportClick() {
        JTextField field = new JTextField("text");
        assertEquals(-1, SwingUtils.supportsClick(field));
    }

    @Test
    void jPasswordField_doesNotSupportClick() {
        JPasswordField field = new JPasswordField("secret");
        assertEquals(-1, SwingUtils.supportsClick(field));
    }

    @Test
    void jTextArea_doesNotSupportClick() {
        JTextArea area = new JTextArea("text");
        assertEquals(-1, SwingUtils.supportsClick(area));
    }

    @Test
    void jCheckBox_supportsClick() {
        JCheckBox cb = new JCheckBox("Check");
        assertTrue(SwingUtils.supportsClick(cb) >= 0);
    }

    @Test
    void jRadioButton_supportsClick() {
        JRadioButton rb = new JRadioButton("Option");
        ButtonGroup group = new ButtonGroup();
        group.add(rb);
        assertTrue(SwingUtils.supportsClick(rb) >= 0);
    }

    @Test
    void jComboBox_doesNotSupportClick() {
        JComboBox<String> combo = new JComboBox<>(new String[]{"A", "B"});
        assertEquals(-1, SwingUtils.supportsClick(combo));
    }

    @Test
    void jToggleButton_supportsClick() {
        JToggleButton tb = new JToggleButton("Toggle");
        assertTrue(SwingUtils.supportsClick(tb) >= 0);
    }

    @Test
    void jSpinner_doesNotSupportClick() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(5, 0, 10, 1));
        assertEquals(-1, SwingUtils.supportsClick(spinner));
    }

    @Test
    void jSlider_doesNotSupportClick() {
        JSlider slider = new JSlider(0, 100, 50);
        assertEquals(-1, SwingUtils.supportsClick(slider));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportClick() {
        JPanel panel = new JPanel();
        assertEquals(-1, SwingUtils.supportsClick(panel));
    }

    @Test
    void jScrollPane_doesNotSupportClick() {
        JScrollPane sp = new JScrollPane(new JTextArea("content"));
        assertEquals(-1, SwingUtils.supportsClick(sp));
    }

    @Test
    void jTabbedPane_doesNotSupportClick() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        tp.addTab("Tab2", new JPanel());
        assertEquals(-1, SwingUtils.supportsClick(tp));
    }

    @Test
    void jSplitPane_doesNotSupportClick() {
        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel());
        assertEquals(-1, SwingUtils.supportsClick(sp));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportClick() {
        JLabel label = new JLabel("Hello");
        assertEquals(-1, SwingUtils.supportsClick(label));
    }

    @Test
    void jProgressBar_doesNotSupportClick() {
        JProgressBar pb = new JProgressBar(0, 100);
        pb.setValue(50);
        assertEquals(-1, SwingUtils.supportsClick(pb));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportClick() {
        JMenuBar mb = new JMenuBar();
        assertEquals(-1, SwingUtils.supportsClick(mb));
    }

    @Test
    void jMenu_supportsClick() {
        JMenu menu = new JMenu("File");
        assertTrue(SwingUtils.supportsClick(menu) >= 0);
    }

    @Test
    void jMenuItem_supportsClick() {
        JMenuItem item = new JMenuItem("Open");
        assertTrue(SwingUtils.supportsClick(item) >= 0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportClick() {
        JToolBar tb = new JToolBar();
        assertEquals(-1, SwingUtils.supportsClick(tb));
    }

    @Test
    void jList_doesNotSupportClick() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        assertEquals(-1, SwingUtils.supportsClick(list));
    }

    @Test
    void jList_childAccessible_supportsClick() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        AccessibleContext ac = list.getAccessibleContext();
        Accessible child = ac.getAccessibleChild(0);
        assertNotNull(child, "JList should have virtual child accessibles");
        assertTrue(SwingUtils.supportsClick(child) >= 0,
                "JList virtual child should support click");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void disabledButton_stillReportsClickSupport() {
        JButton button = new JButton("Disabled");
        button.setEnabled(false);
        // supportsClick checks capability, not state
        assertTrue(SwingUtils.supportsClick(button) >= 0);
    }

    @Test
    void returnedIndexIsConsistentAcrossCalls() {
        JButton button = new JButton("OK");
        int first = SwingUtils.supportsClick(button);
        int second = SwingUtils.supportsClick(button);
        assertEquals(first, second);
    }
}
