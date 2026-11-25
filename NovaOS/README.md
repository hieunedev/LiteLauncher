# NovaOS
**Adaptive Android Experience · Premium Dark Liquid Glass**

NovaOS is an Android launcher experience built as a modular application. It is not a replacement Android operating system and does not require root.

## Product principles
- Functional launcher first; visual polish must not hide missing behavior.
- Android platform security remains authoritative.
- Low-memory devices are a first-class target.
- Feature modules communicate through explicit contracts.
- No AI features, SDKs, APIs, assistant, or automation are in scope.

## Modules
1. Nova Lock Screen — companion lock-screen experience within Android's supported APIs; never bypasses system keyguard.
2. Nova Home — home screen, dock, folders, drag and drop.
3. Nova Control Center — supported shortcuts and settings panels; privileged toggles open system panels/settings where direct control is restricted.
4. Nova App Drawer — installed-app discovery, search and launch.
5. Nova Widgets — Android AppWidget integration.
6. Nova Studio — themes, layout and appearance preferences.
7. Nova Workspace — named home layouts / usage contexts.
8. Nova Settings — app configuration, permissions and diagnostics.
9. Plugin API — versioned extension contracts, introduced after core stability.

## Current status
Foundation branch: project charter, architecture, UX and engineering constraints, Android starter scaffold, and CI build workflow. This is not yet a release APK. Build verification is delegated to GitHub Actions.

## Build
Open the `NovaOS/` directory as an Android Gradle project in Android Studio, or run:
```bash
./gradlew :app:assembleDebug
```
