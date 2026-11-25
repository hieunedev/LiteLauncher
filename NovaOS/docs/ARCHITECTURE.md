# NovaOS — Architecture Specification v1.0

## Architecture style
Single Android application with feature-oriented modules. Start with a small app module and extract Gradle modules only when boundaries are stable. Prefer Android Views for the initial low-overhead shell; avoid adding Compose or large UI libraries until profiling justifies them.

## Layers
- **app**: application composition, launcher entry point, navigation.
- **core/design**: colors, typography, dimensions, surfaces and reusable view components.
- **core/data**: DataStore preferences and Room only when relational data is needed.
- **core/platform**: package manager, wallpaper, widget host, system settings intents.
- **feature/home**: launcher grid, dock, folders and drag/drop.
- **feature/drawer**: app discovery and search.
- **feature/lockscreen**: opt-in companion activity; Android Keyguard remains authoritative.
- **feature/control**: app-owned shortcuts and safe system-panel links.
- **feature/studio**: appearance settings.
- **feature/workspace**: saved layout profiles.
- **feature/plugins**: versioned contracts, disabled until security review.

## Dependency direction
Features may depend on core contracts. Core must not depend on features. Features must not directly reach into another feature's implementation. App composition wires implementations.

## Data model (initial)
- LauncherPreference(key, value, updatedAt)
- HomeItem(id, packageName, activityName, cellX, cellY, spanX, spanY, itemType, folderId)
- Folder(id, title, sortOrder)
- Workspace(id, name, isActive, createdAt)
- ThemeConfig(themeMode, accentColor, blurEnabled, iconScale, gridColumns)

Use DataStore for scalar preferences. Use Room when layout persistence requires relational queries and migrations.

## Platform boundaries
- Query launcher activities using PackageManager and current package visibility rules.
- Request only permissions needed by a user-visible feature.
- Do not use AccessibilityService to simulate privileged system controls.
- Do not request notification access unless a separately specified feature genuinely requires it.
- For protected toggles, route to official Settings panels.
- Keyguard security is owned by Android/OEM.

## Performance
- No polling loops.
- Load app labels/icons off the main thread; cache with bounded memory.
- Use RecyclerView/ListAdapter for long app lists.
- Avoid large bitmap allocations; decode wallpaper previews to view size.
- Keep animations short, optional and hardware-accelerated where supported.
- Provide a reduced-effects mode.

## Testing strategy
- Unit tests for preference mapping, layout validation and workspace selection.
- Instrumentation tests for launcher entry, app list, navigation and persistence.
- Manual matrix: Android 8–10, 11–13, 14+; low-memory physical device.
- Profile startup, frame time, memory and battery before release.
