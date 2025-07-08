package com.climus.climeet.presentation.ui.main.home.popularcrags

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.climus.climeet.R
import com.climus.climeet.databinding.FragmentPopularCragsBinding
import com.climus.climeet.presentation.base.BaseFragment
import com.climus.climeet.presentation.ui.main.home.popularcrags.viewpager.PopularCragsVPAdpater
import com.climus.climeet.presentation.ui.toSearchProfile
import com.google.android.material.tabs.TabLayoutMediator

class PopularCragsFragment : BaseFragment<FragmentPopularCragsBinding>(R.layout.fragment_popular_crags) {
    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupPopularCrags()
        setupOnClickListener()
    }

    private fun setupOnClickListener() {
        binding.icPopularCragsBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.icPopularCragsSearch.setOnClickListener {
            findNavController().toSearchProfile()
        }
    }

    private fun setupPopularCrags() {
        val popularCragsAdapter = PopularCragsVPAdpater(this)
        binding.vpPopularCragsDetail.adapter = popularCragsAdapter

        val tabMenu = arrayListOf(" 팔로워순 ", " 기록순 ")
        TabLayoutMediator(binding.tbPopularCragsDetail, binding.vpPopularCragsDetail) { tab, position ->
            tab.text = tabMenu[position]
        }.attach()
    }
}
