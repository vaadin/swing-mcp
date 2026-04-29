package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.swing.SwingUtilities;

/**
 * MCP tool {@code swing_click}: clicks a UI component identified by ref.
 *
 * <p>Looks up the component by ref, verifies it supports the click action
 * and is effectively enabled, then invokes the click via the {@link Runnable}
 * returned by {@link SwingUtils#supportsClick(Accessible)}.</p>
 *
 * @see <a href="tool-004-swing-click.md">T-004</a>
 */
public class SwingClickTool extends AbstractSwingTool {

    public SwingClickTool() {
        super(SwingTools.SWING_CLICK);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: ref is required integer
        int ref = params.getInt("ref");

        // BR-02: look up the accessible by ref (throws MCPServerException if not found)
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-06: check click support (Tier 1: AccessibleAction, Tier 2: MouseListener)
        Runnable click = SwingUtils.supportsClick(accessible);
        if (click == null) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_click. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // BR-05: check effectively enabled
        if (!SwingUtils.isEffectivelyEnabled(accessible)) {
            throw new MCPErrorResponseException(
                    "Component is disabled and cannot be clicked");
        }

        // BR-03: fire the click action asynchronously (fire-and-forget)
        SwingUtilities.invokeLater(click);
        // BR-07: DR-010 success echo
        return echo(ref);
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
