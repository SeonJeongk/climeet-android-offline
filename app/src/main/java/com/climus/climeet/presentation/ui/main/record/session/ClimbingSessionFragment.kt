package com.climus.climeet.presentation.ui.main.record.session

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.viewpager2.widget.ViewPager2
import com.climus.climeet.R
import com.climus.climeet.databinding.FragmentClimbingSessionBinding
import com.climus.climeet.presentation.base.BaseFragment
import com.climus.climeet.presentation.ui.main.record.session.stopwatch.StopwatchViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ClimbingSessionFragment : BaseFragment<FragmentClimbingSessionBinding>(R.layout.fragment_climbing_session) {
    private val viewModel: SessionPagerViewModel by activityViewModels()
    private val stopwatchViewModel: StopwatchViewModel by activityViewModels()
    private lateinit var sessionPagerAdapter: SessionPagerAdapter

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        binding.vm = viewModel

        setViewPager()
        setObserver()
        moveToStopwatchFragment()
    }

    override fun onResume() {
        super.onResume()
        if (stopwatchViewModel.isRunning.value == true) {
            binding.sessionPageIndicator.visibility = View.VISIBLE
            binding.sessionPager.isUserInputEnabled = true
        } else {
            binding.sessionPageIndicator.visibility = View.INVISIBLE
            binding.sessionPager.isUserInputEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopwatchViewModel.unregisterReceiver(requireContext())
    }

    private fun setViewPager() {
        sessionPagerAdapter = SessionPagerAdapter(this)
        binding.sessionPager.adapter = sessionPagerAdapter
        binding.sessionPager.offscreenPageLimit = 1
        binding.sessionPageIndicator.setViewPager(binding.sessionPager)

        binding.sessionPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    if (position == 0) {
                        if (stopwatchViewModel.isRunning.value == true) {
                            binding.sessionPageIndicator.visibility = View.VISIBLE
                        }
                    } else {
                        binding.sessionPageIndicator.visibility = View.INVISIBLE
                    }
                }
            },
        )
    }

    private fun setObserver() {
        stopwatchViewModel.isRunning.observe(viewLifecycleOwner) { stopwatchState ->
            if (stopwatchState) {
                binding.sessionPageIndicator.visibility = View.VISIBLE
                binding.sessionPager.isUserInputEnabled = true
            } else {
                binding.sessionPageIndicator.visibility = View.INVISIBLE
                binding.sessionPager.isUserInputEnabled = false
            }
        }
    }

    private fun moveToStopwatchFragment() {
        viewModel.moveToStopwatchFragmentEvent.observe(
            viewLifecycleOwner,
            Observer {
                binding.sessionPager.currentItem = 0
            },
        )
    }
}
