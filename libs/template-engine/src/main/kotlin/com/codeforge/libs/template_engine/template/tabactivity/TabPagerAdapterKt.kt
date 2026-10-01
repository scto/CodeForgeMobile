package com.codeforge.libs.template_engine.template.tabactivity

fun TabPagerAdapterKt(pkg: String) = """
    package $pkg.ui.main

    import android.content.Context
    import androidx.fragment.app.Fragment
    import androidx.fragment.app.FragmentManager
    import androidx.fragment.app.FragmentPagerAdapter
    import $pkg.R

    private val TAB_TITLES = arrayOf(
        R.string.tab_text_1,
        R.string.tab_text_2
    )

    class SectionsPagerAdapter(private val context: Context, fm: FragmentManager) :
        FragmentPagerAdapter(fm) {

        override fun getItem(position: Int): Fragment {
            return PlaceholderFragment.newInstance(position + 1)
        }

        override fun getPageTitle(position: Int): CharSequence? {
            return context.resources.getString(TAB_TITLES[position])
        }

        override fun getCount(): Int {
            // Show 2 total pages.
            return 2
        }
    }
""".trimIndent()
