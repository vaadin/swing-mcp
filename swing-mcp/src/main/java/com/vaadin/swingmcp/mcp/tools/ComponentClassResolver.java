package com.vaadin.swingmcp.mcp.tools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import java.awt.Component;
import java.awt.MenuComponent;
import java.lang.reflect.Modifier;
import java.util.Set;

/**
 * Resolves the component-identity slot for a snapshot line per
 * <b>UC-002 BR-11</b>. The slot takes one of three forms:
 *
 * <ul>
 *   <li><b>Case A (standard Swing component):</b>
 *       {@code JClass (role)} — e.g. {@code JButton (push_button)}.</li>
 *   <li><b>Case B (meaningful custom subclass):</b>
 *       {@code ConcreteSimpleName -> JClass (role)} — e.g.
 *       {@code SearchField -> JTextField (text)}.</li>
 *   <li><b>Case C (non-Component accessible):</b>
 *       {@code (role)} — e.g. {@code (page_tab)}. Applies to accessibles
 *       that do not descend from {@link Component} or {@link MenuComponent},
 *       such as {@code JTabbedPane.Page}, {@code JList.AccessibleJListChild},
 *       and {@code JTree.AccessibleJTreeNode}.</li>
 * </ul>
 *
 * <p>Parenthesised role is unconditional — even when the role is a
 * tautological lowercasing of the class ({@code JButton (push_button)}) —
 * so that a non-standard overridden role becomes a clean attention signal
 * for the AI (see UC-002 BR-11 rationale).</p>
 *
 * <p>See UC-002 § Implementation Notes — Component Identity Resolution
 * for the full algorithm and the audit test that guards against JDK drift.</p>
 */
public final class ComponentClassResolver {

    private ComponentClassResolver() {
    }

    /**
     * Returns the component-identity slot for the given accessible, including
     * the surrounding parentheses around the role. The caller appends this
     * directly after the list marker ({@code "- "}) of the snapshot line.
     *
     * <p>If the accessible has no {@link AccessibleContext} or its role cannot
     * be resolved, the role falls back to {@code "unknown"} (same fallback
     * used by {@link AccessibleNames#roleName}).</p>
     */
    public static String resolveIdentitySlot(Accessible accessible) {
        AccessibleContext ctx = accessible.getAccessibleContext();
        AccessibleRole role = ctx != null ? ctx.getAccessibleRole() : null;
        String roleName = role != null ? AccessibleNames.roleName(role) : "unknown";

        Class<?> concrete = accessible.getClass();
        Class<?> displayClass = findDisplayClass(concrete);
        Class<?> qualifying = findQualifyingAncestor(displayClass);

        if (qualifying == null) {
            // Case C — non-Component accessible (no javax.swing/java.awt ancestor).
            return "(" + roleName + ")";
        }

        String ancestorName = qualifying.getSimpleName();
        if (displayClass == qualifying) {
            // Case A — standard Swing / AWT component.
            return ancestorName + " (" + roleName + ")";
        }

        // Case B — meaningful custom subclass.
        return displayClass.getSimpleName() + " -> " + ancestorName + " (" + roleName + ")";
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Algorithm (package-private for tests)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Strips anonymous, synthetic, local, and runtime-proxy classes from the
     * head of the superclass chain. Returns the first display-worthy class
     * encountered. Every Java class chain terminates at {@link Object}, which
     * is never stripped, so this method always returns a non-null class.
     */
    static Class<?> findDisplayClass(Class<?> concrete) {
        Class<?> c = concrete;
        while (c != null && c != Object.class && shouldStrip(c)) {
            c = c.getSuperclass();
        }
        return c != null ? c : concrete;
    }

    /**
     * Returns true for classes whose names carry no stable identity for the AI:
     * anonymous, synthetic, local, runtime-generated proxies (CGLIB, Hibernate,
     * ByteBuddy, Mockito, …), and Swing/AWT implementation internals
     * ({@code javax.swing.plaf.*} L&amp;F classes like {@code MetalScrollButton},
     * and nested classes inside {@code javax.swing.*}/{@code java.awt.*} such
     * as {@code JScrollPane.ScrollBar}).
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
     * True when the class lives in {@code javax.swing.plaf} or a subpackage.
     * L&amp;F implementation classes ({@code BasicArrowButton},
     * {@code MetalScrollButton}, {@code BasicComboPopup}, …) are Swing
     * implementation detail, not the widget vocabulary the LLM knows;
     * walking past them lands on the real Swing widget ({@code JButton},
     * {@code JComboBox}).
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
     * True when the class is a nested class declared inside a
     * {@code javax.swing.*} or {@code java.awt.*} class — e.g.
     * {@code JScrollPane.ScrollBar}, {@code JSpinner.DefaultEditor}. These
     * are framework-internal components the end user did not write. User
     * nested classes (enclosing class in user packages) are preserved.
     */
    /**
     * Classes that are JDK-internal nested classes but should be preserved
     * as display classes because they are first-class snapshot citizens
     * (added to {@code SEMANTIC_ROLES}).
     */
    private static final Set<Class<?>> JDK_NESTED_CARVEOUTS = Set.of(
            javax.swing.JInternalFrame.JDesktopIcon.class
    );

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
     * Runtime-proxy heuristic: the class name contains two or more {@code $}
     * characters <b>and</b> {@link Class#getEnclosingClass()} is {@code null}.
     * Genuine nested classes (even {@code Outer$Inner$Deep}) have a non-null
     * enclosing class and are preserved; runtime-generated proxy classes have
     * no enclosing class by construction.
     */
    static boolean isRuntimeProxy(Class<?> c) {
        if (c.getEnclosingClass() != null) {
            return false;
        }
        long dollarCount = c.getName().chars().filter(ch -> ch == '$').count();
        return dollarCount >= 2;
    }

    /**
     * Walks from {@code displayClass} up the superclass chain looking for a
     * class that satisfies the qualifying-ancestor predicate. Returns
     * {@code null} when no ancestor qualifies — indicating a non-Component
     * accessible (BR-11 Case C).
     */
    static Class<?> findQualifyingAncestor(Class<?> displayClass) {
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
     * Qualifying-ancestor predicate per UC-002 BR-11:
     * <ul>
     *   <li>public;</li>
     *   <li>top-level (no enclosing class — excludes nested, inner, local,
     *       anonymous);</li>
     *   <li>package is {@code javax.swing}, a subpackage of {@code javax.swing}
     *       other than {@code javax.swing.plaf} (and its subpackages), or
     *       {@code java.awt};</li>
     *   <li>assignable to {@link Component} or {@link MenuComponent}.</li>
     * </ul>
     * Abstract classes qualify — e.g. {@code AbstractButton},
     * {@code JTextComponent} — because the AI recognises them even when not
     * directly instantiable.
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
