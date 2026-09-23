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

import org.jspecify.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import java.awt.Component;
import java.awt.MenuComponent;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * Names an accessible the way a person or model reads it: the snapshot's identity slot
 * ({@code JButton (push_button)}, {@code SearchField -> JTextField (text)},
 * {@code (page_tab)} — the three forms are owned by {@code design/snapshot-format.md}),
 * and the bare class name that error messages use ({@code JButton}).
 *
 * <p>The resolution runs in two steps over the superclass chain:
 * <ol>
 *   <li>the <b>display class</b> — the first class {@link #shouldStrip} does not reject;</li>
 *   <li>the <b>qualifying ancestor</b> — the first class from there up that
 *       {@link #isQualifying} accepts. None means a non-{@link Component} accessible
 *       ({@code JTabbedPane.Page}, {@code JList.AccessibleJListChild}), rendered as
 *       {@code (role)} alone; a display class that is not the ancestor itself is a custom
 *       subclass, rendered as {@code Concrete -> JClass (role)}.</li>
 * </ol>
 *
 * <p>{@code ComponentClassResolverAuditTest} pins the qualifying-ancestor set against a
 * checked-in fixture, so a JDK that adds or removes a Swing class fails the build rather
 * than silently changing what every snapshot line says.</p>
 */
public final class ComponentClassResolver {

    private ComponentClassResolver() {
    }

    /**
     * Returns the display class of {@code accessible} — the concrete-side identity alone,
     * without the {@code -> JClass (role)} decoration, as the modal-stack header shows it
     * (D_modal_stack_header).
     *
     * @return never {@code null}; {@code Object} when every class in the chain is stripped
     */
    public static Class<?> resolveDisplayClass(Accessible accessible) {
        return findDisplayClass(accessible.getClass());
    }

    /**
     * Returns the qualifying ancestor's simple name, for error messages
     * (D_role_in_snapshot_only):
     *
     * <pre>{@code
     * ComponentClassResolver.resolveClassName(accessible) + " does not support " + toolName
     * }</pre>
     *
     * @return {@code "JTextField"} for a {@code SearchField}, too; {@code "Component"} for a
     *         non-{@link Component} accessible such as {@code JTabbedPane.Page}
     */
    public static String resolveClassName(Accessible accessible) {
        Class<?> displayClass = findDisplayClass(accessible.getClass());
        Class<?> qualifying = findQualifyingAncestor(displayClass);
        if (qualifying == null) {
            return "Component";
        }
        return qualifying.getSimpleName();
    }

    /**
     * Returns the snapshot line's identity slot, e.g. {@code SearchField -> JTextField (text)}.
     *
     * @return the role reads {@code unknown} when there is no {@link AccessibleContext} or
     *         no role, as {@link AccessibleNames#roleName} does for an undeclared one
     */
    public static String resolveIdentitySlot(Accessible accessible) {
        AccessibleContext ctx = accessible.getAccessibleContext();
        AccessibleRole role = ctx != null ? ctx.getAccessibleRole() : null;
        String roleName = role != null ? AccessibleNames.roleName(role) : "unknown";

        Class<?> concrete = accessible.getClass();
        Class<?> displayClass = findDisplayClass(concrete);
        Class<?> qualifying = findQualifyingAncestor(displayClass);

        if (qualifying == null) {
            return "(" + roleName + ")";
        }

        String ancestorName = qualifying.getSimpleName();
        if (displayClass == qualifying) {
            return ancestorName + " (" + roleName + ")";
        }

        return displayClass.getSimpleName() + " -> " + ancestorName + " (" + roleName + ")";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Algorithm (package-private for tests)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Walks up from {@code concrete} past every class {@link #shouldStrip} rejects.
     *
     * @return never {@code null}; the walk stops at {@link Object}
     */
    static Class<?> findDisplayClass(Class<?> concrete) {
        Class<?> c = concrete;
        while (c != null && c != Object.class && shouldStrip(c)) {
            c = c.getSuperclass();
        }
        return c != null ? c : concrete;
    }

    /**
     * True for a class whose name carries no stable identity for the model: anonymous,
     * synthetic, local, a runtime-generated proxy (CGLIB, ByteBuddy, Mockito, …), or a
     * Swing/AWT internal — an L&amp;F class like {@code MetalScrollButton}, or a nested
     * class like {@code JScrollPane.ScrollBar}.
     */
    static boolean shouldStrip(Class<?> c) {
        return c.isAnonymousClass()
                || c.isSynthetic()
                || c.isLocalClass()
                || isRuntimeProxy(c)
                || isPlafClass(c)
                || isJdkInternalNested(c);
    }

    /**
     * True for a class in {@code javax.swing.plaf} or below: walking past a
     * {@code BasicArrowButton} lands on the {@code JButton} the model knows.
     */
    static boolean isPlafClass(Class<?> c) {
        Package pkg = c.getPackage();
        if (pkg == null) {
            return false;
        }
        String pkgName = pkg.getName();
        return pkgName.equals("javax.swing.plaf") || pkgName.startsWith("javax.swing.plaf.");
    }

    /**
     * JDK-nested classes that are display classes and qualifying ancestors anyway, because
     * the snapshot renders them as themselves (D_desktop_icon_as_itself).
     */
    private static final Set<Class<?>> JDK_NESTED_CARVEOUTS = Set.of(
            javax.swing.JInternalFrame.JDesktopIcon.class
    );

    /**
     * True for a class nested inside a {@code javax.swing} or {@code java.awt} class
     * ({@code JScrollPane.ScrollBar}, {@code JSpinner.DefaultEditor}), except the
     * {@link #JDK_NESTED_CARVEOUTS}. A class nested in user code is kept.
     */
    static boolean isJdkInternalNested(Class<?> c) {
        if (JDK_NESTED_CARVEOUTS.contains(c)) {
            return false;
        }
        Class<?> enclosing = c.getEnclosingClass();
        if (enclosing == null) {
            return false;
        }
        Package pkg = enclosing.getPackage();
        if (pkg == null) {
            return false;
        }
        String pkgName = pkg.getName();
        return pkgName.equals("javax.swing")
                || pkgName.startsWith("javax.swing.")
                || pkgName.equals("java.awt")
                || pkgName.startsWith("java.awt.");
    }

    /**
     * True when the name holds two or more {@code $} and there is no enclosing class — a
     * genuine {@code Outer$Inner$Deep} has one, a runtime-generated proxy never does.
     */
    static boolean isRuntimeProxy(Class<?> c) {
        if (c.getEnclosingClass() != null) {
            return false;
        }
        long dollarCount = c.getName().chars().filter(ch -> ch == '$').count();
        return dollarCount >= 2;
    }

    /**
     * @return the first class from {@code displayClass} up that {@link #isQualifying}
     *         accepts, or {@code null} for a non-{@link Component} accessible
     */
    static @Nullable Class<?> findQualifyingAncestor(Class<?> displayClass) {
        Class<?> c = displayClass;
        while (c != null && c != Object.class) {
            if (isQualifying(c)) {
                return c;
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * True for a public, top-level {@link Component} or {@link MenuComponent} in
     * {@code javax.swing}, a non-plaf subpackage of it, or {@code java.awt} (not its
     * subpackages) — plus the {@link #JDK_NESTED_CARVEOUTS}. Abstract classes such as
     * {@code AbstractButton} qualify: the model knows them by name.
     */
    static boolean isQualifying(Class<?> c) {
        if (JDK_NESTED_CARVEOUTS.contains(c)) {
            return true;
        }
        if (!Modifier.isPublic(c.getModifiers())) {
            return false;
        }
        if (c.getEnclosingClass() != null) {
            return false;
        }
        Package pkg = c.getPackage();
        if (pkg == null) {
            return false;
        }
        String pkgName = pkg.getName();
        boolean packageOk =
                pkgName.equals("javax.swing")
                        || (pkgName.startsWith("javax.swing.") && !isPlafPackage(pkgName))
                        || pkgName.equals("java.awt");
        if (!packageOk) {
            return false;
        }
        return Component.class.isAssignableFrom(c)
                || MenuComponent.class.isAssignableFrom(c);
    }

    private static boolean isPlafPackage(String pkgName) {
        return pkgName.equals("javax.swing.plaf")
                || pkgName.startsWith("javax.swing.plaf.");
    }
}
