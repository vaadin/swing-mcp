/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
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
