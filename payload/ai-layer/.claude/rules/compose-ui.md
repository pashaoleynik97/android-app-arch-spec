---
paths:
  - "**/src/main/**/ui/**/*.kt"
---

# Compose UI code

Owner: the **compose-ui** skill (tokens, kit, host chrome, screen layout) and, for ViewModels and navigation,
the presentation reference of **application-architecture**. The rules most often broken here:

- No hardcoded `Color(0x…)`, `.sp` or magic `.dp`: every value comes from the theme accessor
  (colors / typography / dimens); a missing value becomes a token first (DS-06).
- Use the kit component for its role (button, button row, text field, banner, sheet, dialog) before writing
  ad-hoc Compose (DS-13).
- User-visible text comes from string resources; ViewModels never resolve strings (DS-20, UI-06).
- A screen is a stateful `XScreen` plus a private stateless body that never receives the ViewModel (UI-11).
- Host chrome (status bar, banners, menu) is declared through the RESUMED-gated `Set…` effects only; screens
  never host their own banner host (UI-24, UI-26).
- Navigation lives only in the feature's FeatureImpl; screens get lambdas, ViewModels get `uiCallbacks`
  (UI-08, UI-18).
- Previews wrap the stateless body in the theme and use the shared reference-device preview annotation (DS-11).
- A new token or kit component updates the compose-ui skill in the same change (DS-26).
