package com.vaadin.swingmcp;

import com.vaadin.swingmcp.tinymcpserver.InputSchemaBuilder;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DR-013: ToolDescriptor structural equality.
 */
class ToolDescriptorTest {

    @Test
    void recordRejectsNullFields() {
        MCPProtocol.InputSchema schema = new InputSchemaBuilder().build();
        assertThrows(NullPointerException.class,
                () -> new ToolDescriptor(null, "desc", schema));
        assertThrows(NullPointerException.class,
                () -> new ToolDescriptor("name", null, schema));
        assertThrows(NullPointerException.class,
                () -> new ToolDescriptor("name", "desc", null));
    }

    @Test
    void recordRejectsBlankName() {
        MCPProtocol.InputSchema schema = new InputSchemaBuilder().build();
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("", "desc", schema));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("  ", "desc", schema));
    }

    @Test
    void recordRejectsInvalidName() {
        MCPProtocol.InputSchema schema = new InputSchemaBuilder().build();
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("1tool", "desc", schema));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("my-tool", "desc", schema));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("my tool", "desc", schema));
    }

    @Test
    void recordRejectsBlankDescription() {
        MCPProtocol.InputSchema schema = new InputSchemaBuilder().build();
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("my_tool", "", schema));
        assertThrows(IllegalArgumentException.class,
                () -> new ToolDescriptor("my_tool", "  ", schema));
    }

    @Test
    void equalDescriptorsCompareEqual() {
        // Same logical content, built independently (different schema
        // instances, possibly different `properties` map iteration
        // orders) — must compare equal.
        ToolDescriptor a = new ToolDescriptor("swing_click",
                "Click a UI element by ref",
                new InputSchemaBuilder()
                        .requiredInteger("ref", "the ref")
                        .build());
        ToolDescriptor b = new ToolDescriptor("swing_click",
                "Click a UI element by ref",
                new InputSchemaBuilder()
                        .requiredInteger("ref", "the ref")
                        .build());
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentDescriptionDetected() {
        ToolDescriptor a = new ToolDescriptor("t", "old", new InputSchemaBuilder().build());
        ToolDescriptor b = new ToolDescriptor("t", "new", new InputSchemaBuilder().build());
        assertNotEquals(a, b);
    }

    @Test
    void differentSchemaDetected() {
        ToolDescriptor a = new ToolDescriptor("t", "d",
                new InputSchemaBuilder().requiredInteger("ref", "r").build());
        ToolDescriptor b = new ToolDescriptor("t", "d",
                new InputSchemaBuilder().requiredString("ref", "r").build());
        assertNotEquals(a, b);
    }
}
