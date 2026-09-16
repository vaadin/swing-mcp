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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.swing.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for {@link SwingUtils#getTooltipAsText(Accessible)}.
 * <p>
 * Covers:
 * <ul>
 *   <li>Plain {@link JComponent} tooltip lookup</li>
 *   <li>{@link JTabbedPane} per-tab tooltip lookup via the page Accessible</li>
 *   <li>Disambiguation between a tab Accessible (PAGE_TAB role) and a
 *       JComponent that happens to live inside a JTabbedPane</li>
 *   <li>HTML cleanup: tag stripping, entity decoding, whitespace collapse</li>
 *   <li>Defensive cases: {@code null}, missing AccessibleContext,
 *       non-JComponent accessibles</li>
 * </ul>
 */
class SwingUtilsGetTooltipTest {

    @BeforeAll
    static void checkHeadless() {
        assertEquals("true", System.getProperty("java.awt.headless"));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JComponent — direct tooltip
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class JComponentDirect {

        @Test
        void buttonWithTooltip_returnsTooltip() {
            JButton button = new JButton("OK");
            button.setToolTipText("Save the document");
            assertEquals("Save the document", SwingUtils.getTooltipAsText(button));
        }

        @Test
        void buttonWithoutTooltip_returnsNull() {
            JButton button = new JButton("OK");
            assertNull(SwingUtils.getTooltipAsText(button));
        }

        @Test
        void buttonWithEmptyTooltip_returnsNull() {
            // Blank tooltips normalize to null so callers need only one
            // null check.
            JButton button = new JButton("OK");
            button.setToolTipText("");
            assertNull(SwingUtils.getTooltipAsText(button));
        }

        @Test
        void buttonWithWhitespaceOnlyTooltip_returnsNull() {
            JButton button = new JButton("OK");
            button.setToolTipText("   \t  ");
            assertNull(SwingUtils.getTooltipAsText(button));
        }

        @Test
        void labelWithTooltip_returnsTooltip() {
            JLabel label = new JLabel("Name");
            label.setToolTipText("The user's full name");
            assertEquals("The user's full name", SwingUtils.getTooltipAsText(label));
        }

        @Test
        void textFieldWithTooltip_returnsTooltip() {
            JTextField field = new JTextField();
            field.setToolTipText("Enter your email");
            assertEquals("Enter your email", SwingUtils.getTooltipAsText(field));
        }

        @Test
        void tabbedPaneItself_componentLevelTooltip_returnsIt() {
            // The JTabbedPane itself (not a tab) goes through the JComponent
            // branch — its own setToolTipText still works.
            JTabbedPane pane = new JTabbedPane();
            pane.setToolTipText("Section navigator");
            assertEquals("Section navigator", SwingUtils.getTooltipAsText(pane));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // JTabbedPane per-tab tooltips
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class JTabbedPanePerTab {

        @Test
        void tabWithToolTipTextAt_returnsThatTooltip() {
            JTabbedPane pane = new JTabbedPane();
            pane.addTab("General", new JPanel());
            pane.addTab("Advanced", new JPanel());
            pane.setToolTipTextAt(0, "Common settings");
            pane.setToolTipTextAt(1, "Power-user options");

            Accessible tab0 = pane.getAccessibleContext().getAccessibleChild(0);
            Accessible tab1 = pane.getAccessibleContext().getAccessibleChild(1);

            assertEquals("Common settings", SwingUtils.getTooltipAsText(tab0));
            assertEquals("Power-user options", SwingUtils.getTooltipAsText(tab1));
        }

        @Test
        void tabWithoutToolTipTextAt_returnsNull() {
            JTabbedPane pane = new JTabbedPane();
            pane.addTab("General", new JPanel());

            Accessible tab = pane.getAccessibleContext().getAccessibleChild(0);
            assertNull(SwingUtils.getTooltipAsText(tab));
        }

        @Test
        void tabHtmlTooltip_isStripped() {
            JTabbedPane pane = new JTabbedPane();
            pane.addTab("Advanced", new JPanel());
            pane.setToolTipTextAt(0,
                    "<html><b>Power user</b><br>Use with care</html>");

            Accessible tab = pane.getAccessibleContext().getAccessibleChild(0);
            assertEquals("Power user Use with care",
                    SwingUtils.getTooltipAsText(tab));
        }

        @Test
        void tabContentJComponent_returnsItsOwnTooltip_notTheTabsTooltip() {
            // Regression guard for the role check. A tab-content JComponent
            // also reports the JTabbedPane as its accessible parent — without
            // the PAGE_TAB role gate, getTooltipAsText would mistakenly look
            // up the tab tooltip by the panel's index.
            JTabbedPane pane = new JTabbedPane();
            JPanel content = new JPanel();
            content.setToolTipText("Content tooltip");
            pane.addTab("General", content);
            pane.setToolTipTextAt(0, "Tab tooltip");

            assertEquals("Content tooltip", SwingUtils.getTooltipAsText(content));
        }

        @Test
        void tabContentJComponentWithoutOwnTooltip_returnsNull_notTabTooltip() {
            // Same guard as above, but the content has no tooltip of its own —
            // the result must be null, not the tab's tooltip.
            JTabbedPane pane = new JTabbedPane();
            JPanel content = new JPanel();
            pane.addTab("General", content);
            pane.setToolTipTextAt(0, "Tab tooltip");

            assertNull(SwingUtils.getTooltipAsText(content));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HTML cleanup
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class HtmlCleanup {

        @Test
        void htmlTooltip_stripsTags() {
            JButton button = new JButton("OK");
            button.setToolTipText(
                    "<html><b>Save</b><br>Persists changes</html>");
            assertEquals("Save Persists changes",
                    SwingUtils.getTooltipAsText(button));
        }

        @Test
        void htmlTooltip_brBecomesSpace_notMissing() {
            // Tags must be replaced with a space so adjacent words don't
            // collide: "Save<br>file" → "Save file", not "Savefile".
            JButton button = new JButton("OK");
            button.setToolTipText("<html>Save<br>file</html>");
            assertEquals("Save file", SwingUtils.getTooltipAsText(button));
        }

        @Test
        void htmlTooltip_collapsesWhitespace() {
            JButton button = new JButton("OK");
            button.setToolTipText(
                    "<html>  hello   <br>   <b>world</b>   </html>");
            assertEquals("hello world", SwingUtils.getTooltipAsText(button));
        }

        @Test
        void htmlTooltip_decodesEntities() {
            JButton button = new JButton("OK");
            button.setToolTipText(
                    "<html>Tom&nbsp;&amp;&nbsp;Jerry &lt;3 &gt;_&lt;</html>");
            assertEquals("Tom & Jerry <3 >_<",
                    SwingUtils.getTooltipAsText(button));
        }

        @Test
        void htmlTooltip_ampDecodedLast_preservesEscapedEntities() {
            // Source text "&amp;lt;" represents the literal string "&lt;",
            // not the character "<". This works because we decode &amp; LAST.
            JButton button = new JButton("OK");
            button.setToolTipText("<html>&amp;lt;</html>");
            assertEquals("&lt;", SwingUtils.getTooltipAsText(button));
        }

        @Test
        void htmlTooltip_caseInsensitivePrefix() {
            // Swing accepts <HTML> too — verify our prefix check is case-
            // insensitive.
            JButton button = new JButton("OK");
            button.setToolTipText("<HTML><b>Bold</b></HTML>");
            assertEquals("Bold", SwingUtils.getTooltipAsText(button));
        }

        @Test
        void htmlTooltip_emptyAfterStrip_returnsNull() {
            // HTML that strips down to nothing is also blank → null.
            JButton button = new JButton("OK");
            button.setToolTipText("<html></html>");
            assertNull(SwingUtils.getTooltipAsText(button));
        }

        @Test
        void htmlTooltip_whitespaceOnlyAfterStrip_returnsNull() {
            JButton button = new JButton("OK");
            button.setToolTipText("<html>  <br>  &nbsp;  </html>");
            assertNull(SwingUtils.getTooltipAsText(button));
        }

        @Test
        void nonHtmlTooltipWithAngleBrackets_passesThroughVerbatim() {
            // Swing renders as HTML only when the string starts with <html>.
            // A literal tooltip like "List<String>" must NOT have its angle
            // brackets stripped.
            JButton button = new JButton("OK");
            button.setToolTipText("List<String>");
            assertEquals("List<String>", SwingUtils.getTooltipAsText(button));
        }

        @Test
        void nonHtmlTooltipWithEntities_passesThroughVerbatim() {
            // Entities are only decoded when the tooltip is HTML. A plain
            // tooltip mentioning "&amp;" should keep its literal text.
            JButton button = new JButton("OK");
            button.setToolTipText("Look up &amp; in the docs");
            assertEquals("Look up &amp; in the docs",
                    SwingUtils.getTooltipAsText(button));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Defensive cases
    // ══════════════════════════════════════════════════════════════════════════

    @Nested
    class Defensive {

        @Test
        void nullAccessible_returnsNull() {
            assertNull(SwingUtils.getTooltipAsText(null));
        }

        @Test
        void accessibleWithNoContext_returnsNull() {
            // An Accessible that returns null from getAccessibleContext()
            // and is not a JComponent — must not crash, must return null.
            Accessible a = () -> null;
            assertNull(SwingUtils.getTooltipAsText(a));
        }

        @Test
        void nonJComponentAccessibleWithContext_returnsNull() {
            // A custom Accessible that isn't a JComponent and isn't a
            // PAGE_TAB — there's nowhere to get a tooltip from.
            Accessible a = new Accessible() {
                @Override
                public AccessibleContext getAccessibleContext() {
                    return new AccessibleContext() {
                        @Override
                        public javax.accessibility.AccessibleRole getAccessibleRole() {
                            return javax.accessibility.AccessibleRole.LABEL;
                        }
                        @Override
                        public javax.accessibility.AccessibleStateSet getAccessibleStateSet() {
                            return new javax.accessibility.AccessibleStateSet();
                        }
                        @Override
                        public int getAccessibleIndexInParent() { return -1; }
                        @Override
                        public int getAccessibleChildrenCount() { return 0; }
                        @Override
                        public Accessible getAccessibleChild(int i) { return null; }
                        @Override
                        public java.util.Locale getLocale() { return java.util.Locale.ROOT; }
                    };
                }
            };
            assertNull(SwingUtils.getTooltipAsText(a));
        }
    }
}
