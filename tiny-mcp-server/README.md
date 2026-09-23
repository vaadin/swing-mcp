# tiny-mcp-server

A minimal Model Context Protocol server in pure Java, built to be embedded in someone else's
application. HTTP and stdio transports, tools, resources and prompts, plus the `MCPProxy`
forwarding machinery — over the JDK's `HttpServer` and GSON, with no framework underneath.
It implements the protocol and knows nothing about what the tools do.

## Why it exists

An MCP server that runs inside a host application shares that application's classloader, so
every runtime dependency it carries is one the host might already have at a different version.
The official Java SDK brings Project Reactor and two Jackson lineages; this one brings GSON.
See `design/decisions.md`, `D_no_framework_deps`.

## Using it

Register tools on an `MCPHandler`, wrap it in a transport, run the transport.

```java
MCPHandler handler = new MCPHandler()
        .setAcceptNewSession(existing ->
                new SessionDecision.AcceptAndEvict(existing, "Superseded by a new client"));
handler.addTool("greet", "Greet someone by name",
        new InputSchemaBuilder().requiredString("name", "who to greet").build(),
        req -> MCPProtocol.Content.text("Hello, " + req.arguments().getString("name")));

// in-process, inside a host application:
new HttpMCPServer(18088, "/mcp", handler).start();

// or as a standalone process an MCP client spawns:
new StdioMCPServer(handler).runStdio(System.in, System.out);
```

To put a stdio face on an MCP server that is already running over HTTP somewhere else,
`MCPProxy.newHandler(tools, upstreamUri, messages)` returns a handler that forwards every
`tools/call` upstream and answers `tools/list` from the manifest you supply.

## What it does not do

No SSE streams, no server-to-client push, no authentication, no resumability. The HTTP
transport binds to `127.0.0.1` and nothing else.

## Building

`./gradlew :tiny-mcp-server:test` from the repository root.

## Its place in this repository

This is a separate product that happens to live here: it is host-agnostic, nothing in it
names Swing, and it is expected to move to a repository of its own with its own release
cadence. See `AGENTS.md` beside this file for the rules that keep it that way.

## License

Copyright 2000-2026 Vaadin Ltd.

Licensed under the [Apache License, Version 2.0](LICENSE). Every source file
carries the corresponding header; contributions are accepted under the same
license.
