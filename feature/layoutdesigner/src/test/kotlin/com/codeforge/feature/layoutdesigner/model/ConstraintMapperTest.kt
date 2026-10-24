package com.codeforge.feature.layoutdesigner.model

import com.codeforge.feature.layoutdesigner.xml.LayoutXml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConstraintMapperTest {

    @Test
    fun mapContainer_linksAndMarginsCorrect() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/title"
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="16dp"
                    android:layout_marginTop="24dp"
                    app:layout_constraintStart_toStartOf="parent"
                    app:layout_constraintTop_toTopOf="parent"
                    app:layout_constraintEnd_toEndOf="parent"
                    app:layout_constraintHorizontal_bias="0.3" />
                <Button
                    android:id="@+id/btnSubmit"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="8dp"
                    app:layout_constraintTop_toBottomOf="@id/title"
                    app:layout_constraintStart_toStartOf="@id/title" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        val model = ConstraintMapper.mapContainer(doc.root)

        assertEquals(2, model.children.size)

        val titleSpec = model.children[0]
        assertEquals("title", titleSpec.id)
        assertTrue(titleSpec.widthSpec is DimensionSpec.MatchConstraints)
        assertEquals(16, titleSpec.links[ConstraintAnchor.START]?.marginDp)
        assertEquals(ConstraintTarget.Parent, titleSpec.links[ConstraintAnchor.START]?.target)
        assertEquals(24, titleSpec.links[ConstraintAnchor.TOP]?.marginDp)
        assertEquals(0.3f, titleSpec.horizontalBias, 0.001f)

        val buttonSpec = model.children[1]
        assertEquals("btnSubmit", buttonSpec.id)
        val topLink = buttonSpec.links[ConstraintAnchor.TOP]
        assertNotNull(topLink)
        assertEquals(ConstraintTarget.Sibling("title"), topLink!!.target)
        assertEquals(ConstraintAnchor.BOTTOM, topLink.targetAnchor)
        assertEquals(8, topLink.marginDp)
    }

    @Test
    fun mapContainer_detectsMissingConstraints() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/floating"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintTop_toTopOf="parent" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        val model = ConstraintMapper.mapContainer(doc.root)

        val floatingWarnings = model.children[0].warnings
        assertTrue(floatingWarnings.any { it is ConstraintWarning.MissingHorizontalConstraint })
    }

    @Test
    fun mapContainer_detectsUnknownTargetId() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <Button
                    android:id="@+id/btn"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintStart_toEndOf="@id/nonExistent"
                    app:layout_constraintTop_toTopOf="parent" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        val model = ConstraintMapper.mapContainer(doc.root)

        val btnWarnings = model.children[0].warnings
        assertTrue(btnWarnings.any { it is ConstraintWarning.UnknownTargetId && it.missingId == "nonExistent" })
    }

    @Test
    fun mapContainer_detectsCycles() {
        val xml = """
            <androidx.constraintlayout.widget.ConstraintLayout
                xmlns:android="http://schemas.android.com/apk/res/android"
                xmlns:app="http://schemas.android.com/apk/res-auto"
                android:layout_width="match_parent"
                android:layout_height="match_parent">
                <TextView
                    android:id="@+id/viewA"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintTop_toBottomOf="@id/viewB"
                    app:layout_constraintStart_toStartOf="parent" />
                <TextView
                    android:id="@+id/viewB"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    app:layout_constraintTop_toBottomOf="@id/viewA"
                    app:layout_constraintStart_toStartOf="parent" />
            </androidx.constraintlayout.widget.ConstraintLayout>
        """.trimIndent()

        val doc = LayoutXml.parse(xml)
        val model = ConstraintMapper.mapContainer(doc.root)

        assertTrue(model.warnings.any { it is ConstraintWarning.CircularConstraint })
    }
}
