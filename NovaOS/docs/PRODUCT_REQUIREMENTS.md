# NovaOS — Product Requirements v1.0

## Vision
Deliver a coherent, customizable Android experience with a premium dark Liquid Glass visual language and practical everyday launcher functionality.

## Target
- Android phones; baseline minSdk 26 (Android 8.0) for initial engineering.
- Optimize first for entry-level hardware, including 4 GB RAM devices.
- Portrait-first phone layouts; responsive to tablets where practical.
- No root, no privileged permissions, no hidden API dependency.

## Personas and jobs
- Users who want a distinctive home and lock-screen companion experience.
- Users who need fast app discovery and lightweight shortcuts.
- Users who want to customize visual appearance without replacing Android security.

## Functional requirements
### Lock Screen
- Provide an optional companion lock-screen surface with clock, date, wallpaper and configurable widgets.
- Respect Android Keyguard and secure lock settings at all times.
- Never collect, intercept, store or simulate PIN/password/biometric credentials.
- Clearly disclose limitations: third-party apps cannot universally replace the system keyguard. A full-screen companion experience is opt-in and must not claim to be the secure system lock screen.
- AOD is out of initial scope; depends on OEM/system support.

### Home
- Settable as the user's default launcher.
- Render installed apps, launch activities, support a configurable dock and basic folders.
- Persist layout and restore safely after process death.
- Support long-press contextual actions and accessible touch targets.

### App Drawer
- Enumerate launchable apps using Android package visibility rules.
- Search by app label; sort alphabetically and optionally by recent usage only with user-granted Usage Access.

### Control Center
- Expose app-owned controls and shortcuts.
- For protected system settings, launch official Android settings panels instead of pretending to toggle restricted state.

### Widgets
- Integrate Android AppWidgetHost only after Home baseline is stable.
- Handle provider lifecycle and widget resize/configuration.

### Studio and Settings
- Persist theme, accent, wallpaper selection, grid and icon sizing.
- Provide reset-to-default and permission explanations.

### Workspace
- Support multiple saved launcher layouts in a later phase; no background profile switching without explicit user action.

### Plugins
- Versioned, permission-scoped extension API.
- No arbitrary code loading in v1.0; plugin installation and trust model require separate security review.

## Non-functional requirements
- Cold start target < 2.5 s on reference low-end device (measurement required).
- Avoid unnecessary background services and continuous polling.
- No mandatory network access.
- Handle process recreation and configuration changes.
- TalkBack labels and scalable text for core navigation.
- No crashes during app enumeration, missing icon fallback, or denied permissions.

## Out of scope
- Replacing Android OS, system keyguard, status bar or privileged control APIs.
- Root-only features.
- AI features of any kind.
- Cloud account or telemetry requirement.
- Always-on background monitoring.

## Release gates
- Build succeeds on CI.
- Install/launch verified on Android emulator and at least one physical device.
- Launcher role works and can be revoked through Android settings.
- Keyguard is never bypassed.
- Core navigation, app launch, persistence and accessibility smoke tests pass.
