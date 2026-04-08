package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleText;

/**
 * MCP tool {@code swing_get_text}: reads the text content of a UI component by ref.
 *
 * <p>Looks up the component by ref, verifies it supports the {@code get_text} action,
 * then reads the text via the accessibility API.</p>
 *
 * @see <a href="use-case-005-swing-get-text.md">UC-005</a>
 */
public class SwingGetTextTool extends AbstractSwingTool {

    static final int MAX_TEXT_LENGTH = 1000;

    @Override
    public String getName() {
        return TOOL_SWING_GET_TEXT;
    }

    @Override
    public String getDescription() {
        return "Read the text content of a UI component by ref. Requires a ref obtained from swing_snapshot or swing_get_cells.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot or swing_get_cells")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01: ref is required integer
        int ref = params.getInt("ref");

        // BR-02: look up the accessible by ref (throws MCPServerException if not found)
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-04: check get_text support
        if (!SwingUtils.supportsGetText(accessible)) {
            throw new MCPErrorResponseException(
                    "Component does not support get_text. Call swing_snapshot to verify the list of actions");
        }

        // BR-05: all access happens on EDT (guaranteed by MCPServer.registerTool)
        AccessibleContext ac = accessible.getAccessibleContext();
        AccessibleText at = ac.getAccessibleText();

        // Step 4: get total character count
        int len = at.getCharCount();

        // BR-08: empty text returns explicit empty string
        if (len == 0) {
            return MCPProtocol.Content.text("");
        }

        // BR-09: cap at MAX_TEXT_LENGTH
        int readLen = Math.min(len, MAX_TEXT_LENGTH);

        String text;

        // Step 7: primary path via AccessibleEditableText
        var editableText = ac.getAccessibleEditableText();
        if (editableText != null) {
            text = editableText.getTextRange(0, readLen);
        } else {
            // Step 8: fallback — character by character
            StringBuilder sb = new StringBuilder(readLen);
            for (int i = 0; i < readLen; i++) {
                String ch = at.getAtIndex(AccessibleText.CHARACTER, i);
                if (ch != null) {
                    sb.append(ch);
                }
            }
            text = sb.toString();
        }

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
