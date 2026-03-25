# Architecture

This server only uses Google GSON library for JSON
serialization and parsing. Do not generate
JSONs via String concatenation nor via the element API:
use a POJO classes mapping instead.

This server must not use `io.modelcontextprotocol.sdk` runtime
dependency since it brings the jackson library (a huge dependency)
and requires a servlet container to run (another huge dependency).
The server must also not use Quarkus MCP server (the Swing app
startup is set in stone and is not to be changed to Quarkus way)
nor Spring AI MCP (a huge dependency).

For testing purposes, we are leveraging the official
`modelcontextprotocol/java-sdk` Java SDK library which provides a MCP
client. We hope that the official MCP client will run in strict mode and
will throw an exception on any malformed MCP JSON message. Don't use the
`mcp-test` module - even though it looks like the perfect fit, it's the
`java-sdk` internal testing tool not meant to be used by other projects.
Alternatively we can use `LangChain4j`.

---

## 1. Technology Stack

- Google GSON
- For testing: the official Java MCP SDK
- Gradle (wrapper included)
- Java
- Testing: JUnit 5

---

## 2. Application Structure

```
com.example.specdriven/
  Application.java              — Spring Boot entry point
  [feature-package]/
    [FeatureView].java          — Vaadin @Route view
    [FeatureService].java       — Business logic (Spring @Service)
    [FeatureRepository].java    — Data access (Spring Data)
```

---

## 3. Testing

- **Browserless Tests**: Vaadin Browserless Testing (`SpringBrowserlessTest`)
  - Tests live in `src/test/java/`, mirroring the main package structure
  - Extend `SpringBrowserlessTest`, annotate with `@SpringBootTest`
  - Use `@WithMockUser(roles = "ADMIN")` for admin views
  - Use `@WithAnonymousUser` for access control tests
  - Use `navigate(ViewClass.class)` to render views
  - Use `$(ComponentClass.class)` to query components, `test(component)` to interact
- **React View Tests**: Vitest with React Testing Library
  - Tests live in `src/test/frontend/`, mirroring the view structure
  - Mock `@BrowserCallable` endpoint calls
  - Test component rendering, user interactions, and navigation
  - Run via `npx vitest run`
- **Service Tests**: JUnit tests for Spring `@Service` classes
  - Tests live in `src/test/java/`, same as browserless tests
  - Annotate with `@SpringBootTest`, autowire the service
  - Test business rules, validation, and data access
  - Endpoints (`@BrowserCallable`) typically delegate to services — test the service, not the endpoint
- **Test Coverage Requirements**:
  - React views: Vitest view tests
  - Vaadin Flow views: Browserless view tests
  - Services: JUnit service tests
- **Visual Verification**: Playwright MCP during development (not automated)

---

## 4. UIState Management

- **Signals** are the primary mechanism for managing UI state
- **Non-shared signals** for standard per-user UI state (e.g., form values, selection state, view-local data)
- **Shared signals** when state must be visible across multiple users/sessions (collaborative or real-time features) — requires **server push** to be enabled
- When using shared signals, enable push on the view/UI (e.g., `@Push` annotation)

---

## 5. Security & Admin

- **Spring Security** with `VaadinSecurityConfigurer`
- Public views: `@AnonymousAllowed` (React Hilla endpoints, public routes)
- Admin views: `@RolesAllowed("ADMIN")` (Vaadin Flow views)
- Login: Vaadin `LoginForm` at `/login`
