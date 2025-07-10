package com.climus.climeet.presentation.ui.main.home.popularcrags.viewpager

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class PopularCragsVPAdpater(
    fragment: Fragment,
) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment =
        when (position) {
            0 -> FollowerOrderFragment()
            else -> RecordOrderFragment()
        }
}
