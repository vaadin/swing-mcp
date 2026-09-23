# Lower the Java floor from 11 to 8?

Split out of the proxy removal on 2026-09-23, which kept the floor at 11. This idea asks
whether to go lower.

## Why it might matter

`D_java11_floor` rests on reach: the jar goes into Swing applications built years ago, whose JVM
is often not the owner's to choose, and a class-file version error is a total failure at startup.
Java 8 is where many of those applications actually stopped. The question is how much of the
market for a Swing→Vaadin migration still runs on 8. Nobody has measured that, and it decides
whether this is worth doing at all (`Q_market`).

## Measured cost, 2026-09-23

`javac --release 8` over every shipped main source set: tiny-mcp-server, swing-mcp-tool-defs
(since folded into swing-mcp), swing-mcp and swing-mcp-agent, about 13 k lines, with gson
2.13.2 and jspecify 1.0 on the classpath. Measured before the proxy removal, so the counts
include `MCPProxy` and `AutoRetryMCPClient`. It reports **69 errors**:

| Module | Errors |
|---|---|
| tiny-mcp-server | 48 |
| swing-mcp | 19 |
| swing-mcp-tool-defs | 1 |
| swing-mcp-agent | 1 |

By cause:

| Cause | Count | Fix |
|---|---|---|
| `String.isBlank` | 17 | `trim().isEmpty()` or a helper |
| `List.of` / `Set.of` / `Map.of` | 16 | `Collections.unmodifiable*(Arrays.asList(…))` or a helper |
| `java.net.http.*` (`HttpClient`, `HttpRequest`, `HttpResponse`, `BodyHandlers`, `BodyPublishers`) | ~18 | only `TinyMCPClient`; see `Q_client_home` |
| `String.repeat` / `strip` / `stripTrailing` | 6 | small helpers |
| `List.copyOf` | 3 | `Collections.unmodifiableList(new ArrayList<>(…))` |
| `var` | 2 | spell the type |
| `InputStream.readAllBytes` | 1 | a read loop |
| `new InputStreamReader(in, Charset)`-style overload in `StdioMCPServer` | 1 | the `String` charset name |
| diamond inference on an anonymous `LinkedHashMap` in `BoundedLRUMap` | 1 | explicit type arguments |

The source changes are mechanical, apart from the HTTP client.

Dependencies are not blockers. gson 2.13.2 and jspecify 1.0 are both class-file version 52
(Java 8), JUnit 5.14 runs on 8, and `com.sun.net.httpserver` has existed since Java 6.

## Open questions

- `Q_market` — Is Java 8 worth it? Who is actually stuck on 8 among migration candidates? Without
  a real case, the cost below buys reach nobody asked for.
- `Q_client_home` — `TinyMCPClient` is the one real blocker, and only tests use it: the swing-mcp
  headless and screen tests, `test-apps`, and tiny-mcp-server's own suite. There are two options:
  - Move it out of tiny-mcp-server's main source set into test fixtures (`java-test-fixtures`).
    Shipped code would then no longer need `HttpClient`, and the host JVM would stop carrying a
    client it never uses. Tests would stay on 11+.
  - Rewrite it on `HttpURLConnection`, which is what `D_java11_floor` calls "real work".

  Moving it looks cheaper, but it collides with the tests being held to the floor (next question).
- `Q_test_floor` — `D_java11_floor` holds the tests to the floor too, and `testJava11` re-runs them
  on a real 11 VM. That VM run is what catches a newer class pulled in by the shadow jar or an API
  reached reflectively. Going to 8 means a `testJava8` on a Temurin 8 JVM (`mise`). Either the
  tests also compile for 8, which the test-fixtures client blocks, or `--release 8` covers main
  only and a smaller set of tests runs on 8. Which one?
- `Q_runtime_behaviour` — Swing and `javax.accessibility` on 8 differ from 11, whereas the research
  entries in `design/research.md` were checked on 11+ and some are explicitly version-gated, like
  the `JSlider` actions entry. The snapshot's golden output may differ on an 8 VM. Only a
  `testSwing` run on 8 would tell.
- `Q_gradle` — Can the build still produce and test Java 8 artifacts on Gradle 8.14.3 with a JDK
  11–24 build JVM? `--release 8` is supported by those JDKs' `javac`; JDK 25's still accepts it
  with a deprecation warning.
