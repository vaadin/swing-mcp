package com.vaadin.swingmcp.mcp.tools;

import com.vaadin.swingmcp.mcp.SwingUtils;
import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPErrorResponseException;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;

import javax.accessibility.Accessible;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.WindowConstants;
import java.awt.Window;
import java.awt.event.WindowEvent;

/**
 * MCP tool {@code swing_close}: closes a window or dialog identified by ref.
 *
 * <p>Dispatches {@link WindowEvent#WINDOW_CLOSING} to the window, respecting the
 * application's {@link java.awt.event.WindowListener}s and
 * {@code defaultCloseOperation}. For windows with {@code DO_NOTHING_ON_CLOSE},
 * the event is still dispatched (custom listeners may still close the window),
 * but no PostVerification polling is performed.</p>
 *
 * @see <a href="use-case-011-swing-close.md">UC-011</a>
 */
public class SwingCloseTool extends AbstractSwingTool {

    @Override
    public String getName() {
        return TOOL_SWING_CLOSE;
    }

    @Override
    public String getDescription() {
        return "Close a window or dialog by ref. Call swing_snapshot first to obtain refs.";
    }

    @Override
    public MCPProtocol.InputSchema getInputSchema() {
        return new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number from swing_snapshot")
                .build();
    }

    @Override
    public MCPProtocol.Content execute(Parameters params, SwingToolContext context) throws Exception {
        // BR-01/BR-02: ref lookup
        int ref = params.getInt("ref");
        Accessible accessible = context.getAccessibleByRef(ref);

        // BR-05: check close support
        if (!SwingUtils.supportsClose(accessible)) {
            throw new MCPErrorResponseException(
                    "Component does not support close. Call swing_snapshot to verify the list of actions");
        }

        Window window = (Window) accessible;

        // BR-10: DO_NOTHING_ON_CLOSE — dispatch + synchronous isShowing() check, no PostVerification
        if (isDoNothingOnClose(window)) {
            window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
            if (!window.isShowing()) {
                return null;
            }
            return MCPProtocol.Content.text(
                    "Window close was requested but the window is still showing — it has DO_NOTHING_ON_CLOSE set or a WindowListener vetoed the close.");
        }

        // BR-03/BR-07: dispatch and set PostVerification polling
        window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
        postVerification = new PostVerification(
                new int[]{100, 200, 700},
                () -> !window.isShowing(),
                "Window close was requested but the window is still showing — a WindowListener may have vetoed the close, or the window is still closing."
        );
        return null;
    }

    private boolean isDoNothingOnClose(Window window) {
        if (window instanceof JFrame) {
            return ((JFrame) window).getDefaultCloseOperation() == WindowConstants.DO_NOTHING_ON_CLOSE;
        }
        if (window instanceof JDialog) {
            return ((JDialog) window).getDefaultCloseOperation() == WindowConstants.DO_NOTHING_ON_CLOSE;
        }
        return false;
    }

    @Override
    public boolean isMutation() {
        return true;
    }
}
