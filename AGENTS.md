# MightyMiner Porting Rules

These rules apply to every future session working on this project.

## Target and names

- Target Minecraft 26.2 on Fabric, using Java 25 and Mojang names.
- Never use Yarn names, SRG names, LWJGL 2, Forge events, or raw OpenGL.
- Use `reference/MAPPING_1.8.9_to_26.2.md` for name translations. Rows marked unverified (including ⚠️ entries and entries explicitly called unverified) must be checked against the real 26.2 sources, current Fabric documentation, or IDE autocomplete before use.
- Never guess an API name. If a name cannot be verified, say so and leave a `TODO(verify)` comment instead.

## Scope and translation

- Port one module at a time, in this order: `01` → `06` → `04` → `03` → `02` → `08` → `07` → `05`.
- Before porting a module, read its section in `reference/PORTING_NOTES.md` and every source file in that module's folder.
- `reference/MightyMiner_core_for_port/` and all reference material are read-only. Never edit them or copy 1.8.9 code into `src/` without translating it for the target APIs.
- Skyhanni_code/ is read-only reference. Use it to understand what data exists and how it is parsed. Do not copy its code; write our own implementation. Read its LICENSE file first and follow it.
- Keep pure algorithm logic in module `01`; replace only incompatible types such as `BlockPos` and `Vec3`, except for explicitly documented fixes or necessary compatibility changes.
- Do not reproduce bugs and quirks identified in `PORTING_NOTES.md`. For each module, report which applicable ones were fixed and which were deliberately kept.
- The failsafe package, audio, and the FML handshake mixin are intentionally excluded. Replace calls to failsafe classes with stubs, and stub `ApplyAbilityState`.

## Per-module completion

- After each module, run `./gradlew build` and fix compile errors before moving on.
- Update `PORT_STATUS.md` as work progresses. Record module completion, what compiles, what is stubbed, and what still needs in-game testing.
- Do not claim in-game behavior works unless it was actually run in-game. Use “compiles” or “untested” when that is all that has been verified.
