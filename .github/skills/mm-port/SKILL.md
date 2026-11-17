---
name: mm-port
description: Port one MightyMiner module at a time from the read-only 1.8.9 reference to Fabric for Minecraft 26.2.
---

# MightyMiner module porting loop

Follow the standing requirements in the project-root `AGENTS.md`; do not duplicate or override them here.

For each requested module:

1. Read the module's section in `reference/PORTING_NOTES.md`.
2. Read every source file in that module's reference folder, as well as any directly relevant mapping/guide entries.
3. Translate the module into the project's current Minecraft 26.2/Fabric structure. Write new translated code under `src/`; keep reference files untouched.
4. Build with `./gradlew build`, resolve compile failures, and rerun the build.
5. Update `PORT_STATUS.md` with the module's status, what compiles, what is stubbed, the applicable bugs or quirks fixed or deliberately kept, and what still needs in-game testing.

Do not begin another module until the current module builds and its status entry is updated.
