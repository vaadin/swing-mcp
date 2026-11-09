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

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.github.mvysny.tinymcpserver.MCPErrorResponseException;
import com.github.mvysny.tinymcpserver.MCPProtocol;
import com.github.mvysny.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;
import org.jspecify.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleValue;
import javax.swing.SwingUtilities;
import java.math.BigDecimal;

/**
 * MCP tool {@code swing_set_value}: sets a component's {@link AccessibleValue} by ref, converted
 * to the current value's numeric type. Refuses a disabled component, a value outside
 * {@code [min, max]}, and a fraction for an integer-typed value.
 */
public class SwingSetValueTool extends AbstractSwingTool {

    public SwingSetValueTool() {
        super(SwingTools.SWING_SET_VALUE);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        int ref = params.getInt("ref");
        Number value = params.getNumber("value");

        Accessible accessible = context.getAccessibleByRef(ref);

        if (!SwingUtils.supportsSetValue(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_set_value. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be modified");
        }

        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleValue av = ac.getAccessibleValue();

        Number min = av.getMinimumAccessibleValue();
        Number max = av.getMaximumAccessibleValue();
        double dValue = value.doubleValue();

        if (min != null && dValue < min.doubleValue()) {
            throw new MCPErrorResponseException(
                    "Value " + SwingUtils.serializeNumber(value) + " is below the minimum ("
                            + SwingUtils.serializeNumber(min) + "). Call swing_get_value to check the valid range.");
        }
        if (max != null && dValue > max.doubleValue()) {
            throw new MCPErrorResponseException(
                    "Value " + SwingUtils.serializeNumber(value) + " is above the maximum ("
                            + SwingUtils.serializeNumber(max) + "). Call swing_get_value to check the valid range.");
        }

        Number current = av.getCurrentAccessibleValue();
        Number convertedValue = convertToType(value, current);

        SwingUtilities.invokeLater(() -> av.setCurrentAccessibleValue(convertedValue));
        return echo(ref, renderEchoNumber(convertedValue));
    }

    /**
     * Converts {@code value} to {@code current}'s numeric type: a {@code SpinnerNumberModel}
     * stores whatever {@code Number} it is handed, and a {@code Double} left in an int-typed
     * model breaks the spinner's own stepping (R_accessible_value_types).
     *
     * @param current the component's current value, or {@code null} to pass {@code value} as-is;
     *                an unknown {@code Number} type passes it as-is too
     * @throws MCPErrorResponseException if {@code value} is fractional and {@code current} is an
     *         {@code Integer} or {@code Long}
     */
    static Number convertToType(Number value, @Nullable Number current) {
        if (current == null) {
            return value;
        }

        double dValue = value.doubleValue();

        if (current instanceof Integer || current instanceof Long) {
            if (dValue % 1 != 0) {
                throw new MCPErrorResponseException(
                        "Value " + SwingUtils.serializeNumber(value)
                                + " cannot be set \u2014 this component requires a whole number.");
            }
        }

        if (current instanceof Integer) {
            return value.intValue();
        } else if (current instanceof Long) {
            return value.longValue();
        } else if (current instanceof Float) {
            return value.floatValue();
        } else if (current instanceof Double) {
            return value.doubleValue();
        } else if (current instanceof BigDecimal) {
            return BigDecimal.valueOf(value.doubleValue());
        }

        return value;
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
