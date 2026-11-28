# Remove legacy termux:application module

> Scope: complete removal of the Gradle module `:termux:application`; terminal UI/runtime ownership moves to `:core:ZeroStudio-Terminal`.
> Branch: `refactor/remove-termux-application`.

## Architecture decision

ZeroStudio's process Application is owned by `:core:app` and extends the shared IDE `BaseApplication`. It must not inherit `com.termux.app.TermuxApplication` or execute Termux application lifecycle initialization.

The terminal is a feature module, not an Application superclass. Its UI entry points, session service, settings, runtime bootstrap, resources and future implementation belong to `:core:ZeroStudio-Terminal`.

## Removed legacy module

The entire `termux/application/` directory has been removed from the repository and `:termux:application` has been removed from `settings.gradle.kts`.

Removed legacy application classes include:
- `TermuxApplication`
- legacy `TermuxActivity` and `TermuxFragment`
- legacy `TermuxService`
- legacy activity/fragment session clients and tab controllers
- legacy help/file receiver activities and event receiver
- legacy app Manifest, layouts, menus, drawable resources and tests
- the old module's native bootstrap JNI source (the required bootstrap installer JNI is now owned by the terminal module)

## New ownership and migrated entry points

| Concern | New owner |
|---|---|
| Process Application | `:core:app` → `IDEApplication : BaseApplication` |
| Terminal host Activity | `:core:ZeroStudio-Terminal` → `ui.activities.terminal.MainActivity` |
| IDE sidebar terminal | `TerminalSidebarAction` → `TerminalHostFragment` |
| Main screen terminal launch | Launches the terminal module's host Activity |
| Terminal sessions | `:core:ZeroStudio-Terminal` → `SessionService` / `TerminalSessionHolder` |
| Bootstrap package installer needed by SDK onboarding | `TerminalBootstrapInstaller` in `:core:ZeroStudio-Terminal` |
| Bootstrap native library | `:core:ZeroStudio-Terminal/src/main/cpp` |
| Bootstrap strings | Terminal module `values/strings.xml` and `values-zh/strings.xml` |

The bootstrap installer is a user-triggered setup operation. It is not initialized from `Application.onCreate()`.

## Cross-module cleanup

- `:core:app` no longer declares `projects.termux.application`.
- `:core:chatai:app` no longer declares `projects.termux.application`.
- `RikkaHubApp` extends Android `Application`, not `TermuxApplication`.
- `IDEApplication` extends `BaseApplication`; it still injects the application context into the terminal module's context bridge. This is an explicit feature integration and does not initialize the removed application module.
- The old terminal Activity was removed from the app manifest.
- The duplicate old/new sidebar actions were consolidated into one action backed by `TerminalHostFragment`.
- The old Termux preference extension and its settings entry were removed.
- Old `termux_app_strings.xml` files for every locale, old Termux Activity theme resources and old terminal fragment tab resources were removed from `:core:resources`.
- Legacy drawer attributes were removed. Terminal-view and extra-key attributes are retained where still required by `:termux:view` and the terminal emulator integration.

## Deliberately retained modules

This change removes only `:termux:application`, not every source artifact whose name contains Termux.

The following lower-level modules remain because the terminal runtime still consumes their APIs:
- `:termux:shared`
- `:termux:emulator`
- `:termux:view`

They are not Application/Activity/Fragment modules. Their eventual replacement or package/API migration is a separate terminal-runtime task. Do not delete them as part of removing the application module without replacing their consumers.

## Verification checklist

- [x] No `:termux:application` project include.
- [x] No app/chatai Gradle dependency on `:termux:application`.
- [x] Main `IDEApplication` no longer extends `TermuxApplication`.
- [x] ChatAI `RikkaHubApp` no longer extends `TermuxApplication`.
- [x] Legacy TerminalActivity/TermuxFragment sidebar path removed.
- [x] Legacy Termux preferences removed from IDE settings.
- [x] Bootstrap installer and its native JNI bridge moved to `:core:ZeroStudio-Terminal`.
- [x] Legacy Termux application strings and obsolete Activity theme resources removed.
- [ ] Run Gradle configuration and compile checks for `:core:app`, `:core:ZeroStudio-Terminal`, and dependent release variants.
- [ ] Inspect merged app Manifest to ensure only the new terminal Activity/SessionService/DocumentProvider are contributed.
- [ ] Runtime-test onboarding SDK bootstrap, opening the terminal from Main home, editor sidebar embedding, session create/close/reconnect, notifications and process recreation.

The source migration is committed on the feature branch. Build and runtime verification are intentionally not marked complete until CI/device evidence is available.
