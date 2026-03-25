# UC-002: Skeletal implementation of a MCP server

This spec implements a basic skeletal implementation of a MCP server.
This is just to get the necessary infrastructure in place (implementation
and basic testing); the server returns no tools in the beginning,
nor allows any registration of additional tools.

Prerequisite: UC-001 implemented

**Status:** Implemented
**Date:** 2026-03-25

---

## Main Flow

Implement a TinyMCPServer according to the architecture.
The Java class accepts port and context path as constructor
args. Zero-arg constructor with default values is provided too.

The TinyMCPServer has initially two methods: `start()` starts
the http server, `stop()` stops it.

The server provides the following initialization information over MCP:

- Server name: `Swing MCP`
- Server version: `0.0.1`
- Instructions: omit.

---

## Acceptance Criteria

- [x] TinyMCPServer starts and listens on given port
- [x] TinyMCPServer responds correctly to the official MCP SDK client connecting to it
- [x] TinyMCPServer responds to MCP SDK query for tools, resources and prompts,
 and returns an empty list.

---

## Tests

> Write unit tests that verify the acceptance criteria above. See `architecture.md` § Testing for conventions.

- [x] Write TinyMCPServerTest, which uses the official MCP
SDK client to test that TinyMCPServer responds correctly to
client calls: the SDK client must connect and assert that there are
zero tools, prompts and resources, without throwing an exception.
