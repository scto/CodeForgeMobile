#!/usr/bin/env python3
"""Android-Support-Library -> AndroidX. Eingabe: 'check' (Standard, nur Bericht) oder 'apply' (Dateien ändern)."""
import os, re, sys
from common import args, walk, read, write, rel

CLASSES = {
    # AppCompat
    "android.support.v7.app.AppCompatActivity": "androidx.appcompat.app.AppCompatActivity",
    "android.support.v7.app.AppCompatDelegate": "androidx.appcompat.app.AppCompatDelegate",
    "android.support.v7.app.AppCompatDialog": "androidx.appcompat.app.AppCompatDialog",
    "android.support.v7.app.ActionBar": "androidx.appcompat.app.ActionBar",
    "android.support.v7.app.ActionBarDrawerToggle": "androidx.appcompat.app.ActionBarDrawerToggle",
    "android.support.v7.app.AlertDialog": "androidx.appcompat.app.AlertDialog",
    "android.support.v7.view.ActionMode": "androidx.appcompat.view.ActionMode",
    "android.support.v7.widget.Toolbar": "androidx.appcompat.widget.Toolbar",
    "android.support.v7.widget.SearchView": "androidx.appcompat.widget.SearchView",
    "android.support.v7.widget.PopupMenu": "androidx.appcompat.widget.PopupMenu",
    "android.support.v7.widget.SwitchCompat": "androidx.appcompat.widget.SwitchCompat",
    "android.support.v7.widget.AppCompatTextView": "androidx.appcompat.widget.AppCompatTextView",
    "android.support.v7.widget.AppCompatButton": "androidx.appcompat.widget.AppCompatButton",
    "android.support.v7.widget.AppCompatEditText": "androidx.appcompat.widget.AppCompatEditText",
    "android.support.v7.widget.AppCompatImageView": "androidx.appcompat.widget.AppCompatImageView",
    "android.support.v7.widget.AppCompatImageButton": "androidx.appcompat.widget.AppCompatImageButton",
    "android.support.v7.widget.AppCompatCheckBox": "androidx.appcompat.widget.AppCompatCheckBox",
    "android.support.v7.widget.AppCompatSpinner": "androidx.appcompat.widget.AppCompatSpinner",
    "android.support.v7.widget.LinearLayoutCompat": "androidx.appcompat.widget.LinearLayoutCompat",
    "android.support.v7.widget.CardView": "androidx.cardview.widget.CardView",
    "android.support.v7.widget.RecyclerView": "androidx.recyclerview.widget.RecyclerView",
    "android.support.v7.widget.LinearLayoutManager": "androidx.recyclerview.widget.LinearLayoutManager",
    "android.support.v7.widget.GridLayoutManager": "androidx.recyclerview.widget.GridLayoutManager",
    "android.support.v7.widget.StaggeredGridLayoutManager": "androidx.recyclerview.widget.StaggeredGridLayoutManager",
    "android.support.v7.widget.DefaultItemAnimator": "androidx.recyclerview.widget.DefaultItemAnimator",
    "android.support.v7.widget.DividerItemDecoration": "androidx.recyclerview.widget.DividerItemDecoration",
    "android.support.v7.widget.helper.ItemTouchHelper": "androidx.recyclerview.widget.ItemTouchHelper",
    "android.support.v7.util.DiffUtil": "androidx.recyclerview.widget.DiffUtil",
    "android.support.v7.util.ListUpdateCallback": "androidx.recyclerview.widget.ListUpdateCallback",
    "android.support.v7.preference.Preference": "androidx.preference.Preference",
    "android.support.v7.preference.PreferenceFragmentCompat": "androidx.preference.PreferenceFragmentCompat",
    "android.support.v7.preference.PreferenceManager": "androidx.preference.PreferenceManager",
    "android.support.v7.graphics.Palette": "androidx.palette.graphics.Palette",
    # Core / v4
    "android.support.v4.app.Fragment": "androidx.fragment.app.Fragment",
    "android.support.v4.app.FragmentActivity": "androidx.fragment.app.FragmentActivity",
    "android.support.v4.app.FragmentManager": "androidx.fragment.app.FragmentManager",
    "android.support.v4.app.FragmentTransaction": "androidx.fragment.app.FragmentTransaction",
    "android.support.v4.app.FragmentPagerAdapter": "androidx.fragment.app.FragmentPagerAdapter",
    "android.support.v4.app.FragmentStatePagerAdapter": "androidx.fragment.app.FragmentStatePagerAdapter",
    "android.support.v4.app.DialogFragment": "androidx.fragment.app.DialogFragment",
    "android.support.v4.app.ListFragment": "androidx.fragment.app.ListFragment",
    "android.support.v4.app.LoaderManager": "androidx.loader.app.LoaderManager",
    "android.support.v4.content.Loader": "androidx.loader.content.Loader",
    "android.support.v4.content.CursorLoader": "androidx.loader.content.CursorLoader",
    "android.support.v4.content.ContextCompat": "androidx.core.content.ContextCompat",
    "android.support.v4.content.FileProvider": "androidx.core.content.FileProvider",
    "android.support.v4.content.LocalBroadcastManager": "androidx.localbroadcastmanager.content.LocalBroadcastManager",
    "android.support.v4.content.res.ResourcesCompat": "androidx.core.content.res.ResourcesCompat",
    "android.support.v4.app.ActivityCompat": "androidx.core.app.ActivityCompat",
    "android.support.v4.app.NotificationCompat": "androidx.core.app.NotificationCompat",
    "android.support.v4.app.NotificationManagerCompat": "androidx.core.app.NotificationManagerCompat",
    "android.support.v4.app.JobIntentService": "androidx.core.app.JobIntentService",
    "android.support.v4.app.ShareCompat": "androidx.core.app.ShareCompat",
    "android.support.v4.app.TaskStackBuilder": "androidx.core.app.TaskStackBuilder",
    "android.support.v4.app.ActivityOptionsCompat": "androidx.core.app.ActivityOptionsCompat",
    "android.support.v4.graphics.drawable.DrawableCompat": "androidx.core.graphics.drawable.DrawableCompat",
    "android.support.v4.graphics.ColorUtils": "androidx.core.graphics.ColorUtils",
    "android.support.v4.view.ViewCompat": "androidx.core.view.ViewCompat",
    "android.support.v4.view.GravityCompat": "androidx.core.view.GravityCompat",
    "android.support.v4.view.MenuItemCompat": "androidx.core.view.MenuItemCompat",
    "android.support.v4.view.ViewPager": "androidx.viewpager.widget.ViewPager",
    "android.support.v4.view.PagerAdapter": "androidx.viewpager.widget.PagerAdapter",
    "android.support.v4.widget.DrawerLayout": "androidx.drawerlayout.widget.DrawerLayout",
    "android.support.v4.widget.SwipeRefreshLayout": "androidx.swiperefreshlayout.widget.SwipeRefreshLayout",
    "android.support.v4.widget.NestedScrollView": "androidx.core.widget.NestedScrollView",
    "android.support.v4.widget.TextViewCompat": "androidx.core.widget.TextViewCompat",
    "android.support.v4.widget.SlidingPaneLayout": "androidx.slidingpanelayout.widget.SlidingPaneLayout",
    "android.support.v4.util.LruCache": "androidx.collection.LruCache",
    "android.support.v4.util.ArrayMap": "androidx.collection.ArrayMap",
    "android.support.v4.util.ArraySet": "androidx.collection.ArraySet",
    "android.support.v4.util.SparseArrayCompat": "androidx.collection.SparseArrayCompat",
    "android.support.v4.util.Pair": "androidx.core.util.Pair",
    "android.support.v13.app.FragmentCompat": "androidx.legacy.app.FragmentCompat",
    # Material / Design
    "android.support.design.widget.FloatingActionButton": "com.google.android.material.floatingactionbutton.FloatingActionButton",
    "android.support.design.widget.Snackbar": "com.google.android.material.snackbar.Snackbar",
    "android.support.design.widget.TabLayout": "com.google.android.material.tabs.TabLayout",
    "android.support.design.widget.NavigationView": "com.google.android.material.navigation.NavigationView",
    "android.support.design.widget.BottomNavigationView": "com.google.android.material.bottomnavigation.BottomNavigationView",
    "android.support.design.widget.BottomSheetBehavior": "com.google.android.material.bottomsheet.BottomSheetBehavior",
    "android.support.design.widget.BottomSheetDialog": "com.google.android.material.bottomsheet.BottomSheetDialog",
    "android.support.design.widget.BottomSheetDialogFragment": "com.google.android.material.bottomsheet.BottomSheetDialogFragment",
    "android.support.design.widget.TextInputLayout": "com.google.android.material.textfield.TextInputLayout",
    "android.support.design.widget.TextInputEditText": "com.google.android.material.textfield.TextInputEditText",
    "android.support.design.widget.CoordinatorLayout": "androidx.coordinatorlayout.widget.CoordinatorLayout",
    "android.support.design.widget.AppBarLayout": "com.google.android.material.appbar.AppBarLayout",
    "android.support.design.widget.CollapsingToolbarLayout": "com.google.android.material.appbar.CollapsingToolbarLayout",
    "android.support.design.widget.MaterialButton": "com.google.android.material.button.MaterialButton",
    "android.support.design.widget.CheckableImageButton": "com.google.android.material.internal.CheckableImageButton",
    "android.support.design.widget.Chip": "com.google.android.material.chip.Chip",
    "android.support.design.widget.ChipGroup": "com.google.android.material.chip.ChipGroup",
    "android.support.design.chip.Chip": "com.google.android.material.chip.Chip",
    "android.support.design.chip.ChipGroup": "com.google.android.material.chip.ChipGroup",
    "android.support.design.card.MaterialCardView": "com.google.android.material.card.MaterialCardView",
    "android.support.design.button.MaterialButton": "com.google.android.material.button.MaterialButton",
    "android.support.design.bottomappbar.BottomAppBar": "com.google.android.material.bottomappbar.BottomAppBar",
    "android.support.design.theme.MaterialComponentsViewInflater": "com.google.android.material.theme.MaterialComponentsViewInflater",
    "android.support.coordinatorlayout.widget.CoordinatorLayout": "androidx.coordinatorlayout.widget.CoordinatorLayout",
    "android.support.constraint.ConstraintLayout": "androidx.constraintlayout.widget.ConstraintLayout",
    "android.support.constraint.ConstraintSet": "androidx.constraintlayout.widget.ConstraintSet",
    "android.support.constraint.Guideline": "androidx.constraintlayout.widget.Guideline",
    "android.support.constraint.Group": "androidx.constraintlayout.widget.Group",
    "android.support.constraint.Barrier": "androidx.constraintlayout.widget.Barrier",
    "android.support.constraint.Placeholder": "androidx.constraintlayout.widget.Placeholder",
    # Sonstiges
    "android.support.multidex.MultiDex": "androidx.multidex.MultiDex",
    "android.support.multidex.MultiDexApplication": "androidx.multidex.MultiDexApplication",
    "android.support.media.ExifInterface": "androidx.exifinterface.media.ExifInterface",
    "android.support.customtabs.CustomTabsIntent": "androidx.browser.customtabs.CustomTabsIntent",
    "android.support.percent.PercentRelativeLayout": "androidx.percentlayout.widget.PercentRelativeLayout",
    "android.support.percent.PercentFrameLayout": "androidx.percentlayout.widget.PercentFrameLayout",
    "android.support.wear.widget.WearableRecyclerView": "androidx.wear.widget.WearableRecyclerView",
    # Architecture Components
    "android.arch.lifecycle.ViewModel": "androidx.lifecycle.ViewModel",
    "android.arch.lifecycle.ViewModelProvider": "androidx.lifecycle.ViewModelProvider",
    "android.arch.lifecycle.ViewModelProviders": "androidx.lifecycle.ViewModelProviders",
    "android.arch.lifecycle.AndroidViewModel": "androidx.lifecycle.AndroidViewModel",
    "android.arch.lifecycle.LiveData": "androidx.lifecycle.LiveData",
    "android.arch.lifecycle.MutableLiveData": "androidx.lifecycle.MutableLiveData",
    "android.arch.lifecycle.MediatorLiveData": "androidx.lifecycle.MediatorLiveData",
    "android.arch.lifecycle.Observer": "androidx.lifecycle.Observer",
    "android.arch.lifecycle.Transformations": "androidx.lifecycle.Transformations",
    "android.arch.lifecycle.Lifecycle": "androidx.lifecycle.Lifecycle",
    "android.arch.lifecycle.LifecycleOwner": "androidx.lifecycle.LifecycleOwner",
    "android.arch.lifecycle.LifecycleObserver": "androidx.lifecycle.LifecycleObserver",
    "android.arch.lifecycle.OnLifecycleEvent": "androidx.lifecycle.OnLifecycleEvent",
    "android.arch.lifecycle.ProcessLifecycleOwner": "androidx.lifecycle.ProcessLifecycleOwner",
    "android.arch.persistence.room.Room": "androidx.room.Room",
    "android.arch.persistence.room.RoomDatabase": "androidx.room.RoomDatabase",
    "android.arch.persistence.room.Database": "androidx.room.Database",
    "android.arch.persistence.room.Dao": "androidx.room.Dao",
    "android.arch.persistence.room.Entity": "androidx.room.Entity",
    "android.arch.persistence.room.PrimaryKey": "androidx.room.PrimaryKey",
    "android.arch.persistence.room.ColumnInfo": "androidx.room.ColumnInfo",
    "android.arch.persistence.room.Query": "androidx.room.Query",
    "android.arch.persistence.room.Insert": "androidx.room.Insert",
    "android.arch.persistence.room.Update": "androidx.room.Update",
    "android.arch.persistence.room.Delete": "androidx.room.Delete",
    "android.arch.persistence.room.OnConflictStrategy": "androidx.room.OnConflictStrategy",
    "android.arch.persistence.room.TypeConverter": "androidx.room.TypeConverter",
    "android.arch.persistence.room.TypeConverters": "androidx.room.TypeConverters",
    "android.arch.persistence.room.Ignore": "androidx.room.Ignore",
    "android.arch.persistence.room.Embedded": "androidx.room.Embedded",
    "android.arch.persistence.room.Relation": "androidx.room.Relation",
    "android.arch.persistence.room.ForeignKey": "androidx.room.ForeignKey",
    "android.arch.persistence.room.Index": "androidx.room.Index",
    "android.arch.persistence.room.Transaction": "androidx.room.Transaction",
    "android.arch.persistence.room.migration.Migration": "androidx.room.migration.Migration",
    "android.arch.persistence.db.SupportSQLiteDatabase": "androidx.sqlite.db.SupportSQLiteDatabase",
    "android.arch.paging.PagedList": "androidx.paging.PagedList",
    "android.arch.paging.PagedListAdapter": "androidx.paging.PagedListAdapter",
    "android.arch.paging.DataSource": "androidx.paging.DataSource",
    "android.arch.paging.LivePagedListBuilder": "androidx.paging.LivePagedListBuilder",
    "android.arch.navigation.NavController": "androidx.navigation.NavController",
    "android.arch.core.executor.testing.InstantTaskExecutorRule": "androidx.arch.core.executor.testing.InstantTaskExecutorRule",
    # Tests
    "android.support.test.InstrumentationRegistry": "androidx.test.platform.app.InstrumentationRegistry",
    "android.support.test.runner.AndroidJUnit4": "androidx.test.ext.junit.runners.AndroidJUnit4",
    "android.support.test.runner.AndroidJUnitRunner": "androidx.test.runner.AndroidJUnitRunner",
    "android.support.test.rule.ActivityTestRule": "androidx.test.rule.ActivityTestRule",
    "android.support.test.espresso.Espresso": "androidx.test.espresso.Espresso",
    "android.support.test.espresso.action.ViewActions": "androidx.test.espresso.action.ViewActions",
    "android.support.test.espresso.assertion.ViewAssertions": "androidx.test.espresso.assertion.ViewAssertions",
    "android.support.test.espresso.matcher.ViewMatchers": "androidx.test.espresso.matcher.ViewMatchers",
}

