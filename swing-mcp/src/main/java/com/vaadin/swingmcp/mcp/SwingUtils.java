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

import org.jspecify.annotations.Nullable;

import javax.accessibility.*;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.JWindow;
import javax.swing.WindowConstants;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The capability probes — what a component can do — plus the Swing helpers behind them. The
 * snapshot and the tools ask the same probe, so what is advertised and what is accepted cannot
 * diverge:
 *
 * <pre>{@code
 * if (SwingUtils.supportsClick(accessible) != null) actions.add("click");   // the snapshot
 * Runnable click = SwingUtils.supportsClick(accessible);                    // swing_click
 * }</pre>
 */
public final class SwingUtils {

    private SwingUtils() {
    }

    // AbstractButton subclasses expose their selected/pressed state as an
    // AccessibleValue (0/1); the snapshot already carries it as CHECKED/SELECTED,
    // so get_value and set_value are both suppressed.
    static final Set<AccessibleRole> SUPPRESSED_VALUE_ROLES = Set.of(
            AccessibleRole.PUSH_BUTTON,
            AccessibleRole.TOGGLE_BUTTON,
            AccessibleRole.CHECK_BOX,
            AccessibleRole.RADIO_BUTTON,
            AccessibleRole.MENU,
            AccessibleRole.MENU_ITEM,
            AccessibleRole.PAGE_TAB,
            // JInternalFrame and JDesktopIcon expose AccessibleValue for the
            // JLayeredPane Z-order layer — a programmatic concept, not a
            // user-controlled value. See D_desktop_icon_as_itself.
            AccessibleRole.INTERNAL_FRAME,
            AccessibleRole.DESKTOP_ICON
    );

    static final Set<AccessibleRole> READ_ONLY_VALUE_ROLES = Set.of(
            AccessibleRole.PROGRESS_BAR
    );

    // Skipped by the MouseListener click tier: a MouseListener on a JButton is
    // look-and-feel plumbing, not application click behaviour.
    static final Set<AccessibleRole> INTERACTIVE_ROLES = Set.of(
            AccessibleRole.PUSH_BUTTON,
            AccessibleRole.TOGGLE_BUTTON,
            AccessibleRole.CHECK_BOX,
            AccessibleRole.RADIO_BUTTON,
            AccessibleRole.TEXT,
            AccessibleRole.PASSWORD_TEXT,
            AccessibleRole.COMBO_BOX,
            AccessibleRole.LIST,
            AccessibleRole.TABLE,
            AccessibleRole.TREE,
            AccessibleRole.MENU_BAR,
            AccessibleRole.MENU,
            AccessibleRole.MENU_ITEM,
            AccessibleRole.POPUP_MENU,
            AccessibleRole.SLIDER,
            AccessibleRole.SPIN_BOX,
            AccessibleRole.PROGRESS_BAR,
            AccessibleRole.SCROLL_BAR,
            AccessibleRole.COLOR_CHOOSER,
            AccessibleRole.FILE_CHOOSER,
            AccessibleRole.DATE_EDITOR
    );

    // MENU_BAR / MENU: the selection is internal keyboard navigation.
    // TREE: the tree-level selection is non-functional (R_selection_index_spaces,
    // D_no_jtree_selection).
    static final Set<AccessibleRole> SUPPRESSED_SELECTION_ROLES = Set.of(
            AccessibleRole.MENU_BAR,
            AccessibleRole.MENU,
            AccessibleRole.TREE
    );

    /**
     * Returns a {@link Runnable} that clicks {@code a} — through its {@link AccessibleAction}
     * click, else by dispatching a synthesized mouse click to an application-installed
     * {@link MouseListener} — or {@code null} if {@code a} is not clickable. The two tiers are
     * {@code design/architecture.md} § Click detection.
     */
    public static @Nullable Runnable supportsClick(Accessible a) {
        // D_jmenu_not_clickable
        if (a instanceof JMenu) {
            return null;
        }
        // --- Tier 1: AccessibleAction ---
        AccessibleContext ac = a.getAccessibleContext();
        if (ac != null) {
            AccessibleAction aa = ac.getAccessibleAction();
            if (aa != null) {
                String clickText = UIManager.getString("AbstractButton.clickText");
                for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
                    String desc = aa.getAccessibleActionDescription(i);
                    if (AccessibleAction.CLICK.equals(desc)
                            || (clickText != null && clickText.equals(desc))) {
                        final int idx = i;
                        return () -> aa.doAccessibleAction(idx);
                    }
                }
            }
        }
        // --- Tier 2: MouseListener fallback ---
        AccessibleRole role = (ac != null) ? ac.getAccessibleRole() : null;
        if (role != null && INTERACTIVE_ROLES.contains(role)) return null;

