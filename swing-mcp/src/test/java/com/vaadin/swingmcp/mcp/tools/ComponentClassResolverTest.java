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

import javax.accessibility.Accessible;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cases A, B and C are the three identity-slot forms of design/snapshot-format.md, in table order.
 */
class ComponentClassResolverTest {

    // ══════════════════════════════════════════════════════════════════════════
    // Case A — standard Swing component
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void standardJButton_rendersClassAndRole() {
        assertEquals("JButton (push_button)",
                ComponentClassResolver.resolveIdentitySlot(new JButton("OK")));
    }

    @Test
    void standardJPanel_rendersClassAndRole() {
        assertEquals("JPanel (panel)",
                ComponentClassResolver.resolveIdentitySlot(new JPanel()));
    }

    @Test
    void standardJTextField_rendersClassAndRole() {
        assertEquals("JTextField (text)",
                ComponentClassResolver.resolveIdentitySlot(new JTextField()));
    }

    // No JFrame case: a headless JFrame constructor throws HeadlessException. The screen-mode
    // tests render one; isQualifying_acceptsStandardSwingWidget covers the class.

    // ══════════════════════════════════════════════════════════════════════════
    // Case A — stripped anonymous / local / synthetic subclasses
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void anonymousJButtonSubclass_stripsAndRendersAsJButton() {
        JButton anon = new JButton("A") { };
        assertTrue(anon.getClass().isAnonymousClass(),
                "precondition: anonymous subclass");
        assertEquals("JButton (push_button)",
                ComponentClassResolver.resolveIdentitySlot(anon));
    }