# Paket-Präfixe (Annotations & gleichnamige Unterpakete), wenn keine exakte Klasse bekannt ist
PREFIXES = [
    ("android.support.annotation.", "androidx.annotation."),
    ("android.support.v7.appcompat.", "androidx.appcompat."),
    ("android.support.test.espresso.", "androidx.test.espresso."),
    ("android.support.test.", "androidx.test."),
    ("android.arch.lifecycle.", "androidx.lifecycle."),
    ("android.arch.persistence.room.", "androidx.room."),
    ("android.arch.persistence.db.", "androidx.sqlite.db."),
    ("android.arch.paging.", "androidx.paging."),
    ("android.arch.navigation.", "androidx.navigation."),
]

DEPS = {
    "com.android.support:appcompat-v7": "androidx.appcompat:appcompat:1.6.1",
    "com.android.support:design": "com.google.android.material:material:1.9.0",
    "com.android.support:support-v4": "androidx.legacy:legacy-support-v4:1.0.0",
    "com.android.support:support-v13": "androidx.legacy:legacy-support-v13:1.0.0",
    "com.android.support:recyclerview-v7": "androidx.recyclerview:recyclerview:1.3.2",
    "com.android.support:cardview-v7": "androidx.cardview:cardview:1.0.0",
    "com.android.support:support-annotations": "androidx.annotation:annotation:1.7.0",
    "com.android.support:support-compat": "androidx.core:core:1.12.0",
    "com.android.support:support-core-utils": "androidx.legacy:legacy-support-core-utils:1.0.0",
    "com.android.support:multidex": "androidx.multidex:multidex:2.0.1",
    "com.android.support:palette-v7": "androidx.palette:palette:1.0.0",
    "com.android.support:preference-v7": "androidx.preference:preference:1.2.1",
    "com.android.support:customtabs": "androidx.browser:browser:1.7.0",
    "com.android.support:exifinterface": "androidx.exifinterface:exifinterface:1.3.6",
    "com.android.support:percent": "androidx.percentlayout:percentlayout:1.0.0",
    "com.android.support.constraint:constraint-layout": "androidx.constraintlayout:constraintlayout:2.1.4",
    "android.arch.lifecycle:extensions": "androidx.lifecycle:lifecycle-extensions:2.2.0",
    "android.arch.lifecycle:runtime": "androidx.lifecycle:lifecycle-runtime:2.6.2",
    "android.arch.lifecycle:viewmodel": "androidx.lifecycle:lifecycle-viewmodel:2.6.2",
    "android.arch.lifecycle:livedata": "androidx.lifecycle:lifecycle-livedata:2.6.2",
    "android.arch.lifecycle:compiler": "androidx.lifecycle:lifecycle-compiler:2.6.2",
    "android.arch.persistence.room:runtime": "androidx.room:room-runtime:2.6.1",
    "android.arch.persistence.room:compiler": "androidx.room:room-compiler:2.6.1",
    "android.arch.persistence.room:rxjava2": "androidx.room:room-rxjava2:2.6.1",
    "android.arch.persistence.room:testing": "androidx.room:room-testing:2.6.1",
    "android.arch.paging:runtime": "androidx.paging:paging-runtime:3.2.1",
    "android.arch.navigation:navigation-fragment": "androidx.navigation:navigation-fragment:2.7.6",
    "android.arch.navigation:navigation-ui": "androidx.navigation:navigation-ui:2.7.6",
    "com.android.support.test:runner": "androidx.test:runner:1.5.2",
    "com.android.support.test:rules": "androidx.test:rules:1.5.0",
    "com.android.support.test.espresso:espresso-core": "androidx.test.espresso:espresso-core:3.5.1",
}

