package com.climus.climeet.presentation.ui.main.home.viewpager.introduce

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import com.climus.climeet.databinding.FragmentBannerBinding

class BannerFragment(
    @DrawableRes private val bannerImageRes: Int,
) : Fragment() {
    lateinit var binding: FragmentBannerBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentBannerBinding.inflate(inflater, container, false)
        binding.bannerImageIv.setImageResource(bannerImageRes)
        return binding.root
    }
}
