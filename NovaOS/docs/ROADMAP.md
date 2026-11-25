# NovaOS — Delivery Roadmap

## Phase 0 — Foundation
- [x] Product scope and non-goals
- [x] Module boundaries and platform constraints
- [x] Liquid Glass design tokens
- [x] Android starter scaffold
- [ ] CI build verified
- [ ] Device baseline and profiling

## Phase 1 — App shell
- Launcher intent and default-home onboarding
- Theme token implementation
- Permission education and diagnostics
- Navigation and settings persistence

## Phase 2 — Home and drawer
- Package discovery and app launch
- RecyclerView app drawer and search
- Home grid, dock, folders
- Layout persistence and recovery

## Phase 3 — Lock companion
- Optional companion surface and customization
- Keyguard integration checks on supported Android versions
- Explicit security boundary and OEM compatibility tests

## Phase 4 — Control center and widgets
- Safe quick actions / system settings panels
- AppWidgetHost lifecycle and resize
- Battery and memory profiling

## Phase 5 — Studio and workspaces
- Theme editor, wallpaper and layout controls
- Named layout profiles and import/export

## Phase 6 — Plugin foundation
- API versioning and capability manifest
- Threat model and permission review
- No arbitrary code execution in initial release

## Phase 7 — Release hardening
- Accessibility, localization, crash and regression tests
- Performance budgets
- Release signing instructions and reproducible CI artifact

Each phase has an acceptance checklist and must build before the next phase is considered complete.
