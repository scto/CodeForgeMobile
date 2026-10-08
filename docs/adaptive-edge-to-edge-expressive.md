# Adaptive Layouts, Edge-to-Edge, Material 3 Expressive

Stand: 2026-10-07. **Nichts davon wurde mit Gradle/Android gebaut** – siehe `agy-tasks/14-verify-adaptive-expressive.md`.

## Breitenklassen (`:core:ui`)
`WindowSizeUtils.kt`: `WidthClass { Compact <600dp, Medium <840dp, Expanded }`, `rememberWidthClass()`,
`rememberIsCompactWidth()`, `rememberIsExpandedWidth()`. Basis ist `LocalConfiguration.screenWidthDp`
(bewusst nicht die WindowSizeClass-API, um API-Wechsel zu vermeiden; Split-Screen/Foldables werden über die Konfiguration erfasst).

## Adaptive Screens
- **Workspace** (`:app` `ProjectWorkspaceRoute`): Expanded → `Row` mit dauerhaftem `WorkspaceDrawer(permanent = true)` (360dp, ohne Menü-Icon);
  sonst `ModalNavigationDrawer`. Beide Pfade teilen sich dasselbe Scaffold (`workspaceContent`).
- **Welcome** (`:feature:welcome`): `NavigableListDetailPaneScaffold` (adaptive 1.1.0: Pakete `...adaptive.layout`/`...adaptive.navigation`),
  Panes in `AnimatedPane`, `currentDestination?.contentKey`, `navigateTo` per Coroutine; Grid `GridCells.Adaptive(160.dp)`.
- Die XML-Layouts existierten im Projekt nicht mehr (alles Compose); es gab nichts zu konvertieren.

## Edge-to-Edge
- `MainActivity`: `enableEdgeToEdge()` vor `super.onCreate` und dynamisch per `SystemBarStyle.auto(...){ isDark }` – Icon-Farbe folgt dem **App-Theme** (`ThemeConfig.resolveIsDark`).
- `themes.xml`: transparente Systemleisten, Cutout `shortEdges` (v28), Kontrast-Erzwingung aus (v29).
- Insets: Scaffolds nutzen Default-`safeDrawing`; Workspace-Scaffold konsumiert sie (`consumeWindowInsets(padding)`), damit geschachtelte
  Editor-Scaffolds nicht doppelt padden; `imePadding()` für den Editor; `WorkspaceDrawer` hält `safeDrawing` (Top/Bottom/Start) ein;
  Onboarding (kein Scaffold) und `GitCloneScreen` nutzen `safeDrawingPadding()`.
- `adjustResize` + `configChanges` im Manifest (kein Activity-Neustart bei Rotation/Faltung).

## Material 3 Expressive
- `CodeForgeTheme` nutzt `MaterialExpressiveTheme` mit `MotionScheme.expressive()` (`@OptIn(ExperimentalMaterial3ExpressiveApi::class)`).
- Nicht umgesetzt: Expressive-Komponenten (`LoadingIndicator`, Button-Gruppen) – Opt-in-API, bei Bedarf einzeln nachziehen.

## Toolchain-Anhebung (ungeprüft)
Kotlin 2.1.21, KSP 2.1.21-2.0.1, Hilt 2.56.2, Compose BOM 2025.09.00, adaptive 1.1.0, activity-compose 1.10.1.
Risiko: `MaterialExpressiveTheme`/`MotionScheme` existieren erst in neueren material3-Versionen; wenn die BOM sie nicht enthält,
BOM erhöhen (dann ggf. compileSdk anheben). Alternative Rückfall: `MaterialTheme` mit `motionScheme`-Parameter weglassen.
