---
name: install
description: >
  Installs (or adds components to, completes deferred items of, or upgrades) the android-app-arch-spec architecture
  in the current Android project by following the spec's INSTALL.md. Run only when the user explicitly asks to
  install, add a component, complete deferred items or upgrade.
disable-model-invocation: true
argument-hint: "[fresh | add <component> | complete <deferred-item> | upgrade | feature <name>]"
---

# android-arch install

1. Make sure the spec is available at `.arch-spec/spec/` in the project root. If it isn't, copy this plugin's root
   directory (`${CLAUDE_PLUGIN_ROOT}`) there. The renderer and payload must live inside the project so later
   sessions, CI and other agents can use them.
2. Read `.arch-spec/spec/INSTALL.md` completely and follow it. The arguments (`$ARGUMENTS`) select the mode
   (INSTALL.md §2). With no arguments, detect the mode.
3. INSTALL.md's golden rules override anything else here: ask, never guess; every question may be skipped; gates
   before commits; nothing external.
