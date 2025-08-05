package com.climus.climeet.presentation.ui.main.record.session.record

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.climus.climeet.R
import com.climus.climeet.app.App.Companion.sharedPreferences
import com.climus.climeet.databinding.FragmentSessionRecordBinding
import com.climus.climeet.presentation.base.BaseFragment
import com.climus.climeet.presentation.customview.NoticePopup
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.GymLevelAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.RouteImageAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.SectorNameAdapter
import com.climus.climeet.presentation.ui.main.record.session.SessionPagerViewModel
import com.climus.climeet.presentation.ui.main.record.session.gym.GymSelectViewModel
import com.climus.climeet.presentation.ui.main.record.session.record.SessionRouteAdapter
import com.climus.climeet.presentation.ui.main.record.session.stopwatch.StopwatchViewModel

class SessionRecordFragment : BaseFragment<FragmentSessionRecordBinding>(R.layout.fragment_session_record) {
    private val viewModel: SessionRecordViewModel by activityViewModels()
    private val stopwatchViewModel: StopwatchViewModel by activityViewModels()
    private val gymSelectViewModel: GymSelectViewModel by activityViewModels()
    private val sessionPagerViewModel: SessionPagerViewModel by activityViewModels()

    private lateinit var routeItemAdapter: SessionRouteAdapter

    private var gymId: Long = 0L
    private var gymName: String = ""

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.vm = viewModel
        routeItemAdapter = SessionRouteAdapter(viewModel)

        binding.ivCelebrate.bringToFront()

        setRecyclerView()
        observeStopwatchTime()
        initClickListener()
        initEventObserve()
        initRouteObserve()
        observeSelectedGym()
    }

    override fun onResume() {
        super.onResume()
        val restoredGymId = sharedPreferences.getLong("gymId", 0L)
        val restoredGymName = sharedPreferences.getString("gymName", getString(R.string.session_gym_select_prompt))
        binding.tvTitle.text = restoredGymName
        if (restoredGymId > 0L && restoredGymName != null) {
            gymId = restoredGymId
            gymName = restoredGymName
            viewModel.restoreSelectedGym(restoredGymId, restoredGymName)
        }
    }

    private fun setRecyclerView() {
        binding.rvSectorName.adapter = SectorNameAdapter()
        binding.rvSectorLevel.adapter = GymLevelAdapter()
        binding.rvSectorImage.adapter = RouteImageAdapter()
        binding.rvRouteRecord.adapter = routeItemAdapter
        binding.rvSectorName.itemAnimator = null
        binding.rvSectorLevel.itemAnimator = null
        binding.rvSectorImage.itemAnimator = null
    }

    private fun observeStopwatchTime() {
        stopwatchViewModel.timeFormat.observe(
            viewLifecycleOwner,
            Observer { timeFormat ->
                binding.tvTime.text = timeFormat
            },
        )
    }

    private fun initClickListener() {
        binding.layoutSelectGym.setOnClickListener {
            if (stopwatchViewModel.isStop.value == false) {
                NoticePopup.make(it, "운동중에는 암장을 바꿀 수 없어요!").show()
            }
        }

        binding.layoutIdcTime.setOnClickListener {
            sessionPagerViewModel.moveToStopwatch()
        }
    }

    private fun initRouteObserve() {
        viewModel.itemsLiveData.observe(viewLifecycleOwner) { items ->
            routeItemAdapter.submitList(items)
        }
    }

    private fun observeSelectedGym() {
        gymSelectViewModel.selectedGym.observe(
            viewLifecycleOwner,
            Observer { gym ->
                if (gym != null) {
                    gymId = gym.id
                    gymName = gym.name

                    binding.tvTitle.text = gymName
                    viewModel.selectGym(gymId, gymName)
                }
            },
        )
    }

    private fun initEventObserve() {
        repeatOnStarted {
            viewModel.event.collect {
                when (it) {
                    is SessionRecordEvent.ShowToastMessage -> showToastMessage(it.msg)
                }
            }
        }
    }
}
