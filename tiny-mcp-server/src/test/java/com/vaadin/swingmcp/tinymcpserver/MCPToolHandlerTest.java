package com.vaadin.swingmcp.tinymcpserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MCPToolHandler} tool registration validation.
 */
class MCPToolHandlerTest {

    @Test
    void addToolRejectsNullName() {
        MCPToolHandler handler = new MCPToolHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addTool(null, "desc", new InputSchemaBuilder().build(), request -> null));
    }

    @Test
    void addToolRejectsBlankName() {
        MCPToolHandler handler = new MCPToolHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addTool("  ", "desc", new InputSchemaBuilder().build(), request -> null));
    }

    @Test
    void addToolRejectsInvalidName() {
        MCPToolHandler handler = new MCPToolHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addTool("1tool", "desc", new InputSchemaBuilder().build(), request -> null));
        assertThrows(IllegalArgumentException.class, () ->
                handler.addTool("my-tool", "desc", new InputSchemaBuilder().build(), request -> null));
        assertThrows(IllegalArgumentException.class, () ->
                handler.addTool("my tool", "desc", new InputSchemaBuilder().build(), request -> null));
    }

    @Test
    void addToolRejectsNullDescription() {
        MCPToolHandler handler = new MCPToolHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addTool("my_tool", null, new InputSchemaBuilder().build(), request -> null));
    }

    @Test
    void addToolRejectsBlankDescription() {
        MCPToolHandler handler = new MCPToolHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addTool("my_tool", "  ", new InputSchemaBuilder().build(), request -> null));
    }

    @Test
    void addToolRejectsNullInputSchema() {
        MCPToolHandler handler = new MCPToolHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addTool("my_tool", "desc", null, request -> null));
    }

    @Test
    void addToolRejectsNullFunction() {
        MCPToolHandler handler = new MCPToolHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addTool("my_tool", "desc", new InputSchemaBuilder().build(), null));
    }

    @Test
    void addToolDuplicateNameThrows() {
        MCPToolHandler handler = new MCPToolHandler();
        handler.addTool("my_tool", "desc", new InputSchemaBuilder().build(), request -> null);
        assertThrows(IllegalStateException.class, () ->
                handler.addTool("my_tool", "other desc", new InputSchemaBuilder().build(), request -> null));
    }
}
