# ADR-0007 — Single light theme by default

Status: accepted · Area: DS-05, DS-21

## Context

Supporting dark mode and dynamic (wallpaper) color doubles every visual review and requires a complete second
palette from design. Many apps — dedicated devices, branded apps, early products — do not have one. A
half-supported dark theme (system dark mode flipping some colors) is worse than none.

## Decision

The default `THEME_MODE` is `light-only`: `<Prefix>Theme` has no `darkTheme` parameter, never reads the system
setting, uses no dynamic color, ships no `values-night/`, and the Activity forces dark system-bar glyphs. The
parameter `light-dark` is available when the design provides a dark palette; token names stay identical.

## Consequences

- Screens look identical on every device regardless of system settings.
- Switching later is additive: a second `<Prefix>Colors` instance and a mode switch in the theme — no call-site
  changes, because every call site already reads semantic tokens.
- Users who prefer dark mode do not get it until the design supports it.
