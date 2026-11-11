## What and why

<!-- One or two sentences: the user-visible change and the reason. Link the task / issue. -->

## How it was verified

<!-- Commands run, scenarios exercised, screenshots for UI changes. -->

## Checklist

- [ ] `./gradlew testDebugUnitTest` passes locally; new behaviour has unit tests (backtick-sentence names, hand-written fakes)
- [ ] `./gradlew spotlessCheck checkModuleGraph :app:lintDebug` passes (run `spotlessApply` first)
- [ ] `./gradlew assembleDebug` passes
- [ ] Skills updated in this PR if a convention, module, build rule or shared component changed (`.claude/skills/**`, `CLAUDE.md`)
- [ ] `.docs/` updated if user-visible behaviour, a data/session lifecycle or a QA matrix changed
- [ ] Server-response handling changed? A `.mitm_scripts/` scenario (`.py` + `.md`) covers the new branch (if mitm-testing is installed)
- [ ] `version.properties` is **not** edited by hand (CI owns it), unless bot mode is `none` and this PR is the version bump
- [ ] No secrets, keystores or `keystore.properties` committed
