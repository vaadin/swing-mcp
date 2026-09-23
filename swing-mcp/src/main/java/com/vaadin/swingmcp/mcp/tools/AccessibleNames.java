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
 * Names an {@link AccessibleRole} or {@link AccessibleState} by its lowercased field name —
 * {@code AccessibleRole.PUSH_BUTTON} → {@code "push_button"} — never by the localized
 * {@code toDisplayString()} (D_role_in_snapshot_only). Neither class is an enum, so the
 * public static fields are reflected once, at class load.
 */
public final class AccessibleNames {

    public static final Map<AccessibleRole, String> ROLE_NAMES;

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
     * @return {@code "unknown"} for a role no {@link AccessibleRole} field declares, such as a
     *         custom subclass's own constant
     */
    public static String roleName(AccessibleRole role) {
        return ROLE_NAMES.getOrDefault(role, "unknown");
    }

    /**
     * @return {@code "unknown"} for a state no {@link AccessibleState} field declares
     */
    public static String stateName(AccessibleState state) {
        return STATE_NAMES.getOrDefault(state, "unknown");
    }
}
