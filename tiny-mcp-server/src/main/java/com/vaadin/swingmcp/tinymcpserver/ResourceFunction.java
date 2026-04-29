package com.vaadin.swingmcp.tinymcpserver;

import java.util.List;

/**
 * Functional interface for resource handlers. Invoked when a client
 * requests {@code resources/read} for a registered URI.
 *
 * <p>Implementations return the current contents of the resource. The
 * returned list must not be {@code null}; it typically contains a single
 * {@link MCPProtocol.ResourceContents} entry, but the MCP spec allows
 * multiple (e.g. for composite resources).
 *
 * <p>Throwing {@link MCPServerException} produces a JSON-RPC error with
 * the given code; any other exception becomes {@code INTERNAL_ERROR}.
 */
@FunctionalInterface
public interface ResourceFunction {
    /**
     * @param request the request bundle (uri, transport headers, JSON-RPC {@code _meta}); never null
     * @return the resource contents; must not be null
     * @throws MCPServerException to return a JSON-RPC protocol error
     * @throws Exception          if resource loading fails unexpectedly
     */
    List<MCPProtocol.ResourceContents> call(ResourceRequest request) throws Exception;
}
