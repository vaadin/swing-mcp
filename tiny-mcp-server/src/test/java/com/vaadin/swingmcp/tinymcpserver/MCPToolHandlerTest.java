package com.vaadin.swingmcp.tinymcpserver;

import com.vaadin.swingmcp.ToolDescriptor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MCPToolHandler} tool registration.
 * Field-level descriptor validation lives in
 * {@link com.vaadin.swingmcp.ToolDescriptorTest}; this class only covers
 * registration-level invariants.
 */
class MCPToolHandlerTest {

    private static ToolDescriptor descriptor(String name) {
        return new ToolDescriptor(name, "desc", new InputSchemaBuilder().build());
    }

    @Test
    void addToolRejectsNullDescriptor() {
        MCPToolHandler handler = new MCPToolHandler();
        assertThrows(NullPointerException.class, () ->
                handler.addTool(null, request -> null));
    }

    @Test
    void addToolRejectsNullFunction() {
        MCPToolHandler handler = new MCPToolHandler();
        assertThrows(NullPointerException.class, () ->
                handler.addTool(descriptor("my_tool"), null));
    }

    @Test
    void addToolDuplicateNameThrows() {
        MCPToolHandler handler = new MCPToolHandler();
        handler.addTool(descriptor("my_tool"), request -> null);
        assertThrows(IllegalStateException.class, () ->
                handler.addTool(descriptor("my_tool"), request -> null));
    }
}
