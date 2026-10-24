package com.codeforge.feature.layoutdesigner.model

enum class LayoutTemplate(val displayName: String) {
    EMPTY_CONSTRAINT("Leer (ConstraintLayout)"),
    EMPTY_LINEAR("Leer (LinearLayout vertikal)"),
    LIST_ITEM("Listeneintrag (Bild + zwei Zeilen)"),
    FORM("Formular (zwei Eingabefelder + Button)"),
    SCROLL_PAGE("Scroll-Seite mit Toolbar");

    fun xml(): String = when (this) {
        EMPTY_CONSTRAINT -> """<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

</androidx.constraintlayout.widget.ConstraintLayout>
"""
        EMPTY_LINEAR -> """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

</LinearLayout>
"""
        LIST_ITEM -> """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:padding="16dp"
    android:gravity="center_vertical">

    <ImageView
        android:id="@+id/item_icon"
        android:layout_width="48dp"
        android:layout_height="48dp"
        android:importantForAccessibility="no" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:layout_marginStart="16dp"
        android:orientation="vertical">

        <TextView
            android:id="@+id/item_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textSize="16sp"
            tools:text="Titelzeile" />

        <TextView
            android:id="@+id/item_subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="4dp"
            android:textSize="14sp"
            tools:text="Untertitel oder Beschreibung" />
    </LinearLayout>
</LinearLayout>
"""
        FORM -> """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <EditText
        android:id="@+id/input_first"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="text"
        tools:hint="Erstes Eingabefeld" />

    <EditText
        android:id="@+id/input_second"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="12dp"
        android:inputType="text"
        tools:hint="Zweites Eingabefeld" />

    <Button
        android:id="@+id/button_submit"
        android:layout_width="match_parent"
        android:layout_height="48dp"
        android:layout_marginTop="16dp"
        tools:text="Absenden" />
</LinearLayout>
"""
        SCROLL_PAGE -> """<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical">

    <Toolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        tools:title="Überschrift" />

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView
                android:id="@+id/page_content"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                tools:text="Seiteninhalt..." />
        </LinearLayout>
    </ScrollView>
</LinearLayout>
"""
    }
}