TEXT_EXTS = {"java", "kt", "kts", "xml", "gradle", "pro", "txt", "cfg", "properties", "md"}
FQN = re.compile(r"android\.(?:support|arch)\.[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)*")
DEP = re.compile(r"(com\.android\.support(?:\.[a-z]+)*|android\.arch\.[a-z.]+):([A-Za-z0-9_.-]+)(?::([^'\"\s)]+))?")


def map_fqn(name):
    parts = name.split(".")
    for i in range(len(parts), 2, -1):  # längster Präfix zuerst (innere Klassen: ...AlertDialog.Builder)
        head = ".".join(parts[:i])
        if head in CLASSES:
            return ".".join([CLASSES[head]] + parts[i:])
    for old, new in PREFIXES:
        if name.startswith(old):
            return new + name[len(old):]
    return None


def convert(text, unknown):
    def fqn(m):
        new = map_fqn(m.group(0))
        if new is None:
            unknown.add(m.group(0))
            return m.group(0)
        return new

    def dep(m):
        key = f"{m.group(1)}:{m.group(2)}"
        return DEPS.get(key, m.group(0))

    return DEP.sub(dep, FQN.sub(fqn, text))


def main():
    project, mode = args()
    apply = mode.lower() == "apply"
    changed, unknown = [], set()
    for path in walk(project):
        ext = path.rsplit(".", 1)[-1].lower() if "." in path else ""
        if ext not in TEXT_EXTS:
            continue
        try:
            text = read(path)
        except OSError:
            continue
        if "android.support" not in text and "android.arch" not in text and "com.android.support" not in text:
            continue
        new = convert(text, unknown)
        if new != text:
            changed.append(rel(path, project))
            if apply:
                write(path, new)
    props = os.path.join(project, "gradle.properties")
    flags = {"android.useAndroidX": "true", "android.enableJetifier": "true"}
    if os.path.isfile(props):
        txt = read(props)
        add = [f"{k}={v}" for k, v in flags.items() if re.search(rf"^\s*{re.escape(k)}\s*=", txt, re.M) is None]
    else:
        txt, add = "", [f"{k}={v}" for k, v in flags.items()]
    if add:
        print(f"gradle.properties: {', '.join(add)} {'ergänzt' if apply else 'würden ergänzt'}")
        if apply:
            write(props, txt + ("" if txt.endswith("\n") or not txt else "\n") + "\n".join(add) + "\n")
    print(f"{'Geändert' if apply else 'Würden geändert'}: {len(changed)} Dateien")
    for c in changed[:200]:
        print("  " + c)
    if unknown:
        print("\nManuell prüfen (keine bekannte AndroidX-Entsprechung):")
        for u in sorted(unknown):
            print("  " + u)
    print("\nHinweis: Abhängigkeits-Versionen sind Vorschläge – bitte aktuelle Versionen prüfen." if changed else "")
    if not apply and (changed or add):
        print("Mit Eingabe 'apply' erneut starten, um die Änderungen zu schreiben (vorher committen!).")


main()
