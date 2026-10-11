# Lacuna — progress log

Format per milestone: what was built, deviations from LACUNA_SPEC.md, known limitations, gate status.

## Build environment note (applies to every milestone below)

This code was written in a sandbox whose network policy **denies `maven.neoforged.net` and every Mojang host**
(`piston-meta`/`piston-data.mojang.com`, `libraries.minecraft.net`, `resources.download.minecraft.net`) and
throttles `repo.maven.apache.org`. ModDevGradle therefore cannot download NeoForge or Minecraft, so the
gates that need Minecraft (**1 build, 2 runData, 5 GameTests, 6 smoke**) could **not** be executed here.
APIs were checked against the NeoForge 1.21.1 sources (github.com/neoforged/NeoForge, branch `1.21.1`,
including its patches and tests) and vanilla 1.21.1 data from misode/mcmeta, not against a compiled jar.

Gates that **were** run for every milestone:
- gate 3 `python3 tools/validate_assets.py`
- gate 4 for `com.lacuna.mod.logic` (pure Java): compiled standalone with `javac` and run with the JUnit 5
  console launcher (the Gradle `test` task needs the NeoForge toolchain to configure).
- determinism of `tools/gen_resources.py`, `tools/gen_textures.py`, `tools/gen_structures.py` (run twice, no diff).

**First thing to do on a machine with normal internet:** `./gradlew build`, fix any compile errors, then run
`./gradlew runGameTestServer` and `bash tools/smoke.sh`.

## Fallbacks and known limitations (global list)

- Java datagen is replaced by deterministic Python generators (`tools/gen_resources.py`), because they can run
  and be validated without Minecraft. `runData` is still configured; it has no providers and is a no-op.
- Client entry point uses `@Mod(value = "lacuna", dist = Dist.CLIENT)` (equivalent of a client-only
  `@EventBusSubscriber`): listeners are registered explicitly in its constructor.

## M0 — project, tooling, gates, config, SafeRun, commands, creative tab, texture pipeline

Built:
- ModDevGradle project (Gradle wrapper 9.2.1, MDG 2.0.148, NeoForge 21.1.259 = newest in the official 1.21.1
  MDK at setup time), run configs `client`, `server`, `gameTestServer`, `data`; JUnit 5 for pure logic.
- `neoforge.mods.toml` template: depends only on `neoforge` and `minecraft [1.21.1]`, `side = "BOTH"`.
- `LacunaConfig` (COMMON, every feature has `enabled` + numbers, safe getters that fall back to defaults) and
  `Balance` (every fixed number, incl. registration-time stats).
- `SafeRun` (catches Throwable except VirtualMachineError, logs once with stack trace, runs a restore callback,
  disables the feature for the session; reset on ServerStoppedEvent).
- `/lacuna` command tree (permission 2) with `selftest` (coordinate round trips); other subcommands are
  placeholders until their milestone.
- `CoordMath` (+ unit tests, incl. negatives and round-trip error ≤ 11).
- Creative tab "Lacuna".
- Tools: `gen_textures.py` (+ `texlib.py`), `gen_resources.py` (+ `content/`, vanilla blockstate/model
  templates in `tools/templates/`), `gen_structures.py` (nbtlib, deterministic gzip), `validate_assets.py`
  (with a vanilla 1.21.1 id index in `tools/vanilla_1.21.1.json`), `smoke.sh` (+ `SmokeRunner`, activated only
  by `-Dlacuna.smoke=phase1|phase2`), `log_allowlist.txt`.

Deviations: smoke test drives the server through `SmokeRunner` reading `run/lacuna_smoke_<phase>.txt` instead
of piping stdin (Gradle run tasks do not forward stdin reliably).

Gates: 3 ✔, 4 (logic) ✔ 8/8, 1/2/5/6 not runnable here (see note).
