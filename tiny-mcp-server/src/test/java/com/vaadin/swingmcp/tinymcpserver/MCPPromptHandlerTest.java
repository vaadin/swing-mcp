package com.vaadin.swingmcp.tinymcpserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MCPPromptHandler} — registration validation and
 * direct handler dispatch (without going through HTTP).
 */
class MCPPromptHandlerTest {

    // ===== Registration validation =====

    private static MCPProtocol.InputSchema emptySchema() {
        return new InputSchemaBuilder().build();
    }

    private static TinyMCPServer.PromptFunction constPrompt() {
        return args -> {
            MCPProtocol.GetPromptResult r = new MCPProtocol.GetPromptResult();
            r.setMessages(Collections.emptyList());
            return r;
        };
    }

    @Test
    void addPromptRejectsNullName() {
        MCPPromptHandler handler = new MCPPromptHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt(null, "desc", emptySchema(), constPrompt()));
    }

    @Test
    void addPromptRejectsBlankName() {
        MCPPromptHandler handler = new MCPPromptHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt("  ", "desc", emptySchema(), constPrompt()));
    }

    @Test
    void addPromptRejectsInvalidName() {
        MCPPromptHandler handler = new MCPPromptHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt("1prompt", "desc", emptySchema(), constPrompt()));
        assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt("my-prompt", "desc", emptySchema(), constPrompt()));
        assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt("my prompt", "desc", emptySchema(), constPrompt()));
    }

    @Test
    void addPromptRejectsNullDescription() {
        MCPPromptHandler handler = new MCPPromptHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt("my_prompt", null, emptySchema(), constPrompt()));
    }

    @Test
    void addPromptRejectsBlankDescription() {
        MCPPromptHandler handler = new MCPPromptHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt("my_prompt", "  ", emptySchema(), constPrompt()));
    }

    @Test
    void addPromptRejectsNullSchema() {
        MCPPromptHandler handler = new MCPPromptHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt("my_prompt", "desc", null, constPrompt()));
    }

    @Test
    void addPromptRejectsNullFunction() {
        MCPPromptHandler handler = new MCPPromptHandler();
        assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt("my_prompt", "desc", emptySchema(), null));
    }

    @Test
    void addPromptRejectsNonStringProperty() {
        MCPPromptHandler handler = new MCPPromptHandler();
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredInteger("count", "how many")
                .build();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                handler.addPrompt("my_prompt", "desc", schema, constPrompt()));
        assertTrue(ex.getMessage().contains("count"), ex.getMessage());
        assertTrue(ex.getMessage().contains("string"), ex.getMessage());
    }

    @Test
    void addPromptDuplicateNameThrows() {
        MCPPromptHandler handler = new MCPPromptHandler();
        handler.addPrompt("greet", "desc", emptySchema(), constPrompt());
        assertThrows(IllegalStateException.class, () ->
                handler.addPrompt("greet", "other desc", emptySchema(), constPrompt()));
    }

    // ===== handlePromptsList =====

    private static JsonObject dispatch(java.util.function.Consumer<JsonRpcExchange> action) {
        FakeHttpExchange exchange = new FakeHttpExchange(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"x\"}");
        JsonRpcExchange rpc = new JsonRpcExchange(exchange);
        rpc.parsePost();
        action.accept(rpc);
        return MCPProtocol.fromJson(exchange.getResponseBodyString(), JsonObject.class);
    }

    @Test
    void handlePromptsListEmpty() {
        MCPPromptHandler handler = new MCPPromptHandler();
        JsonObject body = dispatch(handler::handlePromptsList);
        assertEquals(0, body.getAsJsonObject("result").getAsJsonArray("prompts").size());
    }

    @Test
    void handlePromptsListReturnsRegisteredDescriptors() {
        MCPPromptHandler handler = new MCPPromptHandler();
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredString("name", "Who to greet")
                .optionalString("greeting", "How to greet")
                .build();
        handler.addPrompt("greet", "Greet someone", schema, constPrompt());

        JsonObject body = dispatch(handler::handlePromptsList);
        JsonArray prompts = body.getAsJsonObject("result").getAsJsonArray("prompts");
        assertEquals(1, prompts.size());

        JsonObject prompt = prompts.get(0).getAsJsonObject();
        assertEquals("greet", prompt.get("name").getAsString());
        assertEquals("Greet someone", prompt.get("description").getAsString());

        JsonArray args = prompt.getAsJsonArray("arguments");
        assertEquals(2, args.size());
        JsonObject a0 = args.get(0).getAsJsonObject();
        assertEquals("name", a0.get("name").getAsString());
        assertEquals("Who to greet", a0.get("description").getAsString());
        assertTrue(a0.get("required").getAsBoolean());
        JsonObject a1 = args.get(1).getAsJsonObject();
        assertEquals("greeting", a1.get("name").getAsString());
        assertFalse(a1.get("required").getAsBoolean());
    }

    @Test
    void handlePromptsListPreservesRegistrationOrder() {
        MCPPromptHandler handler = new MCPPromptHandler();
        handler.addPrompt("zulu", "Z", emptySchema(), constPrompt());
        handler.addPrompt("alpha", "A", emptySchema(), constPrompt());
        handler.addPrompt("mike", "M", emptySchema(), constPrompt());

        JsonArray prompts = dispatch(handler::handlePromptsList)
                .getAsJsonObject("result").getAsJsonArray("prompts");
        assertEquals("zulu", prompts.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("alpha", prompts.get(1).getAsJsonObject().get("name").getAsString());
        assertEquals("mike", prompts.get(2).getAsJsonObject().get("name").getAsString());
    }

    // ===== handlePromptsGet =====

    private static MCPProtocol.JsonRpcRequest buildGetRequest(String promptName, Map<String, String> args) {
        MCPProtocol.GetPromptParams params = new MCPProtocol.GetPromptParams();
        params.setName(promptName);
        params.setArguments(args);
        MCPProtocol.JsonRpcRequest req = new MCPProtocol.JsonRpcRequest();
        req.setMethod("prompts/get");
        req.setId(1);
        req.setParams(MCPProtocol.gson().toJsonTree(params));
        return req;
    }

    private static JsonObject doGet(MCPPromptHandler handler, MCPProtocol.JsonRpcRequest req) {
        FakeHttpExchange exchange = new FakeHttpExchange(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"prompts/get\"}");
        JsonRpcExchange rpc = new JsonRpcExchange(exchange);
        rpc.parsePost();
        handler.handlePromptsGet(rpc, req);
        return MCPProtocol.fromJson(exchange.getResponseBodyString(), JsonObject.class);
    }

    @Test
    void handlePromptsGetReturnsFunctionResult() {
        MCPPromptHandler handler = new MCPPromptHandler();
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredString("name", "Who")
                .build();
        handler.addPrompt("greet", "Greet", schema, args -> {
            MCPProtocol.GetPromptResult r = new MCPProtocol.GetPromptResult();
            r.setDescription("Hi " + args.get("name"));
            MCPProtocol.PromptMessage msg = new MCPProtocol.PromptMessage();
            msg.setRole("user");
            msg.setContent(MCPProtocol.Content.text("Hello, " + args.get("name") + "!"));
            r.setMessages(List.of(msg));
            return r;
        });

        JsonObject body = doGet(handler, buildGetRequest("greet", Map.of("name", "Alice")));
        JsonObject result = body.getAsJsonObject("result");
        assertEquals("Hi Alice", result.get("description").getAsString());
        JsonArray messages = result.getAsJsonArray("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("Hello, Alice!",
                messages.get(0).getAsJsonObject().getAsJsonObject("content").get("text").getAsString());
    }

    @Test
    void handlePromptsGetPassesArgsToFunction() {
        MCPPromptHandler handler = new MCPPromptHandler();
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredString("a", "A")
                .optionalString("b", "B")
                .build();
        java.util.concurrent.atomic.AtomicReference<Map<String, String>> seen = new java.util.concurrent.atomic.AtomicReference<>();
        handler.addPrompt("cap", "capture", schema, args -> {
            seen.set(args);
            MCPProtocol.GetPromptResult r = new MCPProtocol.GetPromptResult();
            r.setMessages(Collections.emptyList());
            return r;
        });

        doGet(handler, buildGetRequest("cap", Map.of("a", "A-val", "b", "B-val")));
        assertEquals(Map.of("a", "A-val", "b", "B-val"), seen.get());
    }

    @Test
    void handlePromptsGetOmitsOptionalArgs() {
        MCPPromptHandler handler = new MCPPromptHandler();
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredString("a", "A")
                .optionalString("b", "B")
                .build();
        java.util.concurrent.atomic.AtomicReference<Map<String, String>> seen = new java.util.concurrent.atomic.AtomicReference<>();
        handler.addPrompt("cap", "capture", schema, args -> {
            seen.set(args);
            MCPProtocol.GetPromptResult r = new MCPProtocol.GetPromptResult();
            r.setMessages(Collections.emptyList());
            return r;
        });

        doGet(handler, buildGetRequest("cap", Map.of("a", "only-required")));
        assertEquals(Map.of("a", "only-required"), seen.get());
    }

    @Test
    void handlePromptsGetUnknownPromptThrows() {
        MCPPromptHandler handler = new MCPPromptHandler();
        MCPServerException ex = assertThrows(MCPServerException.class, () ->
                doGet(handler, buildGetRequest("nope", Map.of())));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("nope"), ex.getMessage());
    }

    @Test
    void handlePromptsGetMissingRequiredArgThrows() {
        MCPPromptHandler handler = new MCPPromptHandler();
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredString("name", "Who")
                .build();
        handler.addPrompt("greet", "Greet", schema, constPrompt());
        MCPServerException ex = assertThrows(MCPServerException.class, () ->
                doGet(handler, buildGetRequest("greet", Map.of())));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
    }

    @Test
    void handlePromptsGetUnknownArgBecomesInvalidParams() {
        MCPPromptHandler handler = new MCPPromptHandler();
        MCPProtocol.InputSchema schema = new InputSchemaBuilder()
                .requiredString("name", "Who")
                .build();
        handler.addPrompt("greet", "Greet", schema, constPrompt());
        MCPServerException ex = assertThrows(MCPServerException.class, () ->
                doGet(handler, buildGetRequest("greet", Map.of("name", "X", "stray", "Y"))));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertTrue(ex.getMessage().contains("stray"), ex.getMessage());
    }

    @Test
    void handlePromptsGetMissingNameParamThrows() {
        MCPPromptHandler handler = new MCPPromptHandler();
        MCPProtocol.JsonRpcRequest req = new MCPProtocol.JsonRpcRequest();
        req.setMethod("prompts/get");
        req.setId(1);
        // params without name
        MCPProtocol.GetPromptParams params = new MCPProtocol.GetPromptParams();
        req.setParams(MCPProtocol.gson().toJsonTree(params));

        MCPServerException ex = assertThrows(MCPServerException.class, () ->
                doGet(handler, req));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
    }

    @Test
    void handlePromptsGetNullResultThrowsInternalError() {
        MCPPromptHandler handler = new MCPPromptHandler();
        handler.addPrompt("nully", "null returner", emptySchema(), args -> null);
        MCPServerException ex = assertThrows(MCPServerException.class, () ->
                doGet(handler, buildGetRequest("nully", Map.of())));
        assertEquals(MCPServerException.INTERNAL_ERROR, ex.getCode());
    }

    @Test
    void handlePromptsGetFunctionExceptionBecomesInternalError() {
        MCPPromptHandler handler = new MCPPromptHandler();
        handler.addPrompt("boom", "throws", emptySchema(), args -> {
            throw new RuntimeException("kaboom");
        });
        MCPServerException ex = assertThrows(MCPServerException.class, () ->
                doGet(handler, buildGetRequest("boom", Map.of())));
        assertEquals(MCPServerException.INTERNAL_ERROR, ex.getCode());
        assertTrue(ex.getMessage().contains("boom"), ex.getMessage());
    }

    @Test
    void handlePromptsGetPropagatesMCPServerException() {
        MCPPromptHandler handler = new MCPPromptHandler();
        handler.addPrompt("reject", "rejects", emptySchema(), args -> {
            throw new MCPServerException(MCPServerException.INVALID_PARAMS, "no");
        });
        MCPServerException ex = assertThrows(MCPServerException.class, () ->
                doGet(handler, buildGetRequest("reject", Map.of())));
        assertEquals(MCPServerException.INVALID_PARAMS, ex.getCode());
        assertEquals("no", ex.getMessage());
    }
}
