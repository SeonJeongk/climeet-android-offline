package com.climus.climeet.presentation.ui.main.record.session

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.climus.climeet.presentation.ui.main.record.session.record.SessionRecordFragment
import com.climus.climeet.presentation.ui.main.record.session.stopwatch.StopwatchFragment

class SessionPagerAdapter(
    fragment: Fragment,
) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment =
        when (position) {
            0 -> StopwatchFragment()
            else -> SessionRecordFragment()
        }
}
