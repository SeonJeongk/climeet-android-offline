package com.climus.climeet.presentation.ui.main.record.timer.setrecord

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import com.climus.climeet.R
import com.climus.climeet.app.App.Companion.sharedPreferences
import com.climus.climeet.databinding.FragmentTimerRecordBinding
import com.climus.climeet.presentation.base.BaseFragment
import com.climus.climeet.presentation.customview.NoticePopup
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.GymLevelAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.RouteImageAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.SectorNameAdapter
import com.climus.climeet.presentation.ui.main.record.timer.TimerMainViewModel
import com.climus.climeet.presentation.ui.main.record.timer.stopwatch.TimerViewModel
import com.climus.climeet.presentation.ui.main.record.timer.stopwatch.selectcrag.TimerCragSelectBottomSheetViewModel

class SetTimerClimbingRecordFragment : BaseFragment<FragmentTimerRecordBinding>(R.layout.fragment_timer_record) {
    private val viewModel: SetTimerClimbingRecordViewModel by activityViewModels()
    private val timerVM: TimerViewModel by activityViewModels()
    private val cragSelectVM: TimerCragSelectBottomSheetViewModel by activityViewModels()
    private val mainVM: TimerMainViewModel by activityViewModels()

    private lateinit var routeItemAdapter: ClimbingRecordAdapter

    private var cragId: Long = 0L
    private var cragName: String = ""

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.vm = viewModel
        routeItemAdapter = ClimbingRecordAdapter(viewModel)

        binding.ivCelebrate.bringToFront()

        setRecyclerView()
        timerObserve()
        initClickListener()
        initEventObserve()
        initRouteObserve()
        setCragName()
    }

    override fun onResume() {
        super.onResume()
        val restoredCragId = sharedPreferences.getLong("cragId", 0L)
        val restoredCragName = sharedPreferences.getString("cragName", getString(R.string.timer_crag_set_inform))
        binding.tvTitle.text = restoredCragName
        if (restoredCragId > 0L && restoredCragName != null) {
            cragId = restoredCragId
            cragName = restoredCragName
            viewModel.restoreSelectedCrag(restoredCragId, restoredCragName)
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

    private fun timerObserve() {
        timerVM.timeFormat.observe(
            viewLifecycleOwner,
            Observer { timeFormat ->
                binding.tvTime.text = timeFormat
            },
        )
    }

    private fun initClickListener() {
        binding.layoutSelectCrag.setOnClickListener {
            if (timerVM.isStop.value == false) {
                NoticePopup.make(it, "운동중에는 암장을 바꿀 수 없어요!").show()
            }
        }

        binding.layoutIdcTime.setOnClickListener {
            mainVM.moveToStopwatch()
        }
    }

    private fun initRouteObserve() {
        viewModel.itemsLiveData.observe(viewLifecycleOwner) { items ->
            routeItemAdapter.submitList(items)
        }
    }

    private fun setCragName() {
        cragSelectVM.selectedCrag.observe(
            viewLifecycleOwner,
            Observer { cragData ->
                if (cragData != null) {
                    cragId = cragData.id
                    cragName = cragData.name

                    binding.tvTitle.text = cragName
                    viewModel.selectedCrag(cragId, cragName)
                }
            },
        )
    }

    private fun initEventObserve() {
        repeatOnStarted {
            viewModel.event.collect {
                when (it) {
                    is CreateRecordEvent.ShowToastMessage -> showToastMessage(it.msg)
                }
            }
        }
    }
}
