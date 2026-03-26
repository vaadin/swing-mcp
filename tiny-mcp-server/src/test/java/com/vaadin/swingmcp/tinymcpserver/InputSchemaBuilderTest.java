package com.vaadin.swingmcp.tinymcpserver;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InputSchemaBuilderTest {

    @Test
    void requiredIntegerToString() {
        assertEquals("ref: integer",
                new InputSchemaBuilder()
                        .requiredInteger("ref", "The element reference number")
                        .toString());
    }

    @Test
    void allTypesToString() {
        assertEquals("a: integer, b: string, c: number, d: boolean",
                new InputSchemaBuilder()
                        .requiredInteger("a", "int param")
                        .requiredString("b", "string param")
                        .requiredNumber("c", "number param")
                        .requiredBoolean("d", "boolean param")
                        .toString());
    }

    @Test
    void optionalTypesToString() {
        assertEquals("x: string, y: number, z: boolean",
                new InputSchemaBuilder()
                        .optionalString("x", "optional string")
                        .optionalNumber("y", "optional number")
                        .optionalBoolean("z", "optional boolean")
                        .toString());
    }

    @Test
    void fluentChainingPreservesInsertionOrder() {
        assertEquals("a: integer, b: integer, ref: string",
                new InputSchemaBuilder()
                        .requiredInteger("a", "first")
                        .requiredInteger("b", "second")
                        .requiredString("ref", "third")
                        .toString());
    }

    @Test
    void buildProducesCorrectType() {
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number")
                .build();
        assertEquals("object", schema.getType());
    }

    @Test
    void buildProducesCorrectProperties() {
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredInteger("ref", "The element reference number")
                .requiredString("name", "The element name")
                .build();

        Map<String, MCPProtocol.PropertySchema> props = schema.getProperties();
        assertNotNull(props);
        assertEquals(2, props.size());

        assertEquals("integer", props.get("ref").getType());
        assertEquals("The element reference number", props.get("ref").getDescription());

        assertEquals("string", props.get("name").getType());
        assertEquals("The element name", props.get("name").getDescription());
    }

    @Test
    void buildProducesCorrectRequiredList() {
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredInteger("a", "required int")
                .optionalString("b", "optional string")
                .requiredBoolean("c", "required bool")
                .build();

        List<String> required = schema.getRequired();
        assertNotNull(required);
        assertEquals(List.of("a", "c"), required);
    }

    @Test
    void buildWithNoRequiredFieldsProducesNullRequiredList() {
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .optionalString("x", "opt")
                .optionalInteger("y", "opt2")
                .build();

        assertNull(schema.getRequired());
    }

    @Test
    void buildWithAllOptionalTypesHasCorrectPropertyTypes() {
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .optionalString("s", "s")
                .optionalInteger("i", "i")
                .optionalNumber("n", "n")
                .optionalBoolean("b", "b")
                .build();

        Map<String, MCPProtocol.PropertySchema> props = schema.getProperties();
        assertEquals("string", props.get("s").getType());
        assertEquals("integer", props.get("i").getType());
        assertEquals("number", props.get("n").getType());
        assertEquals("boolean", props.get("b").getType());
    }

    @Test
    void toStringOmitsDescription() {
        String result = new InputSchemaBuilder()
                .requiredString("name", "A very long description that should not appear")
                .toString();
        assertFalse(result.contains("description"));
        assertFalse(result.contains("long"));
        assertEquals("name: string", result);
    }

    @Test
    void emptyBuilderToStringIsEmpty() {
        assertEquals("", new InputSchemaBuilder().toString());
    }

    @Test
    void duplicateParameterThrows() {
        assertThrows(IllegalStateException.class, () ->
                new InputSchemaBuilder()
                        .requiredInteger("ref", "first")
                        .requiredString("ref", "duplicate"));
    }
}
