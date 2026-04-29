package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;

/**
 * MCP tool {@code swing_get_description}: reads the full description of a UI
 * component by ref.
 *
 * <p>The snapshot caps descriptions at 120 characters (BR-10 / DR-014). When
 * the AI needs the full text it calls this tool. The description is resolved
 * using the same logic as the snapshot description slot: accessible description
 * first, tooltip fallback second, HTML cleanup and sanitisation applied —
 * but without the 120-character cap.</p>
 *
 * @see <a href="tool-024-swing-get-description.md">T-024</a>
 */
public class SwingGetDescriptionTool extends AbstractSwingTool {

    static final int MAX_DESCRIPTION_LENGTH = 1000;

    public SwingGetDescriptionTool() {
        super(SwingTools.SWING_GET_DESCRIPTION);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: ref is required integer
        int ref = params.getInt("ref");

        // BR-02: look up the accessible by ref (throws MCPServerException if not found)
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-03: resolve description using the same logic as the snapshot
        // (accessible description -> tooltip fallback -> HTML cleanup -> sanitize).
        // BR-04: no gate — every component has a description (possibly empty).
        String desc = SwingUtils.resolveDescription(accessible);

        if (desc == null || desc.isEmpty()) {
            // BR-04: explicit empty string, not null
            return MCPProtocol.Content.text("");
        }

        // BR-07: cap at MAX_DESCRIPTION_LENGTH with truncation notice
        if (desc.length() > MAX_DESCRIPTION_LENGTH) {
            int totalLength = desc.length();
            desc = desc.substring(0, MAX_DESCRIPTION_LENGTH)
                    + "\n... (truncated, " + totalLength + " total characters)";
        }

        return MCPProtocol.Content.text(desc);
    }

    @Override
    public boolean isMutation() {
        // BR-06: read-only tool, ref map is NOT cleared
        return false;
    }
}
