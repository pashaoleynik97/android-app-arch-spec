# Rename a module the installer created

Use when the developer renames a top-level module the spec rendered (`:x` → `:y` below). The
renderer must learn the new name. Otherwise later installs re-add `include(":x")`, `rerender` recreates
the old folder, gates call the old task, and upgrades compare the wrong files.

## Steps

1. **Rename in code.**
   - `git mv <old> <new>`.
   - Update `settings.gradle.kts` (`include(":<new>")`) and every `project(":<old>")` dependency.
   - Optional: rename the package segment, by moving the source directories and updating `package` and `import`
     lines. Type names may stay.
2. **Tell the renderer.** Run `python3 .arch-spec/spec/installer/render.py --target . relocate <old> <new>`. It
   re-keys the recorded checksums and the baseline, and records the relocation. From then on, `install`, marker
   inserts, `rerender`, `snapshot` and the gates shown by `status` all use the new name. Never edit
   `.arch-spec/state.json` by hand.
3. **Build:** `./gradlew assembleDebug testDebugUnitTest`.
4. **Audit the skills** ([audit](audit.md)). `check-refs` flags every stale path, task and module, including the
   component's own reference file and the `CLAUDE.md` module and component lines. Fix them through
   [hygiene](hygiene.md) (dead references).
5. **Log it** in `application-architecture/CHANGELOG.md`:
   `- <date> — rename: :<old> → :<new> (relocation recorded in .arch-spec/state.json)`.

No project rule is needed to "keep the new name": the recorded relocation is that rule, and the renderer enforces it.

## Checks before you finish

- `render.py --target . status` lists the relocation, and its `gates` show the new task names.
- `bash tools/skills/check-refs.sh` passes with no `check-refs: ignore` added for the old name.
- A second `render.py --target . install <any installed component>` adds no line with the old name.
