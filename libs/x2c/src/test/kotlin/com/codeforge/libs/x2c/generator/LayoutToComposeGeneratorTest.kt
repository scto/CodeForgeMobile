package com.codeforge.libs.x2c.generator

import com.codeforge.core.domain.model.GeneratorContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutToComposeGeneratorTest {

    private val gen = LayoutToComposeGeneratorImpl()

    private fun wrap(body: String, tag: String = "LinearLayout", attrs: String = "android:layout_width=\"match_parent\" android:layout_height=\"match_parent\" android:orientation=\"vertical\"") =
        """<$tag xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            xmlns:tools="http://schemas.android.com/tools" $attrs>
            $body
        </$tag>"""

    private fun gen(
        xml: String,
        name: String = "activity_main",
        touched: List<String> = emptyList(),
        ctx: GeneratorContext = GeneratorContext(rPackage = "com.example")
    ) = runBlocking { gen.generateComposable(xml, name, "com.example.ui", touched, ctx) }.getOrThrow()

    private fun balanced(src: String) {
        val code = src.lines().filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }.joinToString("\n")
            .replace(Regex("\"(\\\\.|[^\"\\\\])*\""), "\"\"")
        for ((o, c) in listOf('(' to ')', '{' to '}', '[' to ']')) {
            assertEquals("Klammern $o$c in:\n$src", code.count { it == o }, code.count { it == c })
        }
    }

    @Test fun `Name und Pfad folgen der Konvention`() {
        val r = gen(wrap(""))
        assertEquals("MainScreen", r.composableName)
        assertEquals("src/main/kotlin/com/example/ui/MainScreen.kt", r.targetRelativePath)
        assertTrue(r.sourceContent.startsWith("package com.example.ui"))
        assertTrue("@Preview" in r.sourceContent)
    }

    @Test fun `Namenskonventionen`() {
        assertEquals("ProfileScreen", LayoutNames.composableName("fragment_profile"))
        assertEquals("UserItem", LayoutNames.composableName("item_user"))
        assertEquals("UserItem", LayoutNames.composableName("list_item_user"))
        assertEquals("EditDialogContent", LayoutNames.composableName("dialog_edit"))
        assertEquals("Layout2fa", LayoutNames.composableName("2fa"))
        val (map, notes) = LayoutNames.resolve(listOf("item_user", "user_item"))
        assertEquals(2, map.values.toSet().size)
        assertEquals(1, notes.size)
    }

    @Test fun `LinearLayout vertikal und horizontal`() {
        val v = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a"/>""")).sourceContent
        assertTrue("Column(modifier = modifier.fillMaxSize())" in v)
        val h = gen(wrap("", attrs = """android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:gravity="center_vertical"""")).sourceContent
        assertTrue("Row(" in h && "verticalAlignment = Alignment.CenterVertically" in h)
    }

    @Test fun `weight in Column und Row`() {
        val c = gen(wrap("""<View android:layout_width="match_parent" android:layout_height="0dp" android:layout_weight="1"/>""")).sourceContent
        assertTrue("weight(1f)" in c)
        val r = gen(wrap("""<View android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="2"/>""", attrs = """android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal"""")).sourceContent
        assertTrue("weight(2f)" in r)
    }

    @Test fun `Padding Margin und Groessen`() {
        val s = gen(wrap("""<TextView android:layout_width="100dp" android:layout_height="100dp" android:layout_margin="8dp" android:text="@string/a"/>""", attrs = """android:layout_width="match_parent" android:layout_height="match_parent" android:padding="16dp" android:orientation="vertical"""")).sourceContent
        assertTrue("padding(16.dp)" in s)
        assertTrue("size(100.dp)" in s)
        assertTrue("import androidx.compose.foundation.layout.size" in s)
        assertTrue("import androidx.compose.foundation.layout.fillMaxSize" in s)
    }

    @Test fun `TextView Texte Farben Stil`() {
        val s = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/a" android:textColor="#FF0000" android:textSize="18sp" android:textStyle="bold|italic"
            android:maxLines="2" android:ellipsize="end" android:gravity="center"/>""")).sourceContent
        for (x in listOf("stringResource(R.string.a)", "Color(0xFFFF0000)", "18.sp", "FontWeight.Bold", "FontStyle.Italic", "maxLines = 2", "TextOverflow.Ellipsis", "TextAlign.Center")) assertTrue(x, x in s)
    }

    @Test fun `harter Text wird Hinweis und Literal`() {
        val r = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Hallo &quot;Welt&quot; ${'$'}5"/>"""))
        assertTrue("\\\"Welt\\\"" in r.sourceContent && "\\${'$'}5" in r.sourceContent)
        assertTrue(r.notes.any { "Harter Text" in it })
    }

    @Test fun `Button mit onClick Parameter wenn Code ihn anfasst`() {
        val r = gen(wrap("""<Button android:id="@+id/btn_save" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/save"/>"""), touched = listOf("btn_save"))
        assertTrue("onBtnSaveClick: () -> Unit = {}" in r.sourceContent)
        assertTrue("onClick = onBtnSaveClick" in r.sourceContent)
    }

    @Test fun `Button ohne Zugriff bekommt leeren Klick`() {
        assertTrue("onClick = {}" in gen(wrap("""<Button android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/save"/>""")).sourceContent)
    }

    @Test fun `Button Stile`() {
        val s = gen(wrap("""<com.google.android.material.button.MaterialButton style="@style/Widget.Material3.Button.OutlinedButton" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a"/>""")).sourceContent
        assertTrue("OutlinedButton(" in s)
    }

    @Test fun `TextInputLayout wird zu TextField mit Zustand`() {
        val r = gen(wrap("""<com.google.android.material.textfield.TextInputLayout android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="@string/email">
            <com.google.android.material.textfield.TextInputEditText android:id="@+id/email" android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textEmailAddress"/>
            </com.google.android.material.textfield.TextInputLayout>"""))
        val s = r.sourceContent
        assertTrue("emailValue: String = \"\"" in s)
        assertTrue("onEmailValueChange: (String) -> Unit = {}" in s)
        assertTrue("KeyboardType.Email" in s && "label = { Text(stringResource(R.string.email)) }" in s)
        assertEquals(0, r.todoCount)
    }

    @Test fun `EditText Passwort und mehrzeilig`() {
        val p = gen(wrap("""<EditText android:id="@+id/pw" android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textPassword" android:hint="@string/pw"/>""")).sourceContent
        assertTrue("PasswordVisualTransformation()" in p && "singleLine = true" in p && "placeholder" in p)
        val m = gen(wrap("""<EditText android:id="@+id/n" android:layout_width="match_parent" android:layout_height="wrap_content" android:inputType="textMultiLine" android:minLines="3" android:maxLines="6"/>""")).sourceContent
        assertTrue("minLines = 3" in m && "maxLines = 6" in m && "singleLine" !in m)
    }

    @Test fun `ImageView und ImageButton`() {
        val i = gen(wrap("""<ImageView android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_a" android:contentDescription="@string/d" android:scaleType="centerCrop"/>""")).sourceContent
        assertTrue("painterResource(R.drawable.ic_a)" in i && "ContentScale.Crop" in i && "contentDescription = stringResource(R.string.d)" in i)
        val b = gen(wrap("""<ImageButton android:id="@+id/close" android:layout_width="48dp" android:layout_height="48dp" android:src="@drawable/ic_x" android:contentDescription="@string/c"/>"""), touched = listOf("close")).sourceContent
        assertTrue("IconButton(" in b && "onCloseClick" in b)
    }

    @Test fun `ImageView ohne contentDescription gibt null`() {
        assertTrue("contentDescription = null" in gen(wrap("""<ImageView android:layout_width="48dp" android:layout_height="48dp" android:src="@mipmap/ic"/>""")).sourceContent)
    }

    @Test fun `CheckBox Switch RadioButton`() {
        val s = gen(wrap("""<CheckBox android:id="@+id/c" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a" android:checked="true"/>
            <Switch android:id="@+id/s" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/b"/>
            <RadioGroup android:layout_width="wrap_content" android:layout_height="wrap_content"><RadioButton android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/c1"/></RadioGroup>"""), touched = listOf("c", "s"))
        assertTrue("cChecked: Boolean = true" in s.sourceContent)
        assertTrue("Switch(" in s.sourceContent && "RadioButton(" in s.sourceContent)
        assertTrue(s.notes.any { "RadioGroup" in it })
    }

    @Test fun `ProgressBar Slider SeekBar`() {
        val s = gen(wrap("""<ProgressBar android:layout_width="wrap_content" android:layout_height="wrap_content" android:indeterminate="true"/>
            <SeekBar android:id="@+id/sb" android:layout_width="match_parent" android:layout_height="wrap_content" android:max="10" android:progress="5"/>"""), touched = listOf("sb")).sourceContent
        assertTrue("CircularProgressIndicator(" in s && "valueRange = 0f..10f" in s && "sbValue: Float = 5f" in s)
    }

    @Test fun `ScrollView und HorizontalScrollView`() {
        val s = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a"/>""", tag = "ScrollView")).sourceContent
        assertTrue("verticalScroll(rememberScrollState())" in s && "import androidx.compose.foundation.verticalScroll" in s)
        val h = gen(wrap("", tag = "HorizontalScrollView")).sourceContent
        assertTrue("horizontalScroll(rememberScrollState())" in h)
    }

    @Test fun `CardView wird Card`() {
        val s = gen(wrap("""<androidx.cardview.widget.CardView android:layout_width="match_parent" android:layout_height="wrap_content" app:cardCornerRadius="8dp" app:cardElevation="4dp">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a"/></androidx.cardview.widget.CardView>""")).sourceContent
        assertTrue("Card(" in s && "RoundedCornerShape(8.dp)" in s && "defaultElevation = 4.dp" in s)
    }

    @Test fun `View als Trenner und Space`() {
        val d = gen(wrap("""<View android:layout_width="match_parent" android:layout_height="1dp" android:background="#CCCCCC"/>""")).sourceContent
        assertTrue("HorizontalDivider(" in d && "thickness = 1.dp" in d)
        val sp = gen(wrap("""<Space android:layout_width="match_parent" android:layout_height="16dp"/>""")).sourceContent
        assertTrue("Spacer(" in sp)
    }

    @Test fun `Sichtbarkeit gone invisible und Code-gesteuert`() {
        val r = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:visibility="gone" android:text="@string/a"/>
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:visibility="invisible" android:text="@string/b"/>
            <TextView android:id="@+id/err" android:layout_width="wrap_content" android:layout_height="wrap_content" android:visibility="gone" android:text="@string/c"/>"""),
            ctx = GeneratorContext(rPackage = "com.example", visibilityIds = setOf("err")))
        val s = r.sourceContent
        assertTrue("alpha(0f)" in s)
        assertTrue("errVisible: Boolean = false" in s && "if (errVisible) {" in s)
        assertFalse("stringResource(R.string.a)" in s.substringAfter("fun MainScreen"))
        assertTrue(r.notes.any { "gone" in it })
    }

    @Test fun `Gravity Layout-Gravity in Column Row Box`() {
        val c = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_gravity="center_horizontal" android:text="@string/a"/>""")).sourceContent
        assertTrue("align(Alignment.CenterHorizontally)" in c)
        val b = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:layout_gravity="bottom|end" android:text="@string/a"/>""", tag = "FrameLayout", attrs = """android:layout_width="match_parent" android:layout_height="match_parent"""")).sourceContent
        assertTrue("align(Alignment.BottomEnd)" in b)
    }

    @Test fun `Accessibility contentDescription und importantForAccessibility`() {
        val s = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a" android:contentDescription="@string/d"/>
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/b" android:importantForAccessibility="no"/>""")).sourceContent
        assertTrue("contentDescription = stringResource(R.string.d)" in s && "clearAndSetSemantics {}" in s)
    }

    @Test fun `Resourcen dimen color`() {
        val s = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a" android:textColor="@color/brand" android:padding="@dimen/gap"/>""")).sourceContent
        assertTrue("colorResource(R.color.brand)" in s && "dimensionResource(R.dimen.gap)" in s)
    }

    @Test fun `unbekannte Attribute werden zu TODO`() {
        val r = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a" android:shadowRadius="3"/>"""))
        assertTrue("TODO(x2c): android:shadowRadius=\"3\"" in r.sourceContent)
        assertTrue(r.todoCount >= 1)
    }

    @Test fun `tools-Attribute erzeugen kein TODO`() {
        val r = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a" tools:text="Beispiel"/>"""))
        assertEquals(0, r.todoCount)
    }

    @Test fun `eigene View wird AndroidView mit TODO`() {
        val r = gen(wrap("""<com.example.ui.ChartView android:layout_width="match_parent" android:layout_height="200dp"/>"""))
        assertTrue("AndroidView(" in r.sourceContent && "com.example.ui.ChartView(context)" in r.sourceContent)
        assertEquals(1, r.todoCount)
    }

    @Test fun `include wird Aufruf bekannter Composable`() {
        val r = gen(wrap("""<include layout="@layout/item_header" android:layout_width="match_parent" android:layout_height="wrap_content"/>"""),
            ctx = GeneratorContext(rPackage = "com.example", knownLayouts = mapOf("item_header" to "HeaderRow")))
        assertTrue("HeaderRow(" in r.sourceContent)
        assertTrue(r.notes.none { "muss ebenfalls" in it })
    }

    @Test fun `RecyclerView wird LazyColumn mit Item`() {
        val r = gen(wrap("""<androidx.recyclerview.widget.RecyclerView android:id="@+id/rv" android:layout_width="match_parent" android:layout_height="match_parent" tools:listitem="@layout/item_user"/>"""))
        assertTrue("LazyColumn(" in r.sourceContent && "UserItem()" in r.sourceContent && "rvItems: List<Any>" in r.sourceContent)
        assertTrue(r.todoCount >= 1)
    }

    @Test fun `Grid und horizontale Liste`() {
        val g = gen(wrap("""<androidx.recyclerview.widget.RecyclerView android:layout_width="match_parent" android:layout_height="match_parent" app:layoutManager="androidx.recyclerview.widget.GridLayoutManager" app:spanCount="3"/>""")).sourceContent
        assertTrue("GridCells.Fixed(3)" in g && "import androidx.compose.foundation.lazy.grid.items" in g)
    }

    @Test fun `ConstraintLayout Constraints Margin Dimension Guideline`() {
        val r = gen(wrap("""
            <androidx.constraintlayout.widget.Guideline android:id="@+id/g" android:layout_width="wrap_content" android:layout_height="wrap_content" android:orientation="vertical" app:layout_constraintGuide_percent="0.5"/>
            <TextView android:id="@+id/a" android:layout_width="0dp" android:layout_height="wrap_content" android:text="@string/a"
                app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toStartOf="@id/g" app:layout_constraintTop_toTopOf="parent" android:layout_marginStart="8dp"/>
            <TextView android:id="@+id/b" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/b"
                app:layout_constraintStart_toEndOf="@id/g" app:layout_constraintBottom_toBottomOf="parent" app:layout_constraintHorizontal_bias="0.3"/>""",
            tag = "androidx.constraintlayout.widget.ConstraintLayout"))
        val s = r.sourceContent
        assertTrue("val (a, b) = createRefs()" in s)
        assertTrue("val g = createGuidelineFromStart(0.5f)" in s)
        assertTrue("start.linkTo(parent.start, margin = 8.dp)" in s)
        assertTrue("end.linkTo(g)" in s && "start.linkTo(g)" in s)
        assertTrue("width = Dimension.fillToConstraints" in s)
        assertTrue("horizontalBias = 0.3f" in s)
        assertEquals(0, r.todoCount)
    }

    @Test fun `ConstraintLayout Kette und Barrier`() {
        val r = gen(wrap("""
            <Button android:id="@+id/x" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/x"
                app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toStartOf="@id/y" app:layout_constraintHorizontal_chainStyle="packed"/>
            <Button android:id="@+id/y" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/y"
                app:layout_constraintStart_toEndOf="@id/x" app:layout_constraintEnd_toEndOf="parent"/>
            <androidx.constraintlayout.widget.Barrier android:id="@+id/bar" android:layout_width="wrap_content" android:layout_height="wrap_content" app:barrierDirection="bottom" app:constraint_referenced_ids="x,y"/>""",
            tag = "androidx.constraintlayout.widget.ConstraintLayout")).sourceContent
        assertTrue("createHorizontalChain(x, y, chainStyle = ChainStyle.Packed)" in r)
        assertTrue("val bar = createBottomBarrier(x, y)" in r)
    }

    @Test fun `ConstraintLayout Seitenverhaeltnis`() {
        val s = gen(wrap("""<ImageView android:id="@+id/img" android:layout_width="0dp" android:layout_height="0dp" android:src="@drawable/p" android:contentDescription="@string/d"
            app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent" app:layout_constraintTop_toTopOf="parent" app:layout_constraintDimensionRatio="16:9"/>""",
            tag = "androidx.constraintlayout.widget.ConstraintLayout")).sourceContent
        assertTrue("aspectRatio(16f / 9f)" in s)
    }

    @Test fun `RelativeLayout Regeln als TODO`() {
        val r = gen(wrap("""<TextView android:id="@+id/t" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a" android:layout_centerInParent="true" android:layout_below="@id/x"/>""", tag = "RelativeLayout"))
        assertTrue("align(Alignment.Center)" in r.sourceContent)
        assertTrue("TODO(x2c): layout_below" in r.sourceContent)
    }

    @Test fun `merge Wurzel`() {
        val r = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a"/>""", tag = "merge", attrs = ""))
        assertTrue(r.notes.any { "merge" in it })
        assertTrue("Text(" in r.sourceContent)
    }

    @Test fun `Zeilennummer der TODOs zeigt auf die Quelle`() {
        val xml = "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"match_parent\" android:layout_height=\"match_parent\">\n\n<TextView android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:text=\"@string/a\" android:shadowRadius=\"1\"/>\n</LinearLayout>"
        val r = gen(xml, ctx = GeneratorContext(rPackage = "com.example", sourcePath = "res/layout/a.xml"))
        assertTrue("res/layout/a.xml:3" in r.sourceContent)
    }

    @Test fun `ohne rPackage gibt es Hinweis statt falschem Import`() {
        val r = gen(wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a"/>"""), ctx = GeneratorContext())
        assertFalse("import null" in r.sourceContent)
        assertTrue(r.notes.any { "R-Paket" in it })
    }

    @Test fun `kaputtes XML ist Result failure`() {
        val r = runBlocking { gen.generateComposable("<LinearLayout><", "a", "p") }
        assertTrue(r.isFailure)
        assertTrue(runBlocking { gen.generateComposable("", "a", "p") }.isFailure)
    }

    @Test fun `DOCTYPE wird abgelehnt (XXE)`() {
        val xml = "<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"/>"
        assertTrue(runBlocking { gen.generateComposable(xml, "a", "p") }.isFailure)
    }

    @Test fun `deterministisch`() {
        val xml = wrap("""<TextView android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/a"/><Button android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/b"/>""")
        assertEquals(gen(xml).sourceContent, gen(xml).sourceContent)
    }

    @Test fun `Beispiel Formular ist vollstaendig und ausgewogen`() {
        val r = gen(wrap("""
            <TextView android:id="@+id/title" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/app_name" android:textSize="20sp" android:textStyle="bold" android:layout_marginBottom="8dp"/>
            <Button android:id="@+id/submit" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="@string/send"/>
            <ProgressBar android:id="@+id/spinner" android:layout_width="wrap_content" android:layout_height="wrap_content" android:visibility="gone" android:layout_gravity="center_horizontal"/>""",
            attrs = """android:layout_width="match_parent" android:layout_height="match_parent" android:orientation="vertical" android:padding="16dp""""),
            name = "activity_login", touched = listOf("title", "submit"), ctx = GeneratorContext(rPackage = "com.example", visibilityIds = setOf("spinner")))
        balanced(r.sourceContent)
        assertTrue("fun LoginScreen(" in r.sourceContent && "titleText: String = stringResource(R.string.app_name)" in r.sourceContent)
        assertEquals(0, r.todoCount)
    }

    @Test fun `Beispiel ConstraintLayout Screen ist ausgewogen`() {
        val r = gen(wrap("""
            <ImageView android:id="@+id/avatar" android:layout_width="64dp" android:layout_height="64dp" android:src="@drawable/ic_user" android:contentDescription="@string/avatar" app:layout_constraintTop_toTopOf="parent" app:layout_constraintStart_toStartOf="parent" android:layout_margin="16dp"/>
            <androidx.recyclerview.widget.RecyclerView android:id="@+id/list" android:layout_width="0dp" android:layout_height="0dp" app:layout_constraintTop_toBottomOf="@id/avatar" app:layout_constraintBottom_toBottomOf="parent" app:layout_constraintStart_toStartOf="parent" app:layout_constraintEnd_toEndOf="parent" tools:listitem="@layout/item_user"/>""",
            tag = "androidx.constraintlayout.widget.ConstraintLayout"), name = "fragment_profile")
        balanced(r.sourceContent)
        assertTrue("fun ProfileScreen(" in r.sourceContent && "height = Dimension.fillToConstraints" in r.sourceContent)
    }

    @Test fun `Beispiel Listenelement`() {
        val r = gen(wrap("""
            <ImageView android:id="@+id/icon" android:layout_width="40dp" android:layout_height="40dp" android:src="@drawable/ic_a" android:contentDescription="@null"/>
            <LinearLayout android:layout_width="0dp" android:layout_height="wrap_content" android:layout_weight="1" android:orientation="vertical" android:layout_marginStart="12dp">
              <TextView android:id="@+id/name" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/n" tools:text="Anna"/>
              <TextView android:id="@+id/mail" android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="@string/m"/>
            </LinearLayout>""", attrs = """android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="horizontal" android:padding="12dp""""),
            name = "item_user", touched = listOf("name", "mail", "icon"))
        balanced(r.sourceContent)
        assertEquals("UserItem", r.composableName)
        assertTrue("nameText: String = stringResource(R.string.n)" in r.sourceContent)
        assertTrue("nameText = \"Anna\"" in r.sourceContent)
        assertTrue("weight(1f)" in r.sourceContent)
    }
}
