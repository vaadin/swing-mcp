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

import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleState;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for resolving {@link AccessibleRole} and {@link AccessibleState} instances
 * to their field names (lowercased). Since neither class is an enum, resolution is done
 * via reflection over the declared public static fields, performed once at class-load time.
 * <p>
 * Unknown instances (custom subclasses not matching any declared field) fall back to {@code "unknown"}.
 */
public final class AccessibleNames {

    /** Map from {@link AccessibleRole} instance to its lowercased field name (e.g. {@code PUSH_BUTTON} → {@code "push_button"}). */
    public static final Map<AccessibleRole, String> ROLE_NAMES;

    /** Map from {@link AccessibleState} instance to its lowercased field name (e.g. {@code DISABLED} → {@code "disabled"}). */
    public static final Map<AccessibleState, String> STATE_NAMES;

    static {
        ROLE_NAMES = buildMap(AccessibleRole.class);
        STATE_NAMES = buildMap(AccessibleState.class);
    }

    private AccessibleNames() {
    }

    @SuppressWarnings("unchecked")
    private static <T> Map<T, String> buildMap(Class<T> clazz) {
        Map<T, String> map = new HashMap<>();
        for (Field field : clazz.getDeclaredFields()) {
            int mods = field.getModifiers();
            if (Modifier.isStatic(mods) && Modifier.isPublic(mods)
                    && clazz.isAssignableFrom(field.getType())) {
                try {
                    T value = (T) field.get(null);
                    if (value != null) {
                        map.put(value, field.getName().toLowerCase());
                    }
                } catch (IllegalAccessException e) {
                    // skip inaccessible fields
                }
            }
        }
        return Collections.unmodifiableMap(map);
    }

    /**
     * Returns the lowercased field name for the given role, or {@code "unknown"} if not found.
     */
    public static String roleName(AccessibleRole role) {
        return ROLE_NAMES.getOrDefault(role, "unknown");
    }

    /**
     * Returns the lowercased field name for the given state, or {@code "unknown"} if not found.
     */
    public static String stateName(AccessibleState state) {
        return STATE_NAMES.getOrDefault(state, "unknown");
    }
}