        if (a instanceof Component) {
            Component c = (Component) a;
            for (MouseListener ml : c.getMouseListeners()) {
                String cls = ml.getClass().getName();
                if (!cls.startsWith("javax.swing.")
                        && !cls.startsWith("java.awt.")
                        && !cls.startsWith("sun.")
                        && !cls.startsWith("com.sun.")
                        && !cls.startsWith("com.apple.")) {
                    return () -> {
                        int x = c.getWidth() / 2;
                        int y = c.getHeight() / 2;
                        long now = System.currentTimeMillis();
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_PRESSED,
                                now, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_RELEASED,
                                now + 1, 0, x, y, 1, false, MouseEvent.BUTTON1));
                        c.dispatchEvent(new MouseEvent(c, MouseEvent.MOUSE_CLICKED,
                                now + 2, 0, x, y, 1, false, MouseEvent.BUTTON1));
                    };
                }
            }
        }
        return null;
    }

    /**
     * Returns the index of {@code a}'s toggle-popup action, or {@code -1} if it has none.
     * Matches {@link AccessibleAction#TOGGLE_POPUP} and the localized
     * {@code "ComboBox.togglePopupText"} {@link UIManager} string (R_accessible_action_impls).
     */
    public static int supportsTogglePopup(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return -1;
        AccessibleAction aa = ac.getAccessibleAction();
        if (aa == null) return -1;
        String togglePopupText = UIManager.getString("ComboBox.togglePopupText");
        for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
            String desc = aa.getAccessibleActionDescription(i);
            if (AccessibleAction.TOGGLE_POPUP.equals(desc)
                    || (togglePopupText != null && togglePopupText.equals(desc))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns {@code true} if {@code a} exposes {@link AccessibleText} whose content is worth
     * showing the model — every such accessible except the
     * {@link AccessibleRole#PASSWORD_TEXT} (D_password_not_readable) and
     * {@link AccessibleRole#LABEL} (D_label_not_readable) roles.
     *
     * @apiNote Independent of {@link #supportsSetText}: a {@code JPasswordField} can be written
     *     but not read. For the raw "exposes {@code AccessibleText}" fact, ask the
     *     {@code AccessibleContext} directly.
     */
    public static boolean supportsGetText(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        AccessibleRole role = ac.getAccessibleRole();
        // D_password_not_readable: reads back echo characters, not the password.
        if (AccessibleRole.PASSWORD_TEXT.equals(role)) return false;
        // D_label_not_readable: redundant with the name slot.
        if (AccessibleRole.LABEL.equals(role)) return false;
        return ac.getAccessibleText() != null;
    }

    /**
     * Returns {@code true} if {@code a} has the {@link AccessibleRole#PASSWORD_TEXT} role — a
     * {@link javax.swing.JPasswordField} or any component that adopts it. Lets a refusal name
     * the password rule rather than a generic unsupported action (D_password_not_readable).
     */
    public static boolean hasPasswordRole(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        return AccessibleRole.PASSWORD_TEXT.equals(ac.getAccessibleRole());
    }

    /**
     * Returns {@code true} if {@code a} exposes {@link AccessibleEditableText} and is currently
     * {@link AccessibleState#EDITABLE} — so {@code false} after {@code setEditable(false)}.
     */
    public static boolean supportsSetText(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        return ac.getAccessibleEditableText() != null
                && ac.getAccessibleStateSet() != null
                && ac.getAccessibleStateSet().contains(AccessibleState.EDITABLE);
    }

    /**
     * Returns {@code true} if {@code a} exposes {@link AccessibleEditableText}, editable or not —
     * a text component that {@link #supportsSetText} rejects is then {@code read_only}.
     */
    public static boolean hasEditableText(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        return ac.getAccessibleEditableText() != null;
    }

    /**
     * Returns {@code true} if {@code a} exposes a readable, user-meaningful
     * {@link AccessibleValue} — not a button's pressed state or an internal frame's layer.
     */
    public static boolean supportsGetValue(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        AccessibleValue av = ac.getAccessibleValue();
        if (av == null) return false;
        if (SUPPRESSED_VALUE_ROLES.contains(ac.getAccessibleRole())) return false;
        // R_jspinner_accessibility: a JSpinner can expose an AccessibleValue that reads null.
        return av.getCurrentAccessibleValue() != null;
    }

    /**
     * Returns {@code true} if {@link #supportsGetValue} holds and the value is writable — not a
     * progress bar's.
     */
    public static boolean supportsSetValue(Accessible a) {
        if (!supportsGetValue(a)) return false;
        AccessibleRole role = a.getAccessibleContext().getAccessibleRole();
        return !READ_ONLY_VALUE_ROLES.contains(role);
    }

    /**
     * Returns {@code true} if {@code a} exposes a user-facing {@link AccessibleSelection} —
     * not a menu bar's, a menu's or a tree's — and, for a {@code JTable}, only in row-selection
     * mode (rows allowed, columns not).
     */
    public static boolean supportsSelection(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        if (ac.getAccessibleSelection() == null) return false;
        if (SUPPRESSED_SELECTION_ROLES.contains(ac.getAccessibleRole())) return false;
        if (a instanceof JTable) {
            JTable table = (JTable) a;
            return table.getRowSelectionAllowed() && !table.getColumnSelectionAllowed();
        }
        return true;
    }

    /**
     * Returns {@code true} if {@code a} is a target for {@code swing_get_items} /
     * {@code swing_get_item_count}: what {@link #supportsSelection} accepts, except that a
     * {@code JTable} passes in any selection mode and a {@code JTabbedPane} never does.
     *
     * @implNote A table passes in any mode because reading rows needs no working selection
     *     model, and with no cell tools on a table (D_no_jtable_cells) these are its only paged
     *     read. Tabs are rejected because the snapshot already lists each with its index
     *     (D_tabs_not_enumerated).
     */
    public static boolean supportsGetItems(Accessible a) {
        if (a instanceof JTabbedPane) {
            return false;
        }
        if (a instanceof JTable) {
            AccessibleContext ac = a.getAccessibleContext();
            return ac != null;
        }
        return supportsSelection(a);
    }

    /**
     * Returns {@code true} if {@code a} is {@link AccessibleState#MULTISELECTABLE}, or is a
     * {@code JTable} whose selection mode is not {@code SINGLE_SELECTION} — a table never
     * reports the state (R_multiselectable_not_reported). Does not check
     * {@link #supportsSelection}; {@link #supportsMultiSelection} does both.
     */
    public static boolean isMultiSelectable(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        if (ac.getAccessibleStateSet().contains(AccessibleState.MULTISELECTABLE)) {
            return true;
        }
        if (a instanceof JTable) {
            JTable table = (JTable) a;
            return table.getSelectionModel().getSelectionMode() != ListSelectionModel.SINGLE_SELECTION;
        }
        return false;
    }

    public static boolean supportsSingleSelection(Accessible a) {
        return supportsSelection(a) && !isMultiSelectable(a);
    }

    public static boolean supportsMultiSelection(Accessible a) {
        return supportsSelection(a) && isMultiSelectable(a);
    }

    /**
     * Returns the index of {@code a}'s {@link AccessibleAction#INCREMENT} action, or {@code -1}
     * if it has none. The constant is not localized, so it is compared raw
     * (R_accessible_action_impls); a {@code JSlider} has it only from Java 17
     * (R_jslider_actions_since_17).
     */
    public static int supportsIncrement(Accessible a) {
        return supportsAction(a, AccessibleAction.INCREMENT);
    }

    /**
     * Returns the index of {@code a}'s {@link AccessibleAction#DECREMENT} action, or {@code -1}
     * if it has none. Same caveats as {@link #supportsIncrement}.
     */
    public static int supportsDecrement(Accessible a) {
        return supportsAction(a, AccessibleAction.DECREMENT);
    }

    /**
     * Returns the index of {@code a}'s {@link AccessibleAction#TOGGLE_EXPAND} action — a
     * non-leaf {@code JTree} node's — or {@code -1} if it has none.
     */
    public static int supportsToggleExpand(Accessible a) {
        return supportsAction(a, AccessibleAction.TOGGLE_EXPAND);
    }

    private static int supportsAction(Accessible a, String actionName) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return -1;
        AccessibleAction aa = ac.getAccessibleAction();
        if (aa == null) return -1;
        for (int i = 0; i < aa.getAccessibleActionCount(); i++) {
            if (actionName.equals(aa.getAccessibleActionDescription(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Returns {@code true} if {@code a} supports the synthetic {@code close} action: a showing
     * {@code Frame} / {@code Dialog}, unless it is undecorated or an
     * {@code EXIT_ON_CLOSE} {@code JFrame}; a showing, closable {@link JInternalFrame} without
     * {@code EXIT_ON_CLOSE}; or a showing {@link JInternalFrame.JDesktopIcon} whose frame is
     * closable and not {@code EXIT_ON_CLOSE}.
     */
    public static boolean supportsClose(Accessible a) {
        if (a instanceof Window) {
            Window window = (Window) a;
            if (!window.isShowing()) return false;
            // A bare Window (a JWindow, say) has no decorations, so no [×] button.
            if (!(window instanceof Frame) && !(window instanceof Dialog)) return false;
            if (window instanceof Frame && ((Frame) window).isUndecorated()) return false;
            if (window instanceof Dialog && ((Dialog) window).isUndecorated()) return false;
            if (window instanceof JFrame &&
                    ((JFrame) window).getDefaultCloseOperation() == WindowConstants.EXIT_ON_CLOSE) return false;
            return true;
        }
        if (a instanceof JInternalFrame) {
            JInternalFrame iframe = (JInternalFrame) a;
            if (!iframe.isShowing()) return false;
            if (!iframe.isClosable()) return false;
            if (iframe.getDefaultCloseOperation() == WindowConstants.EXIT_ON_CLOSE) return false;
            return true;
        }
        // A frame its icon replaced is not showing (R_iconified_windows), so ask the icon.
        if (a instanceof JInternalFrame.JDesktopIcon) {
            JInternalFrame.JDesktopIcon icon = (JInternalFrame.JDesktopIcon) a;
            if (!icon.isShowing()) return false;
            JInternalFrame frame = icon.getInternalFrame();
            if (frame == null) return false;
            if (!frame.isClosable()) return false;
            if (frame.getDefaultCloseOperation() == WindowConstants.EXIT_ON_CLOSE) return false;
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code a} supports the synthetic {@code iconify} action: a showing,
     * decorated, not-yet-iconified {@link Frame}, or a showing, iconifiable, not-yet-iconified
     * {@link JInternalFrame}.
     */
    public static boolean supportsIconify(Accessible a) {
        if (a instanceof Frame) {
            Frame frame = (Frame) a;
            if (!frame.isShowing()) return false;
            if (frame.isUndecorated()) return false;
            if ((frame.getExtendedState() & Frame.ICONIFIED) != 0) return false;
            return true;
        }
        if (a instanceof JInternalFrame) {
            JInternalFrame iframe = (JInternalFrame) a;
            if (!iframe.isShowing()) return false;
            if (!iframe.isIconifiable()) return false;
            if (iframe.isIcon()) return false;
            return true;
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code a} is a showing {@link Frame} or {@link JInternalFrame} in
     * the iconified state. A {@code JDesktopIcon} is the icon, not an iconified frame, so it
     * returns {@code false}.
     *
     * @apiNote Only an internal frame iconified in place can pass: one its icon replaced is not
     *     showing (R_iconified_windows).
     */
    public static boolean isIconified(Accessible a) {
        if (a instanceof Frame) {
            Frame frame = (Frame) a;
            return frame.isShowing()
                    && (frame.getExtendedState() & Frame.ICONIFIED) != 0;
        }
        if (a instanceof JInternalFrame) {
            JInternalFrame iframe = (JInternalFrame) a;
            return iframe.isShowing() && iframe.isIcon();
        }
        return false;
    }

    /**
     * Returns {@code true} if {@code a} supports the synthetic {@code restore} action: it
     * {@linkplain #isIconified is iconified}, or is a showing {@link JInternalFrame.JDesktopIcon}.
     */
    public static boolean supportsRestore(Accessible a) {
        if (a instanceof JInternalFrame.JDesktopIcon) {
            return ((JInternalFrame.JDesktopIcon) a).isShowing();
        }
        return isIconified(a);
    }

    /**
     * Returns {@code a}'s accessible name, or {@code null} if it has none. A non-empty explicit
     * name wins; failing that, a {@link JInternalFrame} falls back to its title, and a
     * {@link JInternalFrame.JDesktopIcon} to its frame's name — so an iconified frame keeps the
     * name it had.
     */
    public static @Nullable String getEffectiveAccessibleName(Accessible a) {
        if (a instanceof JInternalFrame.JDesktopIcon) {
            JInternalFrame.JDesktopIcon icon = (JInternalFrame.JDesktopIcon) a;

            AccessibleContext iconCtx = icon.getAccessibleContext();
            if (iconCtx != null) {
                String name = iconCtx.getAccessibleName();
                if (name != null && !name.isEmpty()) return name;
            }

            JInternalFrame frame = icon.getInternalFrame();
            return frame != null ? getEffectiveAccessibleName(frame) : null;
        }

        if (a instanceof JInternalFrame) {
            JInternalFrame frame = (JInternalFrame) a;
            AccessibleContext ctx = frame.getAccessibleContext();
            if (ctx != null) {
                String name = ctx.getAccessibleName();
                if (name != null && !name.isEmpty()) return name;
            }
            return frame.getTitle();
        }

        AccessibleContext ctx = a.getAccessibleContext();
        return ctx != null ? ctx.getAccessibleName() : null;
    }

    /**
     * Returns {@code a}'s tooltip, uncapped and {@linkplain #htmlToPlainText as plain text}, or
     * {@code null} if it has none or it is blank. A {@code JTabbedPane} tab reads its per-tab
     * tooltip ({@link JTabbedPane#setToolTipTextAt}); any other {@link JComponent} reads
     * {@link JComponent#getToolTipText()}.
     *
     * @apiNote Per-cell, per-row, per-node and per-item tooltips on {@link JTable},
     *     {@link javax.swing.JList}, {@link javax.swing.JTree} and {@link JTableHeader} are not
     *     reachable: the renderer computes them from a {@link MouseEvent}, and there is none here.
     */
    public static @Nullable String getTooltipAsText(Accessible a) {
        if (a == null) return null;
        AccessibleContext ctx = a.getAccessibleContext();

        String raw = null;

        // Gate on the PAGE_TAB role, not on the parent alone: a tab's content
        // component also has the JTabbedPane as its accessible parent.
        if (ctx != null && ctx.getAccessibleRole() == AccessibleRole.PAGE_TAB) {
            Accessible parent = ctx.getAccessibleParent();
            if (parent instanceof JTabbedPane) {
                int index = ctx.getAccessibleIndexInParent();
                if (index >= 0) {
                    raw = ((JTabbedPane) parent).getToolTipTextAt(index);
                }
            }
        } else if (a instanceof JComponent) {
            raw = ((JComponent) a).getToolTipText();
        }

        return htmlToPlainText(raw);
    }

    /**
     * Converts a Swing HTML string to plain text; any other string is returned verbatim, as
     * Swing renders only a string starting with {@code <html>} (case-insensitive) as HTML:
     *
     * <pre>{@code
     * "<html><b>Save</b><br>Persists changes</html>"   // => "Save Persists changes"
     * "<html>Tom&nbsp;&amp;&nbsp;Jerry &lt;3</html>"   // => "Tom & Jerry <3"
     * "<html>&amp;lt;</html>"                           // => "&lt;"  (&amp; decoded last)
     * "List<String>"                                    // => "List<String>"
     * }</pre>
     *
     * Only {@code &amp; &lt; &gt; &nbsp;} are decoded; numeric entities stay as they are.
     *
     * @return the text, or {@code null} if {@code raw} is null or the result is blank
     */
    public static @Nullable String htmlToPlainText(@Nullable String raw) {
        if (raw == null) return null;

        String text;
        if (raw.regionMatches(true, 0, "<html>", 0, 6)) {
            text = raw.replaceAll("<[^>]*>", " ");
            text = text.replace("&nbsp;", " ")
                       .replace("&lt;", "<")
                       .replace("&gt;", ">")
                       .replace("&amp;", "&");
            text = text.replaceAll("\\s+", " ").trim();
        } else {
            text = raw;
        }

        return text.isBlank() ? null : text;
    }

    /**
     * Makes a string safe inside a double-quoted snapshot slot: whitespace runs collapse to one
     * space, the ends are stripped, and {@code "} is escaped (D_quoted_slot_sanitizing).
     *
     * <pre>{@code
     * sanitizeForQuotedSlot("Line 1\nLine 2");   // => Line 1 Line 2
     * sanitizeForQuotedSlot("say \"hi\"");       // => say \"hi\"
     * sanitizeForQuotedSlot("\n\t\r");           // => null
     * }</pre>
     *
     * @return the sanitized text, or {@code null} if {@code raw} is null or blank
     * @apiNote Not idempotent: backslashes are not escaped, so a second pass double-escapes
     *     every quote. Call it exactly once per slot.
     */
    public static @Nullable String sanitizeForQuotedSlot(@Nullable String raw) {
        if (raw == null) return null;
        // Java's \s is ASCII-only; the Unicode line separators would still break a line.
        String collapsed = raw.replaceAll("[\\s\\u0085\\u2028\\u2029]+", " ").strip();
        if (collapsed.isEmpty()) return null;
        return collapsed.replace("\"", "\\\"");
    }

    /**
     * Resolves {@code a}'s description as the snapshot's description slot shows it: the
     * accessible description as plain text, else the tooltip,
     * {@linkplain #sanitizeForQuotedSlot sanitized} but not capped.
     *
     * @return the description, or {@code null} if neither source has one
     */
    public static @Nullable String resolveDescription(Accessible a) {
        if (a == null) return null;
        AccessibleContext ctx = a.getAccessibleContext();
        String desc = ctx != null
                ? htmlToPlainText(ctx.getAccessibleDescription())
                : null;
        if (desc == null) {
            desc = getTooltipAsText(a);
        }
        return sanitizeForQuotedSlot(desc);
    }

    /**
     * Returns {@code true} if a user could interact with {@code a} — its own
     * {@link AccessibleState#ENABLED} state, not its parents', since Swing's disable does not
     * propagate (D_mirror_swing_semantics). Two carve-outs follow the real API where the state
     * set lies: a tab disabled with {@link JTabbedPane#setEnabledAt}, and a virtual child such
     * as a {@code JTable} cell, which follows its host component (R_disabled_not_propagated).
     */
    public static boolean isEffectivelyEnabled(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        if (!ac.getAccessibleStateSet().contains(AccessibleState.ENABLED)) return false;

        Accessible parent = ac.getAccessibleParent();

        // The AccessiblePage keeps ENABLED for a tab disabled with setEnabledAt.
        if (parent instanceof JTabbedPane) {
            JTabbedPane tp = (JTabbedPane) parent;
            int idx = ac.getAccessibleIndexInParent();
            if (idx >= 0 && idx < tp.getTabCount() && !tp.isEnabledAt(idx)) {
                return false;
            }
        }

        // A JTable cell keeps ENABLED when its table is disabled; ask the host Component.
        if (!(a instanceof Component) && parent != null) {
            return isEffectivelyEnabled(parent);
        }

        // D_mirror_swing_semantics: a real Component does not inherit its parent's disable.
        return true;
    }

    /**
     * Returns {@code false} if {@code accessible} is a {@link Component} that is not
     * {@linkplain Component#isVisible() visible}, or is showing with a zero width or height;
     * {@code true} otherwise, including for every virtual accessible.
     */
    public static boolean isVisible(Accessible accessible) {
        if (accessible instanceof Component) {
            Component c = (Component) accessible;
            if (!c.isVisible()) return false;
            // Size only once showing: an unrealized component (a headless test's)
            // legitimately has zero size.
            if (c.isShowing() && (c.getWidth() <= 0 || c.getHeight() <= 0)) return false;
        }
        return true;
    }

    /**
     * Returns {@code component}'s simple class name for a message or log line, never empty: an
     * anonymous class, whose simple name is {@code ""}, gets its binary name
     * ({@code com.example.LoginForm$1}) — which points at the declaring site, as the
     * superclass's {@code JButton} would not.
     */
    public static String getComponentClassName(Component component) {
        String name = component.getClass().getSimpleName();
        return name.isEmpty() ? component.getClass().getName() : name;
    }

    /**
     * Returns {@code true} if {@code w} is a heavyweight {@link JWindow} hosting the
     * {@link JPopupMenu} of a {@link JComboBox} or {@link JMenu}, whose items that invoker
     * already exposes — a context menu's window is not redundant (D_interactable_windows_only).
     */
    public static boolean isRedundantPopupWindow(Window w) {
        if (!(w instanceof JWindow)) {
            return false;
        }
        Container contentPane = ((JWindow) w).getContentPane();
        for (int i = 0; i < contentPane.getComponentCount(); i++) {
            Component child = contentPane.getComponent(i);
            if (child instanceof JPopupMenu) {
                Component invoker = ((JPopupMenu) child).getInvoker();
                return invoker instanceof JComboBox || invoker instanceof JMenu;
            }
        }
        return false;
    }

    /**
     * Returns the topmost visible modal dialog, or {@code null} if none is visible: the active
     * window if it is one, else the last one in {@link Window#getWindows()}.
     */
    public static @Nullable Dialog getTopmostModalDialog() {
        Window active = KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
        if (active instanceof Dialog) {
            Dialog d = (Dialog) active;
            if (d.isVisible() && d.isModal()) {
                return d;
            }
        }

        // Scan backwards: the most recently created window is last.
        Window[] windows = Window.getWindows();
        if (windows == null) {
            return null;
        }
        for (int i = windows.length - 1; i >= 0; i--) {
            if (windows[i] instanceof Dialog) {
                Dialog d = (Dialog) windows[i];
                if (d.isVisible() && d.isModal()) {
                    return d;
                }
            }
        }
        return null;
    }

    // ── Selection item helpers ─────────────────────────────────────────────

    /** Maximum columns included in a JTable row name summary. */
    public static final int MAX_ROW_NAME_COLUMNS = 10;

    /**
     * Returns the item count of {@code a}: a {@code JTable}'s rows, a {@code JComboBox}'s
     * {@code getItemCount()} — its accessible children are just the popup
     * (R_selection_index_spaces) — and otherwise the accessible children.
     *
     * @param a an accessible that passes {@link #supportsGetItems}
     */
    public static int getItemCount(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (a instanceof JTable) {
            return ac.getAccessibleTable().getAccessibleRowCount();
        } else if (a instanceof JComboBox) {
            return ((JComboBox<?>) a).getItemCount();
        } else {
            return ac.getAccessibleChildrenCount();
        }
    }

    /**
     * Returns {@code true} if {@code a}'s role is {@code TABLE}, {@code LIST} or {@code TREE} —
     * a component whose children the snapshot truncates. The cell tools accept fewer; see
     * {@link #isGetCellsSupported}.
     */
    public static boolean isLargeDataComponent(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        AccessibleRole role = ac.getAccessibleRole();
        return role == AccessibleRole.TABLE
                || role == AccessibleRole.LIST
                || role == AccessibleRole.TREE;
    }

    /**
     * Returns {@code true} if {@code a} is a target for {@code swing_get_cells} /
     * {@code swing_get_cell_count}: its role is {@code LIST} or {@code TREE}, never a table's
     * (D_no_jtable_cells).
     */
    public static boolean isGetCellsSupported(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return false;
        AccessibleRole role = ac.getAccessibleRole();
        return role == AccessibleRole.LIST
                || role == AccessibleRole.TREE;
    }

    // ── JTable header & row helpers ──────────────────────────────────────────

    /**
     * Returns {@code true} if a user can see {@code table}'s header: Swing
     * shows the header only when the table is the view of a {@link JScrollPane}, and a showing
     * zero-sized header counts as hidden, as in {@link #isVisible(Accessible)}.
     */
    public static boolean isTableHeaderVisible(JTable table) {
        JTableHeader header = table.getTableHeader();
        if (header == null) return false;
        if (!header.isVisible()) return false;
        Container parent = table.getParent();
        if (!(parent instanceof JViewport)) return false;
        Container grandparent = parent.getParent();
        if (!(grandparent instanceof JScrollPane)) return false;
        if (header instanceof Accessible && !isVisible((Accessible) header)) return false;
        return true;
    }

    /**
     * Returns {@code table}'s column header values as strings, in display order — the
     * {@link TableColumnModel}'s, so a column the user dragged elsewhere is where they put it.
     * A {@code null} header value reads {@code "null"}.
     */
    public static List<String> getTableColumnNames(JTable table) {
        TableColumnModel cm = table.getColumnModel();
        int cols = cm.getColumnCount();
        List<String> names = new ArrayList<>(cols);
        for (int i = 0; i < cols; i++) {
            Object headerValue = cm.getColumn(i).getHeaderValue();
            names.add(headerValue != null ? headerValue.toString() : "null");
        }
        return names;
    }

    /**
     * Joins one table row's cell names with {@code " | "}, at most
     * {@link #MAX_ROW_NAME_COLUMNS} of them, then {@code " | …"} if the table has more:
     *
     * <pre>
     * Alice | 30
     * </pre>
     *
     * @param row 0-based
     * @param cols the table's column count, not the number to include
     */
    public static String buildTableRowText(AccessibleTable at, int row, int cols) {
        int colLimit = Math.min(cols, MAX_ROW_NAME_COLUMNS);
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col < colLimit; col++) {
            if (col > 0) sb.append(" | ");
            Accessible cell = at.getAccessibleAt(row, col);
            sb.append(describeTableCell(cell));
        }
        if (cols > MAX_ROW_NAME_COLUMNS) {
            sb.append(" | \u2026");
        }
        return sb.toString();
    }

    /**
     * Returns the cell's accessible name — the cell value's {@code toString()}, whatever its
     * renderer paints (R_jtable_cells_stamped) — or {@code "null"} if the cell or its name is
     * null.
     */
    static String describeTableCell(Accessible cell) {
        if (cell == null) return "null";
        AccessibleContext ac = cell.getAccessibleContext();
        if (ac == null) return "null";
        String name = ac.getAccessibleName();
        return (name != null) ? name : "null";
    }

    // ── Drag support ─────────────────────────────────────────────────────

    /**
     * A host component and a point in its local coordinates — where a real component or a
     * virtual child ({@code JList} item, {@code JTree} node) actually sits. Immutable.
     */
    public static class ComponentAndPoint {
        public final Component component;
        public final int x;
        public final int y;

        public ComponentAndPoint(Component component, int x, int y) {
            this.component = component;
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Resolves {@code a} to the centre of where it sits: a component's own centre, or a virtual
     * child's centre within its nearest {@link Component} ancestor.
     *
     * @implNote A virtual child's bounds are relative to its accessible parent, which may itself
     *     be virtual — a nested {@code JTree} node's are relative to its parent node
     *     (R_virtual_child_bounds) — so each virtual ancestor's offset is added on the way up.
     * @return the point, or {@code null} if a virtual child or one of its virtual ancestors has no
     *     bounds, or it has no {@code Component} ancestor
     */
    public static @Nullable ComponentAndPoint resolveComponentAndPoint(Accessible a) {
        if (a instanceof Component) {
            Component c = (Component) a;
            return new ComponentAndPoint(c, c.getWidth() / 2, c.getHeight() / 2);
        }

        java.awt.Rectangle bounds = accessibleBounds(a);
        if (bounds == null) return null;
        int x = bounds.x + bounds.width / 2;
        int y = bounds.y + bounds.height / 2;

        Accessible parent = a.getAccessibleContext().getAccessibleParent();
        while (parent != null) {
            if (parent instanceof Component) {
                return new ComponentAndPoint((Component) parent, x, y);
            }
            java.awt.Rectangle parentBounds = accessibleBounds(parent);
            if (parentBounds == null) return null;
            x += parentBounds.x;
            y += parentBounds.y;
            parent = parent.getAccessibleContext().getAccessibleParent();
        }
        return null;
    }

    /**
     * @return {@code a}'s bounds relative to its accessible parent, or {@code null} if it has no
     *     {@code AccessibleComponent} or no bounds — a {@code JTree} node that is not showing
     */
    private static java.awt.@Nullable Rectangle accessibleBounds(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return null;
        AccessibleComponent component = ac.getAccessibleComponent();
        return component == null ? null : component.getBounds();
    }

    /**
     * Returns a drag as synthesized mouse events, one {@link Runnable} per event, each
     * dispatching to {@code source}: a press, drag events interpolated through each waypoint to
     * the target (5 steps with no waypoints, 3 per segment with them), 16 ms apart, then a
     * release. Every coordinate is {@code source}-local. Post each step as its own EDT task:
     *
     * <pre>{@code
     * for (Runnable step : SwingUtils.createDragSteps(list, 50, 10, 250, 10, List.of())) {
     *     SwingUtilities.invokeLater(step);   // a press that throws still lets the rest run
     * }
     * }</pre>
     *
     * @param waypoints each an {@code int[]{x, y}}; may be empty
     * @implNote Not {@code EventQueue.postEvent}, which would be closer to a real mouse: it
     *     coalesces every queued {@code MOUSE_DRAGGED} on one component into the last, so the
     *     interpolated path collapses to a single jump. See R_drag_events_coalesce.
     */
    public static java.util.List<Runnable> createDragSteps(Component source, int pressX, int pressY,
                                                           int targetX, int targetY,
                                                           java.util.List<int[]> waypoints) {
        long start = System.currentTimeMillis();
        java.util.List<Runnable> steps = new java.util.ArrayList<>();

        steps.add(() -> source.dispatchEvent(new MouseEvent(source, MouseEvent.MOUSE_PRESSED,
                start, InputEvent.BUTTON1_DOWN_MASK,
                pressX, pressY, 0, false, MouseEvent.BUTTON1)));

        int fromX = pressX, fromY = pressY;

        java.util.List<int[]> segments = new java.util.ArrayList<>(waypoints);
        segments.add(new int[]{targetX, targetY});

        int stepsPerSegment = waypoints.isEmpty() ? 5 : 3;
        for (int[] seg : segments) {
            int toX = seg[0], toY = seg[1];
            for (int i = 1; i <= stepsPerSegment; i++) {
                int x = fromX + (toX - fromX) * i / stepsPerSegment;
                int y = fromY + (toY - fromY) * i / stepsPerSegment;
                long when = start + 16L * steps.size();
                steps.add(() -> source.dispatchEvent(new MouseEvent(source,
                        MouseEvent.MOUSE_DRAGGED, when, InputEvent.BUTTON1_DOWN_MASK,
                        x, y, 0, false, MouseEvent.NOBUTTON)));
            }
            fromX = toX;
            fromY = toY;
        }

        long end = start + 16L * steps.size();
        steps.add(() -> source.dispatchEvent(new MouseEvent(source, MouseEvent.MOUSE_RELEASED,
                end, 0,
                targetX, targetY, 0, false, MouseEvent.BUTTON1)));
        return steps;
    }

    /**
     * Returns a {@link Runnable} that drags with real OS mouse events through
     * {@link java.awt.Robot}: press, move through each waypoint to the target (10 steps with no
     * waypoints, 3 per segment with them), release. Every coordinate is on-screen.
     *
     * @param waypoints each an {@code int[]{screenX, screenY}}; may be empty
     * @apiNote The Runnable sleeps between moves, a second or more in all, so run it off the
     *     EDT. It throws {@code RuntimeException} if no {@code Robot} can be created.
     */
    public static Runnable createRobotDragAction(int pressScreenX, int pressScreenY,
                                                  int targetScreenX, int targetScreenY,
                                                  java.util.List<int[]> waypoints) {
        return () -> {
            try {
                java.awt.Robot robot = new java.awt.Robot();
                robot.setAutoDelay(50);

                robot.mouseMove(pressScreenX, pressScreenY);
                robot.delay(100);
                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.delay(100);

                int fromX = pressScreenX, fromY = pressScreenY;

                java.util.List<int[]> segments = new java.util.ArrayList<>(waypoints);
                segments.add(new int[]{targetScreenX, targetScreenY});

                int stepsPerSegment = waypoints.isEmpty() ? 10 : 3;
                for (int[] seg : segments) {
                    int toX = seg[0], toY = seg[1];
                    for (int i = 1; i <= stepsPerSegment; i++) {
                        int x = fromX + (toX - fromX) * i / stepsPerSegment;
                        int y = fromY + (toY - fromY) * i / stepsPerSegment;
                        robot.mouseMove(x, y);
                    }
                    fromX = toX;
                    fromY = toY;
                }

                robot.delay(100);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            } catch (java.awt.AWTException e) {
                throw new RuntimeException("Robot could not be created: " + e.getMessage(), e);
            }
        };
    }

    /**
     * Returns {@code value} as a {@code long} if it is whole, else as a {@code double} — so
     * {@code 3.0} prints as {@code 3} and {@code 2.5} as {@code 2.5}.
     */
    public static Number serializeNumber(Number value) {
        double d = value.doubleValue();
        if (d % 1 == 0) {
            return (long) d;
        }
        return d;
    }

    /**
     * Reads up to {@code maxChars} characters of {@code a}'s {@link AccessibleText} — the one
     * read that both the snapshot's inline preview and {@code swing_get_text} use
     * (D_inline_value_preview).
     *
     * @param maxChars {@link Integer#MAX_VALUE} for no cap
     * @return the text; {@code ""} if there is no {@code AccessibleText} or it is empty
     * @apiNote Does not apply the {@link #supportsGetText} gate: it would read a password
     *     field's echo characters.
     */
    public static String readText(Accessible a, int maxChars) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) return "";
        AccessibleText at = ac.getAccessibleText();
        if (at == null) return "";
        int len = at.getCharCount();
        if (len <= 0) return "";
        int readLen = Math.min(len, maxChars);

        AccessibleEditableText editable = ac.getAccessibleEditableText();
        if (editable != null) {
            String text = editable.getTextRange(0, readLen);
            return text != null ? text : "";
        }

        // A read-only AccessibleText has no bulk read.
        StringBuilder sb = new StringBuilder(readLen);
        for (int i = 0; i < readLen; i++) {
            String ch = at.getAtIndex(AccessibleText.CHARACTER, i);
            if (ch != null) sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * Returns {@code a}'s current {@link AccessibleValue} as the raw JDK {@link Number} —
     * format it with {@link #serializeNumber}. The one read that both the snapshot's inline
     * preview and {@code swing_get_value} use (D_inline_value_preview).
     *
     * @param a an accessible that passes {@link #supportsGetValue}
     * @throws IllegalStateException if {@code a} has no readable {@code AccessibleValue}
     */
    public static Number readValue(Accessible a) {
        AccessibleContext ac = a.getAccessibleContext();
        if (ac == null) {
            throw new IllegalStateException("readValue called on accessible with no AccessibleContext");
        }
        AccessibleValue av = ac.getAccessibleValue();
        if (av == null) {
            throw new IllegalStateException("readValue called on accessible with no AccessibleValue (gate violation)");
        }
        Number current = av.getCurrentAccessibleValue();
        if (current == null) {
            throw new IllegalStateException("AccessibleValue.getCurrentAccessibleValue() returned null (gate violation)");
        }
        return current;
    }
}
