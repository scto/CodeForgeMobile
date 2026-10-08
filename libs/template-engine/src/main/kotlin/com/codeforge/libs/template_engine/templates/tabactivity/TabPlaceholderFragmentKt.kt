package com.codeforge.libs.template_engine.templates.tabactivity

fun TabPlaceholderFragmentKt(pkg: String) = """
    package $pkg.ui.main

    import android.os.Bundle
    import android.view.LayoutInflater
    import android.view.View
    import android.view.ViewGroup
    import android.widget.TextView
    import androidx.fragment.app.Fragment
    import androidx.lifecycle.Observer
    import androidx.lifecycle.ViewModelProvider
    import $pkg.databinding.FragmentMainBinding

    class PlaceholderFragment : Fragment() {

        private lateinit var pageViewModel: PageViewModel
        private var _binding: FragmentMainBinding? = null

        private val binding get() = _binding!!

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            pageViewModel = ViewModelProvider(this).get(PageViewModel::class.java).apply {
                setIndex(arguments?.getInt(ARG_SECTION_NUMBER) ?: 1)
            }
        }

        override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View? {

            _binding = FragmentMainBinding.inflate(inflater, container, false)
            val root = binding.root

            val textView: TextView = binding.sectionLabel
            pageViewModel.text.observe(viewLifecycleOwner, Observer {
                textView.text = it
            })
            return root
        }

        companion object {
            private const val ARG_SECTION_NUMBER = "section_number"

            @JvmStatic
            fun newInstance(sectionNumber: Int): PlaceholderFragment {
                return PlaceholderFragment().apply {
                    arguments = Bundle().apply {
                        putInt(ARG_SECTION_NUMBER, sectionNumber)
                    }
                }
            }
        }

        override fun onDestroyView() {
            super.onDestroyView()
            _binding = null
        }
    }
""".trimIndent()
