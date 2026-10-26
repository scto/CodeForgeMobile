package com.codeforge.libs.x2c.wiring

import com.codeforge.core.domain.model.WireStatus
import com.codeforge.core.domain.model.WireTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeEntryWirerTest {

    private val wirer = ComposeEntryWirerImpl()
    private val target = WireTarget("activity_main", "MainScreen", composablePackage = "com.example.ui.compose")

    private val activity = """
package com.example

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
""".trimStart()

    @Test fun `Activity wird auf setContent umgestellt`() {
        val r = wirer.wire("MainActivity.kt", activity, target)
        assertEquals(WireStatus.WIRED, r.status)
        val s = r.newContent!!
        assertTrue("        setContent {\n            MaterialTheme {\n                MainScreen()\n            }\n        }" in s)
        assertFalse("setContentView" in s)
        assertTrue("import androidx.activity.compose.setContent" in s)
        assertTrue("import androidx.compose.material3.MaterialTheme" in s)
        assertTrue("import com.example.ui.compose.MainScreen" in s)
        assertEquals(3, r.addedImports.size)
    }

    @Test fun `eigenes Theme wird verwendet`() {
        val r = wirer.wire("MainActivity.kt", activity, target.copy(themeName = "AppTheme", themePackage = "com.example.ui.theme"))
        val s = r.newContent!!
        assertTrue("AppTheme {" in s && "import com.example.ui.theme.AppTheme" in s)
        assertFalse("MaterialTheme" in s)
    }

    @Test fun `gleiches Paket braucht keinen Import`() {
        val r = wirer.wire("MainActivity.kt", activity, target.copy(composablePackage = "com.example"))
        assertFalse("import com.example.MainScreen" in r.newContent!!)
    }

    @Test fun `vorhandene Imports werden nicht doppelt`() {
        val src = activity.replace("import android.os.Bundle", "import android.os.Bundle\nimport androidx.activity.compose.setContent")
        val r = wirer.wire("MainActivity.kt", src, target)
        assertEquals(1, Regex("import androidx.activity.compose.setContent").findAll(r.newContent!!).count())
    }

    @Test fun `findViewById verhindert die Umstellung mit Zeile`() {
        val src = activity.replace("setContentView(R.layout.activity_main)", "setContentView(R.layout.activity_main)\n        findViewById<android.view.View>(R.id.x)")
        val r = wirer.wire("MainActivity.kt", src, target)
        assertEquals(WireStatus.SKIPPED, r.status)
        assertTrue("findViewById" in r.reason!! && "Zeile 10" in r.reason!!)
        assertEquals(null, r.newContent)
    }

    @Test fun `Kommentare und Strings loesen kein Hindernis aus`() {
        val src = activity.replace("super.onCreate(savedInstanceState)", "super.onCreate(savedInstanceState)\n        // findViewById war frueher hier\n        val s = \"ActivityMainBinding\"")
        assertEquals(WireStatus.WIRED, wirer.wire("MainActivity.kt", src, target).status)
    }

    @Test fun `ViewBinding verhindert die Umstellung`() {
        val src = activity.replace("setContentView(R.layout.activity_main)", "val b = ActivityMainBinding.inflate(layoutInflater)")
        assertEquals(WireStatus.SKIPPED, wirer.wire("MainActivity.kt", src, target).status)
    }

    @Test fun `Java wird nie angefasst`() {
        val r = wirer.wire("MainActivity.java", activity, target)
        assertEquals(WireStatus.SKIPPED, r.status)
        assertTrue("Task 50" in r.reason!!)
    }

    @Test fun `unbekannte Basisklasse wird uebersprungen`() {
        val r = wirer.wire("MainActivity.kt", activity.replace("AppCompatActivity()", "BaseActivity()"), target)
        assertEquals(WireStatus.SKIPPED, r.status)
        assertTrue("BaseActivity" in r.reason!!)
    }

    @Test fun `zwei setContentView werden uebersprungen`() {
        val r = wirer.wire("MainActivity.kt", activity.replace("setContentView(R.layout.activity_main)", "setContentView(R.layout.activity_main)\n        setContentView(R.layout.activity_main)"), target)
        assertEquals(WireStatus.SKIPPED, r.status)
    }

    @Test fun `anderes Layout in der Datei wird uebersprungen`() {
        val r = wirer.wire("MainActivity.kt", activity + "\nval x = R.layout.other\n", target)
        assertEquals(WireStatus.SKIPPED, r.status)
    }

    @Test fun `Pruefhinweise fuer Menue und onClick-Methoden`() {
        val src = activity.replace("    override fun onCreate", "    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean = true\n\n    fun onSave(view: android.view.View) {}\n\n    override fun onCreate")
        val r = wirer.wire("MainActivity.kt", src, target)
        assertEquals(WireStatus.WIRED, r.status)
        assertEquals(2, r.reviewHints.size)
        assertTrue(r.reviewHints.any { "Optionsmen" in it.message })
        assertTrue(r.reviewHints.any { "onSave" in it.message })
    }

    private val fragment = """
package com.example

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment

class ProfileFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }
}
""".trimStart()
    private val fTarget = WireTarget("fragment_profile", "ProfileScreen", themeName = "AppTheme", themePackage = "com.example.ui.theme", composablePackage = "com.example")

    @Test fun `Fragment bekommt ComposeView`() {
        val r = wirer.wire("ProfileFragment.kt", fragment, fTarget)
        assertEquals(WireStatus.WIRED, r.status)
        val s = r.newContent!!
        assertTrue("return ComposeView(requireContext()).apply {" in s)
        assertTrue("ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed" in s)
        assertTrue("AppTheme {" in s && "ProfileScreen()" in s)
        assertTrue("import androidx.compose.ui.platform.ComposeView" in s)
        assertFalse("inflater.inflate" in s)
    }

    @Test fun `Fragment mit Layout im Konstruktor wird uebersprungen`() {
        val r = wirer.wire("ProfileFragment.kt", "package a\n\nimport androidx.fragment.app.Fragment\n\nclass P : Fragment(R.layout.fragment_profile)\n", fTarget)
        assertEquals(WireStatus.SKIPPED, r.status)
    }

    @Test fun `Fragment mit findViewById in onViewCreated wird uebersprungen`() {
        val src = fragment.replace("    override fun onCreateView", "    override fun onViewCreated(v: View, s: Bundle?) { v.findViewById<View>(R.id.a) }\n\n    override fun onCreateView")
        assertEquals(WireStatus.SKIPPED, wirer.wire("ProfileFragment.kt", src, fTarget).status)
    }

    @Test fun `Ergebnis ist deterministisch und Kriterien sind benannt`() {
        assertEquals(wirer.wire("A.kt", activity, target), wirer.wire("A.kt", activity, target))
        assertTrue(Stage2Criteria.RULES.size >= 5)
    }
}
