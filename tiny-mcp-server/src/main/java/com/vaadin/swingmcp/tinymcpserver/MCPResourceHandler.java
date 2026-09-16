package com.vaadin.swingmcp.tinymcpserver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the resource registry and handles {@code resources/list} and
 * {@code resources/read} JSON-RPC methods. Parallel to {@link MCPToolHandler}
 * and {@link MCPPromptHandler}.
 * <p>
 * Resources are keyed by their URI. Registration captures a static descriptor
 * (name, description, mimeType) exposed via {@code resources/list}; the
 * {@link ResourceFunction} is invoked for {@code resources/read}
 * and returns the current contents.
 * <p>
 * The {@code handle*} methods are transport-agnostic: they consume parsed
 * JSON-RPC requests and return the corresponding result POJO. The caller
 * (HTTP or stdio transport) writes the response.
 */
class MCPResourceHandler {

    private static final Logger LOG = Logger.getLogger(MCPResourceHandler.class.getName());

    private static class RegisteredResource {
        final ResourceFunction function;
        final MCPProtocol.Resource descriptor;

        RegisteredResource(String uri, String name, String description, String mimeType,
                ResourceFunction function) {
            this.function = function;
            this.descriptor = new MCPProtocol.Resource();
            this.descriptor.setUri(uri);
            this.descriptor.setName(name);
            this.descriptor.setDescription(description);
            this.descriptor.setMimeType(mimeType);
        }
    }

    private final Map<String, RegisteredResource> resources = new LinkedHashMap<>();

    /**
     * Registers a resource. Must be called before the server is started.
     *
     * @param uri         the resource URI; not null, not blank; used as the
     *                    lookup key for {@code resources/read}
     * @param name        human-readable resource name; not null, not blank
     * @param description human-readable description; may be null
     * @param mimeType    the resource MIME type; may be null
     * @param function    the handler to invoke for {@code resources/read};
     *                    not null
     * @throws IllegalArgumentException if {@code uri}, {@code name}, or
     *                                  {@code function} is null/blank
     * @throws IllegalStateException    if a resource with the same URI is
     *                                  already registered
     */
    void addResource(String uri, String name, String description, String mimeType,
            ResourceFunction function) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Resource URI must not be null or blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Resource name must not be null or blank");
        }
        if (function == null) {
            throw new IllegalArgumentException("ResourceFunction must not be null");
        }
        if (resources.containsKey(uri)) {
            throw new IllegalStateException("A resource with URI '" + uri + "' is already registered");
        }
        resources.put(uri, new RegisteredResource(uri, name, description, mimeType, function));
    }

    MCPProtocol.ListResourcesResult handleResourcesList() {
        MCPProtocol.ListResourcesResult result = new MCPProtocol.ListResourcesResult();
        List<MCPProtocol.Resource> descriptors = new ArrayList<>();
        for (RegisteredResource rr : resources.values()) {
            descriptors.add(rr.descriptor);
        }
        result.setResources(descriptors);
        return result;
    }

    /**
     * Dispatches {@code resources/read}. Resource handlers have no
     * tool-layer "isError" channel (D_three_error_layers layer 3 is tools-only), so any
     * failure becomes a JSON-RPC protocol error.
     *
     * @param request          the parsed JSON-RPC request envelope
     * @param transportHeaders headers from the underlying transport
     */
    MCPProtocol.ReadResourceResult handleResourcesRead(MCPProtocol.JsonRpcRequest request,
            Map<String, String> transportHeaders) {
        MCPProtocol.ReadResourceParams params = request.getParamsAs(MCPProtocol.ReadResourceParams.class);
        if (params == null || params.getUri() == null || params.getUri().isBlank()) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Missing required parameter: uri");
        }
        String uri = params.getUri();
        RegisteredResource resource = resources.get(uri);
        if (resource == null) {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS,
                    "Unknown resource: " + uri);
        }

        ResourceRequest req = new ResourceRequest(uri,
                transportHeaders,
                request.getMeta());
        List<MCPProtocol.ResourceContents> contents;
        try {
            contents = resource.function.call(req);
        } catch (MCPServerException e) {
            throw e;
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Resource '" + uri + "' threw an exception", e);
            throw new MCPServerException(MCPServerException.INTERNAL_ERROR,
                    "Resource '" + uri + "' failed: " + e);
        }
        if (contents == null) {
            throw new MCPServerException(MCPServerException.INTERNAL_ERROR,
                    "Resource '" + uri + "' returned null");
        }

        MCPProtocol.ReadResourceResult result = new MCPProtocol.ReadResourceResult();
        result.setContents(contents);
        return result;
    }
}
