package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleValue;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP tool {@code swing_get_value}: reads the numeric value of a UI component by ref.
 *
 * <p>Looks up the component by ref, verifies it supports the {@code get_value} action,
 * then reads the value via the accessibility API ({@link AccessibleValue}).</p>
 *
 * @see <a href="tool-012-swing-get-value.md">T-012</a>
 */
public class SwingGetValueTool extends AbstractSwingTool {

    public SwingGetValueTool() {
        super(SwingTools.SWING_GET_VALUE);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: ref is required integer
        int ref = params.getInt("ref");

        // BR-02: look up the accessible by ref (throws MCPServerException if not found)
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-03: check get_value support
        if (!SwingUtils.supportsGetValue(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_get_value. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // BR-04: all access happens on EDT (guaranteed by SwingMCP.registerTool)
        // Step 4: read current value via the shared helper (BR-12 / DR-013
        // shared-read with snapshot inline preview). supportsGetValue already
        // verified getCurrentAccessibleValue() is non-null so readValue
        // succeeds here; the IllegalStateException branch is a gate-violation
        // safety net.
        Number current = SwingUtils.readValue(accessible);

        // Steps 5-6: read optional min/max
        AccessibleValue av = accessible.getAccessibleContext().getAccessibleValue();
        Number min = av.getMinimumAccessibleValue();
        Number max = av.getMaximumAccessibleValue();

        // Steps 7-8: build JSON via Content.json()
        Map<String, Number> result = new LinkedHashMap<>();
        result.put("current", SwingUtils.serializeNumber(current));
        if (min != null) {
            result.put("min", SwingUtils.serializeNumber(min));
        }
        if (max != null) {
            result.put("max", SwingUtils.serializeNumber(max));
        }

        return MCPProtocol.Content.json(result);
    }

    @Override
    public boolean isMutation() {
        // BR-05: read-only tool, ref map is NOT cleared
        return false;
    }
}
