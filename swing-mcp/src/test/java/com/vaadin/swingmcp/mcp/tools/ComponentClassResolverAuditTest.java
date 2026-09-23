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
 * Pins the set of {@code java.desktop} classes (outside {@code javax.swing.plaf}) that
 * {@link ComponentClassResolver#isQualifying} accepts. A JDK that adds, renames or removes a
 * widget fails here with the diff to apply to {@link #EXPECTED_QUALIFYING}; so does an
 * unintended change to the predicate, which is a regression to investigate instead.
 */
class ComponentClassResolverAuditTest {

    @Test
    void qualifyingAncestorsMatchFixture() throws IOException {
        Set<String> actual = enumerateQualifying();

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
        return rel.substring(0, rel.length() - ".class".length()).replace('/', '.');
    }

    /**
     * A rough pre-filter so {@link Class#forName} runs only on plausible names;
     * {@link ComponentClassResolver#isQualifying} decides.
     */
    private static boolean isCandidatePackage(String className) {
        // isQualifying rejects nested classes anyway.
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
            // Some classes reference optional native types and fail to load; none is a widget.
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Fixture
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * The set observed on JDK 21. On a failure, apply the diagnostic diff one class at a
     * time, deliberately; never blindly regenerate it.
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
