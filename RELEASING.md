# Releasing

How a maintainer cuts a release. Only `swing-mcp-agent` is published, and only as a jar on the
[GitHub releases page](https://github.com/vaadin/swing-mcp/releases). `master` always carries
the next version with a `-SNAPSHOT` suffix; the steps use `1.0` → `1.1-SNAPSHOT`.

1. **Start clean:** on `master`, `git status` empty, pulled. `CHANGELOG.md` `[Unreleased]` says
   what the release brings, and no dependency in `gradle/libs.versions.toml` is a `-SNAPSHOT`.
2. **Set the release version:**
   - `build.gradle.kts`: `version = "1.0"`.
   - `CHANGELOG.md`: rename `## [Unreleased]` to `## [1.0] - YYYY-MM-DD` and open a fresh,
     empty `## [Unreleased]` above it.
   - `README.md`: the jar name in the `-javaagent` example, `swing-mcp-agent-1.0.jar`.
3. **Build everything:** `xvfb-run -a ./gradlew`, with a JDK 11 visible to Gradle
   (`export JDK11=…`) so `testJava11` runs too. It must be green; a red build stops the release.
4. **Commit and tag:** `git commit -am "Release 1.0"`, then `git tag 1.0`.
5. **Open the next version:** `build.gradle.kts`: `version = "1.1-SNAPSHOT"`, then
   `git commit -am "Prepare 1.1-SNAPSHOT"`.
6. **Push:** `git push origin master 1.0`.
7. **Publish** the jar built in step 3 — step 5 did not rebuild it — with the changelog
   section as the notes:
   ```bash
   awk '/^## \[1.0\]/{f=1;next} /^## \[/{f=0} f' CHANGELOG.md > /tmp/notes.md
   gh release create 1.0 swing-mcp-agent/build/libs/swing-mcp-agent-1.0.jar \
       --title 1.0 --notes-file /tmp/notes.md
   ```
