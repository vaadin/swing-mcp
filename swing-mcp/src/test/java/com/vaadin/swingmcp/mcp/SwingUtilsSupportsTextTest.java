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
package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SwingUtils#supportsGetText(Accessible)} and {@link SwingUtils#supportsSetText(Accessible)}
 * across the component matrix, plus {@link SwingUtils#hasPasswordRole} and
 * {@link SwingUtils#readText}.
 */
class SwingUtilsSupportsTextTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JButton("OK")));
    }

    @Test
    void jTextField_supportsGetText() {
        assertTrue(SwingUtils.supportsGetText(new JTextField("text")));
    }

    @Test
    void jPasswordField_doesNotSupportGetText() {
        // D_password_not_readable: reading yields echo chars, not the content.
        assertFalse(SwingUtils.supportsGetText(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_supportsGetText() {
        assertTrue(SwingUtils.supportsGetText(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportGetText() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertFalse(SwingUtils.supportsGetText(rb));
    }

    @Test
    void jComboBox_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JComboBox<>(new String[]{"A", "B"})));
    }

    @Test
    void jToggleButton_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JToggleButton("Toggle")));
    }

    @Test
    void jSpinner_supportsGetText() {
        // JSpinner's editor exposes AccessibleText
        assertTrue(SwingUtils.supportsGetText(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))));
    }

    @Test
    void jSlider_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JSlider(0, 100, 50)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_doesNotSupportGetText() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertFalse(SwingUtils.supportsGetText(tp));
    }

    @Test
    void jSplitPane_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JLabel("Hello")));
    }

    @Test
    void htmlJLabel_doesNotSupportGetText_dr015() {
        // An HTML JLabel does expose AccessibleText (AccessibleHTMLTextSupport); the
        // LABEL role still wins.
        JLabel html = new JLabel("<html>Hello <b>world</b></html>");
        assertFalse(SwingUtils.supportsGetText(html),
                "HTML JLabel must not support get_text (D_label_not_readable)");
    }

    @Test
    void customLabelRoleComponent_doesNotSupportGetText_dr015() {
        // D_label_not_readable: a LABEL-role accessible is excluded even when it exposes AccessibleText.
        JLabel custom = new JLabel("x") {
            @Override
            public javax.accessibility.AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJLabel() {
                        @Override
                        public javax.accessibility.AccessibleRole getAccessibleRole() {
                            return javax.accessibility.AccessibleRole.LABEL;
                        }

                        @Override
                        public javax.accessibility.AccessibleText getAccessibleText() {
                            return new javax.accessibility.AccessibleText() {
                                @Override public int getIndexAtPoint(java.awt.Point p) { return -1; }
                                @Override public java.awt.Rectangle getCharacterBounds(int i) { return null; }
                                @Override public int getCharCount() { return 1; }
                                @Override public int getCaretPosition() { return 0; }
                                @Override public String getAtIndex(int part, int index) { return "x"; }
                                @Override public String getAfterIndex(int part, int index) { return ""; }
                                @Override public String getBeforeIndex(int part, int index) { return ""; }
                                @Override public javax.swing.text.AttributeSet getCharacterAttribute(int i) { return null; }
                                @Override public int getSelectionStart() { return 0; }
                                @Override public int getSelectionEnd() { return 0; }
                                @Override public String getSelectedText() { return null; }
                            };
                        }
                    };
                }
                return accessibleContext;
            }
        };
        assertFalse(SwingUtils.supportsGetText(custom),
                "Custom LABEL-role component must not support get_text (D_label_not_readable)");
    }

    @Test
    void jProgressBar_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JProgressBar(0, 100)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JMenuBar()));
    }

    @Test
    void jMenu_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JMenu("File")));
    }

    @Test
    void jMenuItem_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JToolBar()));
    }

    @Test
    void jList_doesNotSupportGetText() {
        assertFalse(SwingUtils.supportsGetText(new JList<>(new String[]{"A", "B"})));
    }

    @Test
    void jTree_doesNotSupportGetText() {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertFalse(SwingUtils.supportsGetText(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Interactive / Form inputs — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jButton_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JButton("OK")));
    }

    @Test
    void jTextField_supportsSetText() {
        assertTrue(SwingUtils.supportsSetText(new JTextField("text")));
    }

    @Test
    void jPasswordField_supportsSetText() {
        assertTrue(SwingUtils.supportsSetText(new JPasswordField("secret")));
    }

    @Test
    void jTextArea_supportsSetText() {
        assertTrue(SwingUtils.supportsSetText(new JTextArea("text")));
    }

    @Test
    void jCheckBox_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JCheckBox("Check")));
    }

    @Test
    void jRadioButton_doesNotSupportSetText() {
        JRadioButton rb = new JRadioButton("Option");
        new ButtonGroup().add(rb);
        assertFalse(SwingUtils.supportsSetText(rb));
    }

    @Test
    void jComboBox_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JComboBox<>(new String[]{"A", "B"})));
    }

    @Test
    void jToggleButton_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JToggleButton("Toggle")));
    }

    @Test
    void jSpinner_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JSpinner(new SpinnerNumberModel(5, 0, 10, 1))));
    }

    @Test
    void jSlider_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JSlider(0, 100, 50)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Containers / structural — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPanel_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JPanel()));
    }

    @Test
    void jScrollPane_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JScrollPane(new JTextArea("content"))));
    }

    @Test
    void jTabbedPane_doesNotSupportSetText() {
        JTabbedPane tp = new JTabbedPane();
        tp.addTab("Tab1", new JPanel());
        assertFalse(SwingUtils.supportsSetText(tp));
    }

    @Test
    void jSplitPane_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JPanel(), new JPanel())));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Display — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jLabel_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JLabel("Hello")));
    }

    @Test
    void jProgressBar_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JProgressBar(0, 100)));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Menus — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jMenuBar_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JMenuBar()));
    }

    @Test
    void jMenu_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JMenu("File")));
    }

    @Test
    void jMenuItem_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JMenuItem("Open")));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Other — setText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jToolBar_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JToolBar()));
    }

    @Test
    void jList_doesNotSupportSetText() {
        assertFalse(SwingUtils.supportsSetText(new JList<>(new String[]{"A", "B"})));
    }

    @Test
    void jTree_doesNotSupportSetText() {
        JTree tree = new JTree(new javax.swing.tree.DefaultMutableTreeNode("Root"));
        assertFalse(SwingUtils.supportsSetText(tree));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // get_text and set_text are decoupled capabilities
    // ══════════════════════════════════════════════════════════════════════════
    //
    // AccessibleEditableText extends AccessibleText, yet supportsSetText does not imply
    // supportsGetText: a password field is writable but not readable (D_password_not_readable).

    @Test
    void jTextField_supportsBothGetAndSetText() {
        JTextField field = new JTextField("text");
        assertTrue(SwingUtils.supportsSetText(field));
        assertTrue(SwingUtils.supportsGetText(field));
    }

    @Test
    void jTextArea_supportsBothGetAndSetText() {
        JTextArea area = new JTextArea("text");
        assertTrue(SwingUtils.supportsSetText(area));
        assertTrue(SwingUtils.supportsGetText(area));
    }

    @Test
    void jPasswordField_supportsSetTextWithoutSupportsGetText() {
        JPasswordField field = new JPasswordField("secret");
        assertTrue(SwingUtils.supportsSetText(field),
                "Password field must remain writable for login-form filling");
        assertFalse(SwingUtils.supportsGetText(field),
                "Password field must not be readable (D_password_not_readable)");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JList virtual child — getText
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jList_virtualChild_doesNotSupportGetText() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        AccessibleContext ac = list.getAccessibleContext();
        Accessible child = ac.getAccessibleChild(0);
        assertNotNull(child);
        assertFalse(SwingUtils.supportsGetText(child));
    }

    @Test
    void jList_virtualChild_doesNotSupportSetText() {
        JList<String> list = new JList<>(new String[]{"A", "B", "C"});
        AccessibleContext ac = list.getAccessibleContext();
        Accessible child = ac.getAccessibleChild(0);
        assertNotNull(child);
        assertFalse(SwingUtils.supportsSetText(child));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // hasPasswordRole (D_password_not_readable)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jPasswordField_hasPasswordRole() {
        assertTrue(SwingUtils.hasPasswordRole(new JPasswordField("secret")));
    }

    @Test
    void jTextField_doesNotHavePasswordRole() {
        assertFalse(SwingUtils.hasPasswordRole(new JTextField("text")));
    }

    @Test
    void jTextArea_doesNotHavePasswordRole() {
        assertFalse(SwingUtils.hasPasswordRole(new JTextArea("text")));
    }

    @Test
    void jButton_doesNotHavePasswordRole() {
        assertFalse(SwingUtils.hasPasswordRole(new JButton("OK")));
    }

    @Test
    void customComponentWithPasswordRole_hasPasswordRole() {
        // D_password_not_readable gates on the PASSWORD_TEXT role, not the JPasswordField class.
        JTextField fake = new JTextField("secret") {
            @Override
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJTextField() {
                        @Override
                        public javax.accessibility.AccessibleRole getAccessibleRole() {
                            return javax.accessibility.AccessibleRole.PASSWORD_TEXT;
                        }
                    };
                }
                return accessibleContext;
            }
        };
        assertTrue(SwingUtils.hasPasswordRole(fake));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // D_inline_value_preview — readText helper contract
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void readText_withContent_returnsString() {
        assertEquals("hello", SwingUtils.readText(new JTextField("hello"), 1000));
    }

    @Test
    void readText_appliesMaxCharsCap() {
        assertEquals("hello", SwingUtils.readText(new JTextField("hello world"), 5));
    }

    @Test
    void readText_emptyField_returnsEmptyString() {
        // "" means the field exists and is empty; never null.
        assertEquals("", SwingUtils.readText(new JTextField(), 1000));
    }

    @Test
    void readText_componentWithoutAccessibleText_returnsEmptyString() {
        // "" rather than a throw or null, so a caller needs no null-check.
        assertEquals("", SwingUtils.readText(new JButton("Save"), 1000));
    }

    @Test
    void readText_customAccessibleTextReturningNullContent_normalizedToEmpty() {
        // The inline preview relies on a null getTextRange being normalised to "".
        JTextField fake = new JTextField("anything") {
            @Override
            public AccessibleContext getAccessibleContext() {
                if (accessibleContext == null) {
                    accessibleContext = new AccessibleJTextField() {
                        @Override
                        public javax.accessibility.AccessibleEditableText getAccessibleEditableText() {
                            return new javax.accessibility.AccessibleEditableText() {
                                @Override
                                public String getTextRange(int start, int end) { return null; }
                                @Override
                                public void setTextContents(String s) {}
                                @Override
                                public void insertTextAtIndex(int i, String s) {}
                                @Override
                                public void delete(int s, int e) {}
                                @Override
                                public void cut(int s, int e) {}
                                @Override
                                public void paste(int i) {}
                                @Override
                                public void replaceText(int s, int e, String t) {}
                                @Override
                                public void selectText(int s, int e) {}
                                @Override
                                public void setAttributes(int s, int e, javax.swing.text.AttributeSet a) {}
                                @Override
                                public int getIndexAtPoint(java.awt.Point p) { return -1; }
                                @Override
                                public java.awt.Rectangle getCharacterBounds(int i) { return null; }
                                @Override
                                public int getCharCount() { return 5; }
                                @Override
                                public int getCaretPosition() { return 0; }
                                @Override
                                public String getAtIndex(int p, int i) { return null; }
                                @Override
                                public String getAfterIndex(int p, int i) { return null; }
                                @Override
                                public String getBeforeIndex(int p, int i) { return null; }
                                @Override
                                public javax.swing.text.AttributeSet getCharacterAttribute(int i) { return null; }
                                @Override
                                public int getSelectionStart() { return 0; }
                                @Override
                                public int getSelectionEnd() { return 0; }
                                @Override
                                public String getSelectedText() { return null; }
                            };
                        }
                    };
                }
                return accessibleContext;
            }
        };
        assertEquals("", SwingUtils.readText(fake, 1000));
    }
}
