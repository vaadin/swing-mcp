package com.vaadin.swingmcp.tinymcpserver;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PromptArgumentsBuilderTest {

    @Test
    void emptyBuilderProducesEmptyList() {
        assertTrue(new PromptArgumentsBuilder().build().isEmpty());
    }

    @Test
    void requiredAndOptionalBuild() {
        List<MCPProtocol.PromptArgument> args = new PromptArgumentsBuilder()
                .required("name", "Who to greet")
                .optional("style", "Greeting style")
                .build();
        assertEquals(2, args.size());

        MCPProtocol.PromptArgument first = args.get(0);
        assertEquals("name", first.getName());
        assertEquals("Who to greet", first.getDescription());
        assertTrue(first.getRequired());

        MCPProtocol.PromptArgument second = args.get(1);
        assertEquals("style", second.getName());
        assertEquals("Greeting style", second.getDescription());
        assertFalse(second.getRequired());
    }

    @Test
    void buildPreservesInsertionOrder() {
        List<MCPProtocol.PromptArgument> args = new PromptArgumentsBuilder()
                .optional("z", "Z")
                .required("a", "A")
                .optional("m", "M")
                .build();
        assertEquals(List.of("z", "a", "m"),
                args.stream().map(MCPProtocol.PromptArgument::getName)
                        .collect(java.util.stream.Collectors.toList()));
    }

    @Test
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class, () ->
                new PromptArgumentsBuilder().required(null, "desc"));
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () ->
                new PromptArgumentsBuilder().optional("  ", "desc"));
    }

    @Test
    void rejectsInvalidName() {
        assertThrows(IllegalArgumentException.class, () ->
                new PromptArgumentsBuilder().required("1abc", "desc"));
        assertThrows(IllegalArgumentException.class, () ->
                new PromptArgumentsBuilder().required("a-b", "desc"));
        assertThrows(IllegalArgumentException.class, () ->
                new PromptArgumentsBuilder().required("a b", "desc"));
    }

    @Test
    void rejectsNullDescription() {
        assertThrows(IllegalArgumentException.class, () ->
                new PromptArgumentsBuilder().required("name", null));
    }

    @Test
    void rejectsBlankDescription() {
        assertThrows(IllegalArgumentException.class, () ->
                new PromptArgumentsBuilder().optional("name", "  "));
    }

    @Test
    void rejectsDuplicateName() {
        PromptArgumentsBuilder b = new PromptArgumentsBuilder().required("x", "first");
        assertThrows(IllegalStateException.class, () -> b.optional("x", "second"));
    }

    @Test
    void buildReturnsIndependentCopies() {
        PromptArgumentsBuilder b = new PromptArgumentsBuilder().required("x", "X");
        List<MCPProtocol.PromptArgument> a = b.build();
        List<MCPProtocol.PromptArgument> c = b.build();
        assertNotSame(a, c);
        assertEquals(1, a.size());
        assertEquals(1, c.size());
    }
}
