package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

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
 * @see <a href="tool-013-swing-set-value.md">T-013</a>
 */
public class SwingSetValueTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_SET_VALUE;
    }

    @Override
    public String getDescription() {
        return "Set the numeric value of a UI component by ref. Call swing_get_value first to check "
                + "the current value and valid range. Requires a ref obtained from swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                .requiredNumber("value", "The numeric value to set")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: both params required
        int ref = params.getInt("ref");
        Number value = params.getNumber("value");

        // BR-02: ref lookup
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-03: set_value support check
        if (!SwingUtils.supportsSetValue(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_set_value. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // BR-05: effectively enabled check
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be modified");
        }

        // BR-04: all access on EDT (guaranteed by MCPServer.registerTool)
        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleValue av = ac.getAccessibleValue();

        // BR-07: range validation
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

        // BR-08: type preservation — determine target class from current value
        Number current = av.getCurrentAccessibleValue();
        Number convertedValue = convertToType(value, current);

        // BR-04: fire-and-forget dispatch
        SwingUtilities.invokeLater(() -> av.setCurrentAccessibleValue(convertedValue));
        // BR-09: DR-010 success echo — echo post-conversion value
        return echo(ref, renderEchoNumber(convertedValue));
    }

    /**
     * Converts the incoming value to the same Java numeric type as the current value.
     * This prevents type contamination in models like {@code SpinnerNumberModel}.
     *
     * @see <a href="tool-013-swing-set-value.md">T-013 BR-08, BR-11</a>
     */
    static Number convertToType(Number value, Number current) {
        if (current == null) {
            return value;
        }

        double dValue = value.doubleValue();

        if (current instanceof Integer || current instanceof Long) {
            // BR-11: reject fractional values for integer types
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
        // BR-06: mutation tool, ref map IS cleared
        return true;
    }
}
