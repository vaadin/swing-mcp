package com.vaadin.swingmcp.tinymcpserver;

/**
 * Functional interface for prompt handlers. Invoked when a client
 * requests {@code prompts/get}. Arguments have already been validated
 * against the registered schema (required / unknown-arg checks), so
 * implementations can read them directly.
 */
@FunctionalInterface
public interface PromptFunction {
    /**
     * @param request the request bundle (name, arguments, transport headers, JSON-RPC {@code _meta});
     *                {@link PromptRequest#arguments()} is always non-null and contains only declared
     *                keys — missing optional arguments are simply absent
     * @return the prompt result; must not be null
     * @throws MCPServerException to return a JSON-RPC protocol error
     * @throws Exception          if prompt expansion fails unexpectedly
     */
    MCPProtocol.GetPromptResult call(PromptRequest request) throws Exception;
}
