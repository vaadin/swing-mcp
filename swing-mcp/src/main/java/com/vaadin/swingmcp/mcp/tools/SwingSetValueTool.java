package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleValue;
import javax.swing.SwingUtilities;
import java.math.BigDecimal;

/**
 * MCP tool {@code swing_set_value}: sets the numeric value of a UI component by ref.
 *
 * <p>Looks up the component by ref, verifies it supports {@code set_value}, is effectively
 * enabled, validates the value is within range, preserves the model's numeric type, then
 * delegates to {@link AccessibleValue#setCurrentAccessibleValue(Number)} via fire-and-forget.</p>
 *
 */
public class SwingSetValueTool extends AbstractSwingTool {

    public SwingSetValueTool() {
        super(SwingTools.SWING_SET_VALUE);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // both params required
        int ref = params.getInt("ref");
        Number value = params.getNumber("value");

        // ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // set_value support check
        if (!SwingUtils.supportsSetValue(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_set_value. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be modified");
        }

        // all access on EDT (guaranteed by SwingMCP.registerTool)
        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleValue av = ac.getAccessibleValue();

        // range validation
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

        // type preservation — determine target class from current value
        Number current = av.getCurrentAccessibleValue();
        Number convertedValue = convertToType(value, current);

        // fire-and-forget dispatch
        SwingUtilities.invokeLater(() -> av.setCurrentAccessibleValue(convertedValue));
        // D_dispatched_echo success echo — echo post-conversion value
        return echo(ref, renderEchoNumber(convertedValue));
    }

    /**
     * Converts the incoming value to the same Java numeric type as the current value.
     * This prevents type contamination in models like {@code SpinnerNumberModel}.
     *
     * @implNote The current value's class is read at call time, not cached, so a model whose
     *           type changed since the last read still gets the right one (R_accessible_value_types).
     */
    static Number convertToType(Number value, Number current) {
        if (current == null) {
            return value;
        }

        double dValue = value.doubleValue();

        if (current instanceof Integer || current instanceof Long) {
            // reject fractional values for integer types
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

        // Unknown type — pass as-is
        return value;
    }

    @Override
    public boolean isMutation() {
        // mutation tool, ref map IS cleared
        return true;
    }
}