    @Test
    void localJButtonSubclass_stripsAndRendersAsJButton() {
        class LocalButton extends JButton {
            LocalButton() { super("L"); }
        }
        JButton local = new LocalButton();
        assertTrue(local.getClass().isLocalClass(),
                "precondition: local class");
        assertEquals("JButton (push_button)",
                ComponentClassResolver.resolveIdentitySlot(local));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Case B — meaningful custom subclass
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void customJButtonSubclass_rendersAsCaseB() {
        assertEquals("FancyButton -> JButton (push_button)",
                ComponentClassResolver.resolveIdentitySlot(new FancyButton()));
    }

    @Test
    void customJTextFieldSubclass_rendersAsCaseB() {
        assertEquals("SearchField -> JTextField (text)",
                ComponentClassResolver.resolveIdentitySlot(new SearchField()));
    }

    @Test
    void abstractButtonSubclass_walksUpToAbstractButton() {
        // Skipping the abstract AbstractButton would land on JComponent and lose the
        // button-family signal. The fixture has no AccessibleContext, so only the prefix is asserted.
        String slot = ComponentClassResolver.resolveIdentitySlot(new MyBareButton());
        assertTrue(slot.startsWith("MyBareButton -> AbstractButton ("),
                "expected 'MyBareButton -> AbstractButton (<role>)', got: " + slot);
    }

    @Test
    void transitiveCustomSubclass_walksToNearestSwingAncestor() {
        // DerivedSearchField -> SearchField -> JTextField.
        assertEquals("DerivedSearchField -> JTextField (text)",
                ComponentClassResolver.resolveIdentitySlot(new DerivedSearchField()));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Case C — non-Component accessible
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void jTabbedPanePage_rendersAsCaseCParensOnly() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("One", new JPanel());
        Accessible page = tabs.getAccessibleContext().getAccessibleChild(0);
        assertNotNull(page, "JTabbedPane must expose its Page as accessible child");
        // JTabbedPane.Page extends AccessibleContext, not Component.
        assertEquals("(page_tab)",
                ComponentClassResolver.resolveIdentitySlot(page));
    }

    @Test
    void jListChild_rendersAsCaseCParensOnly() {
        JList<String> list = new JList<>(new String[]{"Apple", "Banana"});
        Accessible item = list.getAccessibleContext().getAccessibleChild(0);
        assertNotNull(item, "JList must expose items as accessible children");
        String slot = ComponentClassResolver.resolveIdentitySlot(item);
        assertTrue(slot.startsWith("(") && slot.endsWith(")"),
                "expected Case C format (role) for JList item, got: " + slot);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // resolveClassName — error-message variant (class names only, no role)
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void resolveClassName_caseA_returnsStandardSwingClass() {
        assertEquals("JButton", ComponentClassResolver.resolveClassName(new JButton("OK")));
        assertEquals("JTextField", ComponentClassResolver.resolveClassName(new JTextField()));
    }

    @Test
    void resolveClassName_caseB_returnsQualifyingAncestor() {
        // Errors name the standard Swing ancestor, the vocabulary a model knows
        // (D_role_in_snapshot_only), not the concrete subclass.
        assertEquals("JButton", ComponentClassResolver.resolveClassName(new FancyButton()));
        assertEquals("JTextField", ComponentClassResolver.resolveClassName(new SearchField()));
    }

    @Test
    void resolveClassName_anonymousSubclass_stripsToRealParent() {
        JButton anon = new JButton("A") { };
        assertEquals("JButton", ComponentClassResolver.resolveClassName(anon));
    }

    @Test
    void resolveClassName_caseC_fallsBackToComponent() {
        // JTabbedPane.Page has no Swing/AWT qualifying ancestor — degrade
        // gracefully so "Component does not support …" still reads cleanly.
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("T", new JPanel());
        Accessible page = tabs.getAccessibleContext().getAccessibleChild(0);
        assertNotNull(page);
        assertEquals("Component", ComponentClassResolver.resolveClassName(page));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Algorithm primitives — findDisplayClass / isQualifying / isRuntimeProxy
    // ══════════════════════════════════════════════════════════════════════════

    @Test
    void findDisplayClass_keepsConcreteWhenNotStripped() {
        assertSame(FancyButton.class,
                ComponentClassResolver.findDisplayClass(FancyButton.class));
    }

    @Test
    void findDisplayClass_stripsAnonymousToRealParent() {
        JButton anon = new JButton() { };
        assertSame(JButton.class,
                ComponentClassResolver.findDisplayClass(anon.getClass()));
    }

    @Test
    void isQualifying_acceptsStandardSwingWidget() {
        assertTrue(ComponentClassResolver.isQualifying(JButton.class));
        assertTrue(ComponentClassResolver.isQualifying(JPanel.class));
        assertTrue(ComponentClassResolver.isQualifying(JFrame.class));
    }

    @Test
    void isQualifying_acceptsAbstractSwingClasses() {
        assertTrue(ComponentClassResolver.isQualifying(AbstractButton.class));
        assertTrue(ComponentClassResolver.isQualifying(javax.swing.text.JTextComponent.class));
    }

    @Test
    void isQualifying_acceptsAwtClasses() {
        assertTrue(ComponentClassResolver.isQualifying(java.awt.Button.class));
        assertTrue(ComponentClassResolver.isQualifying(java.awt.Canvas.class));
        assertTrue(ComponentClassResolver.isQualifying(java.awt.Menu.class));
    }

    @Test
    void isQualifying_rejectsPlafClasses() {
        // javax.swing.plaf.* is L&F internals; the walk goes past it.
        assertFalse(ComponentClassResolver.isQualifying(javax.swing.plaf.basic.BasicArrowButton.class));
    }

    @Test
    void isQualifying_rejectsNestedClasses() {
        // FancyButton is a static nested class of this test → getEnclosingClass() != null.
        assertFalse(ComponentClassResolver.isQualifying(FancyButton.class));
    }

    @Test
    void isQualifying_rejectsNonSwingAwtClasses() {
        assertFalse(ComponentClassResolver.isQualifying(Object.class));
        assertFalse(ComponentClassResolver.isQualifying(String.class));
    }

    @Test
    void isRuntimeProxy_falseForNestedAndStandardClasses() {
        // Negatives only: the positive case needs a generated class, which
        // SwingSnapshotToolTest builds with ByteBuddy.
        assertFalse(ComponentClassResolver.isRuntimeProxy(FancyButton.class),
                "nested class with enclosing must not be flagged as proxy");
        assertFalse(ComponentClassResolver.isRuntimeProxy(JButton.class),
                "standard Swing class has zero $ signs");
    }

    @Test
    void findQualifyingAncestor_returnsNullForNonComponentAccessible() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("T", new JPanel());
        Accessible page = tabs.getAccessibleContext().getAccessibleChild(0);
        Class<?> display = ComponentClassResolver.findDisplayClass(page.getClass());
        assertNull(ComponentClassResolver.findQualifyingAncestor(display),
                "JTabbedPane.Page is not a Component — no qualifying ancestor");
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Test fixtures
    // ══════════════════════════════════════════════════════════════════════════

    /** Direct subclass of a standard Swing widget — exercises Case B. */
    public static class FancyButton extends JButton {
        public FancyButton() { super("Fancy"); }
    }

    /** Typical company-library widget — exercises Case B. */
    public static class SearchField extends JTextField {
        public SearchField() { super(10); }
    }

    /** Transitive custom subclass — walk-up still lands on JTextField. */
    public static class DerivedSearchField extends SearchField {
    }

    /**
     * Subclass of the abstract {@code AbstractButton}, which is not {@code Accessible} (its
     * concrete subclasses are), so the fixture declares it. Its context stays null.
     */
    public static class MyBareButton extends AbstractButton implements Accessible {
        public MyBareButton() {
            setModel(new javax.swing.DefaultButtonModel());
        }
    }
}
