# Microsoft Fluent 2 Integration in Android Jetpack Compose

Dieses Handbuch beschreibt die vollständige Einrichtung, die Implementierung von UI-Komponenten sowie fortgeschrittene Konzepte wie **Custom Branding** (Farbanpassungen) und die **Jetpack Navigation** mithilfe des **Microsoft Fluent 2** Designsystems in einer nativen Android-App mit **Kotlin** und **Jetpack Compose**.

---

## 1. Setup & Abhängigkeiten

Fügen Sie die offiziellen Fluent UI Android-Bibliotheken von Microsoft in die `build.gradle.kts` (Module-Ebene) ein.

```kotlin
dependencies {
    // Core-Bibliothek für Tokens und Basisfunktionalitäten
    implementation("com.microsoft.fluentui:fluentui_core:0.0.1-alpha") // Version bei Bedarf anpassen
    
    // UI-Steuerelemente und Jetpack Compose Komponenten
    implementation("com.microsoft.fluentui:fluentui_controls:0.0.1-alpha")
    
    // Offizielle Microsoft Fluent Systemsymbole
    implementation("com.microsoft.fluentui:fluentui_icons:0.0.1-alpha")
    
    // Empfohlen für Navigation Szenarien
    implementation("androidx.navigation:navigation-compose:2.8.0")
}
```
*Hinweis: Stellen Sie sicher, dass Ihre `minSdkVersion` in den Build-Konfigurationen mindestens auf **23** gesetzt ist.*

---

## 2. Globales Theme & Custom Branding (Farb-Tokens)

Standardmäßig bietet das `FluentTheme` eine funktionale Farbpalette für Light und Dark Mode. Sie können jedoch die Alias-Tokens überschreiben, um das Design an Ihr eigenes Marken-Branding anzupassen.

```kotlin
import androidx.compose.runtime.Composable
import com.microsoft.fluentui.theme.FluentTheme
import com.microsoft.fluentui.theme.token.FluentColor
import com.microsoft.fluentui.theme.token.FluentColors
import androidx.compose.ui.graphics.Color

// Eigene Markenfarben definieren
private val CustomBrandColorLight = Color(0xFF0066CC) // Ihr Primärblau
private val CustomBrandColorDark = Color(0xFF4DA6FF)

@Composable
fun MyAppTheme(
    useDarkTheme: Boolean = androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Bereitstellen eigener Farb-Tokens
    val customColors = if (useDarkTheme) {
        FluentColors(
            brand = FluentColor(
                light = CustomBrandColorLight,
                dark = CustomBrandColorDark
            )
            // Hier können weitere Tokens wie background, textPrimary etc. überschrieben werden
        )
    } else {
        FluentColors(
            brand = FluentColor(
                light = CustomBrandColorLight,
                dark = CustomBrandColorDark
            )
        )
    }

    // Übergabe der eigenen Konfiguration an das FluentTheme
    FluentTheme(
        colors = customColors
    ) {
        content()
    }
}
```

---

## 3. App-Architektur mit Jetpack Navigation

Hier sehen Sie ein vollständiges Produktionsszenario, das die **Jetpack Navigation Component** verwendet. Die Struktur kombiniert eine `TopAppBar`, eine `BottomBar` für den Wechsel zwischen Screens und wechselnde Bildschirminhalte.

```kotlin
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.microsoft.fluentui.tokenized.navigation.TopAppBar
import com.microsoft.fluentui.tokenized.navigation.BottomBar
import com.microsoft.fluentui.tokenized.navigation.BottomBarItem

// Definition der Navigations-Routen
sealed class Screen(val route: String, val title: String, val icon: Int) {
    object Home : Screen("home", "Startseite", com.microsoft.fluentui.icons.R.drawable.ic_fluent_home_24_regular)
    object Settings : Screen("settings", "Einstellungen", com.microsoft.fluentui.icons.R.drawable.ic_fluent_settings_24_regular)
}

@Composable
fun MainNavigationGraph() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Ermittle den aktuellen Titel basierend auf der Route
    val currentTitle = when(currentRoute) {
        Screen.Home.route -> Screen.Home.title
        Screen.Settings.route -> Screen.Settings.title
        else -> "Fluent 2 App"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = currentTitle,
                // Optionales Icon links falls Rückwärtsnavigation notwendig ist
            )
        },
        bottomBar = {
            BottomBar {
                val items = listOf(Screen.Home, Screen.Settings)
                items.forEach { screen ->
                    BottomBarItem(
                        title = screen.title,
                        icon = screen.icon,
                        selected = currentRoute == screen.route,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }
        }
    }
}
```

