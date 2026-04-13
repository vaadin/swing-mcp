package com.vaadin.swingmcp.mcp.tools;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JDK-drift audit for {@link ComponentClassResolver#isQualifying} per
 * UC-002 BR-11. Enumerates every class in the {@code java.desktop} JDK
 * module whose package is {@code javax.swing}, a subpackage of
 * {@code javax.swing} other than {@code javax.swing.plaf} and its
 * subpackages, or {@code java.awt}. Applies {@code isQualifying} and
 * asserts the resulting set equals the checked-in fixture below.
 *
 * <p>This test fails when:</p>
 * <ul>
 *   <li>a future JDK adds a new qualifying class (e.g. a new {@code Jxxx}
 *       widget or AWT Component) — update {@link #EXPECTED_QUALIFYING} to
 *       include it;</li>
 *   <li>a JDK renames or removes a qualifying class — update the fixture
 *       to reflect the new reality;</li>
 *   <li>the {@code isQualifying} predicate changes its behaviour
 *       unintentionally — investigate the regression.</li>
 * </ul>
 *
 * <p>Failure messages surface the symmetric diff of actual-vs-expected so
 * the update is straightforward.</p>
 */
class ComponentClassResolverAuditTest {

    @Test
    void qualifyingAncestorsMatchFixture() throws IOException {
        Set<String> actual = enumerateQualifying();

        // Symmetric diff helps pinpoint which side changed.
        Set<String> extraInActual = new TreeSet<>(actual);
        extraInActual.removeAll(EXPECTED_QUALIFYING);
        Set<String> extraInExpected = new TreeSet<>(EXPECTED_QUALIFYING);
        extraInExpected.removeAll(actual);

        String diagnostic = "qualifying-ancestor set drift detected.\n"
                + "  in actual but not expected (add to fixture): " + extraInActual + "\n"
                + "  in expected but not actual (remove from fixture): " + extraInExpected;
        assertEquals(EXPECTED_QUALIFYING, actual, diagnostic);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Enumeration via the jrt:/ filesystem (no extra test dependencies)
    // ══════════════════════════════════════════════════════════════════════════

    private static Set<String> enumerateQualifying() throws IOException {
        Set<String> result = new TreeSet<>();
        FileSystem jrt = FileSystems.getFileSystem(URI.create("jrt:/"));
        Path moduleRoot = jrt.getPath("modules", "java.desktop");

        try (Stream<Path> stream = Files.walk(moduleRoot)) {
            stream.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> toClassName(moduleRoot, p))
                    .filter(ComponentClassResolverAuditTest::isCandidatePackage)
                    .forEach(name -> tryQualify(name, result));
        }
        return result;
    }

    private static String toClassName(Path root, Path classFile) {
        String rel = root.relativize(classFile).toString();
        // Strip trailing ".class" and convert path separators to dots.
        return rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
    }

    /**
     * Rough pre-filter so we only attempt {@link Class#forName} on names
     * that could plausibly be qualifying. Exact qualification is decided by
     * {@link ComponentClassResolver#isQualifying}.
     */
    private static boolean isCandidatePackage(String className) {
        // Skip module-info, package-info, and inner/nested-class forms —
        // isQualifying rejects nested classes anyway, and they can't be
        // loaded via forName without the enclosing class syntax.
        if (className.contains("$")) return false;
        if (className.endsWith("module-info") || className.endsWith("package-info")) return false;

        if (className.startsWith("javax.swing.plaf.") || className.equals("javax.swing.plaf")) {
            return false;
        }
        return className.equals("javax.swing")
                || className.startsWith("javax.swing.")
                || className.startsWith("java.awt.")
                || className.equals("java.awt");
    }

    private static void tryQualify(String className, Set<String> result) {
        try {
            Class<?> c = Class.forName(className, false,
                    ComponentClassResolverAuditTest.class.getClassLoader());
            if (ComponentClassResolver.isQualifying(c)) {
                result.add(className);
            }
        } catch (Throwable ignored) {
            // Some classes reference optional native types that may not be
            // loadable during class resolution — skip them. They're not
            // qualifying widgets anyway.
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Fixture — populated from the initial run against the development JDK
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Frozen fixture — qualifying-ancestor set observed on JDK 21
     * (initial population 2026-04-13). If this test fails, consult the
     * diagnostic diff: add newly-surfaced JDK classes or remove removed
     * ones deliberately. Do not blindly regenerate.
     */
    private static final Set<String> EXPECTED_QUALIFYING = new TreeSet<>(Set.of(
            "java.awt.Button",
            "java.awt.Canvas",
            "java.awt.Checkbox",
            "java.awt.CheckboxMenuItem",
            "java.awt.Choice",
            "java.awt.Component",
            "java.awt.Container",
            "java.awt.Dialog",
            "java.awt.FileDialog",
            "java.awt.Frame",
            "java.awt.Label",
            "java.awt.List",
            "java.awt.Menu",
            "java.awt.MenuBar",
            "java.awt.MenuComponent",
            "java.awt.MenuItem",
            "java.awt.Panel",
            "java.awt.PopupMenu",
            "java.awt.ScrollPane",
            "java.awt.Scrollbar",
            "java.awt.TextArea",
            "java.awt.TextComponent",
            "java.awt.TextField",
            "java.awt.Window",
            "javax.swing.AbstractButton",
            "javax.swing.Box",
            "javax.swing.CellRendererPane",
            "javax.swing.DefaultListCellRenderer",
            "javax.swing.JApplet",
            "javax.swing.JButton",
            "javax.swing.JCheckBox",
            "javax.swing.JCheckBoxMenuItem",
            "javax.swing.JColorChooser",
            "javax.swing.JComboBox",
            "javax.swing.JComponent",
            "javax.swing.JDesktopPane",
            "javax.swing.JDialog",
            "javax.swing.JEditorPane",
            "javax.swing.JFileChooser",
            "javax.swing.JFormattedTextField",
            "javax.swing.JFrame",
            "javax.swing.JInternalFrame",
            "javax.swing.JLabel",
            "javax.swing.JLayer",
            "javax.swing.JLayeredPane",
            "javax.swing.JList",
            "javax.swing.JMenu",
            "javax.swing.JMenuBar",
            "javax.swing.JMenuItem",
            "javax.swing.JOptionPane",
            "javax.swing.JPanel",
            "javax.swing.JPasswordField",
            "javax.swing.JPopupMenu",
            "javax.swing.JProgressBar",
            "javax.swing.JRadioButton",
            "javax.swing.JRadioButtonMenuItem",
            "javax.swing.JRootPane",
            "javax.swing.JScrollBar",
            "javax.swing.JScrollPane",
            "javax.swing.JSeparator",
            "javax.swing.JSlider",
            "javax.swing.JSpinner",
            "javax.swing.JSplitPane",
            "javax.swing.JTabbedPane",
            "javax.swing.JTable",
            "javax.swing.JTextArea",
            "javax.swing.JTextField",
            "javax.swing.JTextPane",
            "javax.swing.JToggleButton",
            "javax.swing.JToolBar",
            "javax.swing.JToolTip",
            "javax.swing.JTree",
            "javax.swing.JViewport",
            "javax.swing.JWindow",
            "javax.swing.colorchooser.AbstractColorChooserPanel",
            "javax.swing.table.DefaultTableCellRenderer",
            "javax.swing.table.JTableHeader",
            "javax.swing.text.JTextComponent",
            "javax.swing.tree.DefaultTreeCellRenderer"
    ));
}
