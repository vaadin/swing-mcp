package com.vaadin.swingmcp.tinymcpserver;

import com.vaadin.swingmcp.tinymcpserver.MCPProtocol.InputSchema;
import com.vaadin.swingmcp.tinymcpserver.MCPProtocol.PropertySchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DR-structural-schema-equality: InputSchema and PropertySchema implement deep structural equality.
 * Drift detection in MCPProxy compares descriptors via these methods, so this
 * is the contract test for the underlying equality predicate.
 */
class InputSchemaEqualityTest {

    @Test
    void emptySchemasAreEqual() {
        assertEquals(new InputSchema(), new InputSchema());
        assertEquals(new InputSchema().hashCode(), new InputSchema().hashCode());
    }

    @Test
    void differentTypesAreNotEqual() {
        InputSchema a = new InputSchema();
        a.setType("object");
        InputSchema b = new InputSchema();
        b.setType("array");
        assertNotEquals(a, b);
    }

    @Test
    void requiredCompareAsSet_ignoresOrder() {
        InputSchema a = new InputSchema();
        a.setRequired(List.of("ref", "value"));
        InputSchema b = new InputSchema();
        b.setRequired(List.of("value", "ref"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void requiredCompareAsSet_detectsDifferentMembers() {
        InputSchema a = new InputSchema();
        a.setRequired(List.of("ref"));
        InputSchema b = new InputSchema();
        b.setRequired(List.of("value"));
        assertNotEquals(a, b);
    }

    @Test
    void requiredCompareAsSet_detectsCardinalityMismatch() {
        InputSchema a = new InputSchema();
        a.setRequired(List.of("ref"));
        InputSchema b = new InputSchema();
        b.setRequired(List.of("ref", "value"));
        assertNotEquals(a, b);
    }

    @Test
    void requiredNullVsEmpty_distinguished() {
        // null and empty are different — both are valid wire shapes.
        InputSchema empty = new InputSchema();
        empty.setRequired(List.of());
        InputSchema noField = new InputSchema();
        assertNotEquals(empty, noField);
    }

    @Test
    void propertyMapEqualsRegardlessOfInsertionOrder() {
        InputSchema a = new InputSchema();
        Map<String, PropertySchema> aProps = new LinkedHashMap<>();
        aProps.put("ref", stringProp("the ref"));
        aProps.put("value", stringProp("the value"));
        a.setProperties(aProps);

        InputSchema b = new InputSchema();
        Map<String, PropertySchema> bProps = new LinkedHashMap<>();
        bProps.put("value", stringProp("the value"));
        bProps.put("ref", stringProp("the ref"));
        b.setProperties(bProps);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void propertyDescriptionDifferenceDetected() {
        InputSchema a = new InputSchema();
        a.setProperties(Map.of("ref", stringProp("the ref")));
        InputSchema b = new InputSchema();
        b.setProperties(Map.of("ref", stringProp("a different description")));
        assertNotEquals(a, b);
    }

    @Test
    void propertyTypeDifferenceDetected() {
        InputSchema a = new InputSchema();
        a.setProperties(Map.of("ref", prop("string", "x")));
        InputSchema b = new InputSchema();
        b.setProperties(Map.of("ref", prop("integer", "x")));
        assertNotEquals(a, b);
    }

    @Test
    void propertyEnumOrderMatters() {
        // JSON Schema treats enum as an ordered list — same members in
        // different order are NOT equal (DR-structural-schema-equality).
        PropertySchema a = stringProp("dir");
        a.setEnumValues(List.of("up", "down"));
        PropertySchema b = stringProp("dir");
        b.setEnumValues(List.of("down", "up"));
        assertNotEquals(a, b);
    }

    @Test
    void propertyEnumSameOrderEqual() {
        PropertySchema a = stringProp("dir");
        a.setEnumValues(List.of("up", "down"));
        PropertySchema b = stringProp("dir");
        b.setEnumValues(List.of("up", "down"));
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void roundTripThroughGsonPreservesEquality() {
        // The schema produced by InputSchemaBuilder must compare equal to
        // the schema deserialized from its own JSON — this is the exact
        // shape MCPProxy's drift probe sees (one side from a static
        // descriptor, the other from upstream's listTools wire response).
        InputSchema original = new InputSchemaBuilder()
                .requiredInteger("ref", "the element ref")
                .optionalString("value", "optional value")
                .build();
        String json = MCPProtocol.toJson(original);
        InputSchema roundTripped = MCPProtocol.fromJson(json, InputSchema.class);
        assertEquals(original, roundTripped);
        assertEquals(original.hashCode(), roundTripped.hashCode());
    }

    @Test
    void inequalityWithNullAndOtherTypes() {
        InputSchema schema = new InputSchema();
        assertNotEquals(schema, null);
        assertNotEquals(schema, "not a schema");
    }

    private static PropertySchema stringProp(String description) {
        return prop("string", description);
    }

    private static PropertySchema prop(String type, String description) {
        PropertySchema p = new PropertySchema();
        p.setType(type);
        p.setDescription(description);
        return p;
    }
}
