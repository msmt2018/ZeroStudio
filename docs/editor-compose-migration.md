# Editor Compose framework

The live entry point is `BaseEditorActivity.ComposeScreen()` → `EditorActivityContent` → `EditorWorkspaceScreen`. `EditorLayoutScreens.kt` now contains only shared search/progress content; its unused alternate editor shell has been removed.

## UI ownership

| Area | Implementation |
| --- | --- |
| Toolbar and action menus | Material 3 TopAppBar, IconButton, Compose menu dialog; existing ActionsRegistry/MenuProvider dispatch |
| Document tabs | ScrollableTabRow, stable identities, close requests through existing save/confirmation logic |
| Sidebar | ModalNavigationDrawer with a full-width ModalDrawerSheet and NavigationRail |
| IDE drawer | ModalBottomSheet, scrollable page tabs, partial/full expansion, output share/clear |
| Search, initialization, project close, unsaved files and installer UI | Compose dialogs |
| Symbol input | Module-owned SymbolInputPanel, HorizontalPager, LazyVerticalGrid, preference updates and existing Sora actions |
| Memory chart and onboarding | Compose Canvas and Compose onboarding targets |

Sora editor buffers remain native engines. Their views stay attached while files are open so background autosave and preference subscriptions keep working. They are hidden when inactive, and released through the existing close path. Terminal internals remain unchanged.

Existing Git/file-tree, debugger, output and other feature Fragments remain supported contributions. AndroidX `AndroidFragment` owns their creation, saved state, removal and lifecycle. The independent symbol manager keeps its existing management UI and resources. Specialized dialogs owned by retained feature Fragments are outside the editor-shell replacement.

## Register pages

Contributions belong to an Activity, not a singleton. Register them after `super.onCreate` each time the editor Activity is created. IDs must be nonblank and unique within their registry.

```kotlin
registerSidebarPage(EditorPage.ScreenPage("plugin.files", "Files") { FilesScreen() })
registerIdePage(EditorPage.ScreenPage("plugin.output", "Output") { OutputScreen() })
registerIdePage(EditorPage.FragmentPage("plugin.fragment", "Tools", ToolsFragment::class.java, arguments))

content.sidebar.select("plugin.files")
content.bottomSheet.pages.select("plugin.output")
content.bottomSheet.visible = true
unregisterIdePage("plugin.output")
```

For document tabs use `openComposeTab(id, title) { Screen() }` or `fragmentTabManager.openTab(FragmentTabEntry(...), filePath, args)`. File tabs continue to resolve file identity separately from their tab position. Fragment constructors use the host FragmentFactory; existing registered class factories are delegated through it before restoration. Factories for a class should be consistent across registrations. Compose lambdas cannot be serialized; the contribution owner must reopen its screens after Activity recreation.

Compose page state is retained with SaveableStateHolder during selection changes and released on unregistration. Closing the IDE sheet preserves its page holder. The selected built-in sidebar/drawer IDs and drawer visibility are saved in the Activity bundle.

Log collection belongs to the editor lifecycle, not to the currently visible Fragment. Build output, application logs, IDE logs, diagnostics and search adapters are replayed when a page mounts. Application/IDE log buffers are bounded; file sharing uses the existing FileProvider path and writes on Dispatchers.IO.

## Removed resources

The editor activity/content XML, old sidebar/bottom-sheet/status/action/search/memory/diagnostic XML, associated bindings, native drawer wrappers, old tab adapter, native onboarding bridge, and obsolete symbol input renderer/pager were removed after reference checks. The terminal EdgeSnapBubbleView and feature/symbol-manager layouts still have callers and remain.

## Verification

See the task validation record for exact commands and input hashes. Component tests cover tab selection/removal, mixed Fragment/Compose registrations, sidebar actions, and rendered Compose saveable-state preservation/reset. Full app compilation and device verification must be evaluated separately from those component tests.