---

## 4. UI-Komponenten & Code-Beispiele

### A. Buttons (Schaltflächen)
Fluent 2 bietet vorkonfigurierte Schaltflächen-Stile (`ButtonStyle`) und Größen (`ButtonSize`).

```kotlin
import com.microsoft.fluentui.tokenized.controls.Button
import com.microsoft.fluentui.tokenized.controls.ButtonSize
import com.microsoft.fluentui.tokenized.controls.ButtonStyle

@Composable
fun HomeScreen() {
    // Beispiel für Buttons im Fluent 2-Design
    Button(
        text = "Primäre Aktion",
        style = ButtonStyle.ButtonPrimary,
        size = ButtonSize.Medium,
        onClick = { /* Aktion ausführen */ }
    )
}
```

### B. Cards & Oberflächen (Mica & Acrylic)
Fluent 2 nutzt visuelle Tiefeneffekte. Da Android Unschärfen anders als Windows handhabt, wird dies über Modifikatoren gelöst.

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun FluentAcrylicCard() {
    Box(
        modifier = Modifier
            .padding(16.dp)
            .background(
                color = Color(0x801F1F1F), // Halbdurchsichtiges Fluent-Dunkelgrau
                shape = RoundedCornerShape(12.dp) // Abgerundete Ecken nach Fluent-Token
            )
            .blur(15.dp) // Acrylic-Glaseffekt (verfügbar ab Android 12+)
            .padding(24.dp)
    ) {
        Text(text = "Inhalt auf einer Acrylic-Oberfläche", color = Color.White)
    }
}
```

### C. Text & Typografie
Verwenden Sie Fluent-Typografie-Tokens anstelle von harten Schriftgrößen, um Textskalierungen plattformkonform zu halten.

```kotlin
import androidx.compose.material3.Text
import com.microsoft.fluentui.theme.FluentTheme

@Composable
fun FluentTextExample() {
    Text(
        text = "Mica & Acrylic Surfaces",
        style = FluentTheme.typography.titleLarge,
        color = FluentTheme.colorScheme.textPrimary
    )
}
```

### D. Dialoge (Benachrichtigungsfenster)
Dialoge unterbrechen den Nutzerfluss für wichtige, binäre Entscheidungen oder Bestätigungen.

```kotlin
import com.microsoft.fluentui.tokenized.controls.Dialog
import androidx.compose.material3.Text

@Composable
fun FluentDialogExample(showDialog: Boolean, onDismiss: () -> Unit) {
    if (showDialog) {
        Dialog(
            title = "Aktion bestätigen",
            onDismissRequest = onDismiss,
            confirmButtonText = "Zustimmen",
            onConfirmClick = { onDismiss() },
            dismissButtonText = "Abbrechen",
            onDismissClick = onDismiss
        ) {
            Text("Möchten Sie diese Einstellungsänderung dauerhaft in Ihr Branding übernehmen?")
        }
    }
}
```

### E. BottomSheets (Bodenmenüs)
Ideal für optionale Untermenüs, erweiterte Filter oder kontextbezogene Aktionen.

```kotlin
import com.microsoft.fluentui.tokenized.BottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FluentBottomSheetExample(isOpen: Boolean, onDismiss: () -> Unit) {
    if (isOpen) {
        BottomSheet(
            sheetState = rememberModalBottomSheetState(),
            onDismissRequest = onDismiss
        ) {
            // Inhalt des BottomSheets (z.B. Listen aus Fluent ListItems)
        }
    }
}
```

---
*Dokumentation generiert für die Implementierung nativer Windows & plattformübergreifender Designsysteme in Android Jetpack Compose.*
