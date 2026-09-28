# Contributing

Thank you for taking your time to contribute! Here are some basic rules for a good PR:

1. Please draft a proposal first, as one file in `design/ideas/`; the ones already there show the shape. You can use Claude to help create the draft.
2. The proposal must be grilled by Claude: see the grill-me skill.
3. Cross-cutting decisions are recorded in `design/decisions.md`, and what you found out about Swing or `javax.accessibility` along the way in `design/research.md`.
4. New files must follow the project structure and must be placed sensibly; `AGENTS.md` has the module map and the conventions.
5. A user-visible change adds a line under `[Unreleased]` in `CHANGELOG.md`.
6. `xvfb-run -a ./gradlew` (or plain `./gradlew` with a display) must be green.
