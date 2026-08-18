package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import com.vaadin.swingmcp.tinymcpserver.Parameters;
import com.vaadin.swingmcp.tools.SwingTools;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;

/**
 * MCP tool {@code swing_get_text}: reads the text content of a UI component by ref.
 *
 * <p>Looks up the component by ref, verifies it supports the {@code get_text} action,
 * then reads the text via the accessibility API.</p>
 *
 * @see <a href="tool-005-swing-get-text.md">T-005</a>
 */
public class SwingGetTextTool extends AbstractSwingTool {

    static final int MAX_TEXT_LENGTH = 1000;

    public SwingGetTextTool() {
        super(SwingTools.SWING_GET_TEXT);
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: ref is required integer
        int ref = params.getInt("ref");

        // BR-02: look up the accessible by ref (throws MCPServerException if not found)
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-06 (DR-password-not-readable): password-role accessibles are not readable.
        // This check runs before BR-04 so the AI gets the specific rule rather than
        // the generic "does not support swing_get_text".
        if (SwingUtils.hasPasswordRole(accessible)) {
            throw new MCPErrorResponseException(
                    "JPasswordField content is not readable. Use swing_set_text if you need to write a known value.");
        }

        // BR-04: check get_text support
        if (!SwingUtils.supportsGetText(accessible)) {
            throw new MCPErrorResponseException(
                    ComponentClassResolver.resolveClassName(accessible)
                            + " does not support swing_get_text. Call swing_snapshot or swing_get_cells to verify the list of actions");
        }

        // BR-05: all access happens on EDT (guaranteed by SwingMCP.registerTool)
        // Step 4: get total character count — needed independently of the
        // read itself to compose BR-09's truncation notice with the real total.
        AccessibleContext ac = accessible.getAccessibleContext();
        int len = ac.getAccessibleText().getCharCount();

        // BR-08: empty text returns explicit empty string
        if (len == 0) {
            return MCPProtocol.Content.text("");
        }

        // Shared read path with snapshot inline preview (BR-12 / DR-inline-value-preview):
        // SwingUtils.readText caps at MAX_TEXT_LENGTH and handles both the
        // primary AccessibleEditableText.getTextRange path and the
        // AccessibleText.getAtIndex fallback.
        String text = SwingUtils.readText(accessible, MAX_TEXT_LENGTH);

        // BR-09: append truncation notice if needed
        if (len > MAX_TEXT_LENGTH) {
            text = text + "\n... (truncated, " + len + " total characters)";
        }

        return MCPProtocol.Content.text(text);
    }

    @Override
    public boolean isMutation() {
        // BR-07: read-only tool, ref map is NOT cleared
        return false;
    }
}
