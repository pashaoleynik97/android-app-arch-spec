---
name: doctor
description: >
  Read-only status and conformance report for a project set up with android-app-arch-spec: installed components,
  deferred items, files modified since install, unresolved conflicts, skill lint results and Core-rule violations.
  Use when the user asks what is installed, whether the project still follows the architecture, or before an upgrade.
disable-model-invocation: true
---

# android-arch doctor

Follow `.arch-spec/spec/INSTALL.md` §8 (Doctor). Change nothing. Report findings with rule IDs and `file:line`.
If `.arch-spec/spec/` is missing, say so and stop: the project was not installed with this spec, or the spec copy
was removed.
