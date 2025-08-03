package com.climus.climeet.presentation.ui.main.record.timer.stopwatch

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.core.content.edit
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.climus.climeet.R
import com.climus.climeet.app.App.Companion.sharedPreferences
import com.climus.climeet.data.local.ClimbingRecordData
import com.climus.climeet.data.local.RouteRecordData
import com.climus.climeet.data.repository.MainRepository
import com.climus.climeet.databinding.FragmentTimerBinding
import com.climus.climeet.presentation.base.BaseFragment
import com.climus.climeet.presentation.customview.DeleteDialog
import com.climus.climeet.presentation.customview.NoticePopup
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.GymLevelAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.RouteImageAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.SectorNameAdapter
import com.climus.climeet.presentation.ui.main.record.model.LevelItemForAvg
import com.climus.climeet.presentation.ui.main.record.timer.setrecord.ClimbingRecordAdapter
import com.climus.climeet.presentation.ui.main.record.timer.setrecord.RecordAverageAdapter
import com.climus.climeet.presentation.ui.main.record.timer.setrecord.SetTimerClimbingRecordViewModel
import com.climus.climeet.presentation.ui.main.record.timer.setrecord.TimerSubmissionEffect
import com.climus.climeet.presentation.ui.main.record.timer.setrecord.TimerSubmissionState
import com.climus.climeet.presentation.ui.main.record.timer.stopwatch.selectcrag.TimerCragSelectBottomSheetFragment
import com.climus.climeet.presentation.ui.main.record.timer.stopwatch.selectcrag.TimerCragSelectBottomSheetViewModel
import com.climus.climeet.service.TimerService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

enum class ViewMode {
    START,
    PAUSE,
    RESTART,
    STOP,
}

@AndroidEntryPoint
class TimerFragment : BaseFragment<FragmentTimerBinding>(R.layout.fragment_timer) {
    @Inject
    lateinit var repository: MainRepository

    private val timerVM: TimerViewModel by activityViewModels()
    private val recordVM: SetTimerClimbingRecordViewModel by activityViewModels()
    private val cragSelectVM: TimerCragSelectBottomSheetViewModel by activityViewModels()

    private lateinit var routeItemAdapter: ClimbingRecordAdapter

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.mainVM = timerVM
        binding.recordVM = recordVM

        routeItemAdapter = ClimbingRecordAdapter(recordVM)

        timerVM.registerReceiver(requireContext())
        sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        checkService()
        initTimerLayout()
        initClickListener()
        timerObserve()
        pauseObserve()
        initRouteObserve()
        observeSubmission()

        initCragName()
        setAverage()
        setAverageByLevel()
        setRouteRecyclerView()
    }

    private fun checkService() {
        val serviceState = TimerService.serviceRunning

        if (serviceState.value == null) {
            viewMode(ViewMode.STOP)
            timerVM.isStart.value = false
            timerVM.isPaused.value = false
            timerVM.isRestart.value = false
            timerVM.isStop.value = true
            timerVM.isRunning.value = false
            timerVM.pauseTime.value = 0L
            timerVM.pauseState.value = ""
            updateStatePref()
            recordVM.restoreDraft()
        } else {
            setViewModel()
        }
    }

    private fun initCragName() {
        cragSelectVM.selectedCrag.observe(
            viewLifecycleOwner,
            Observer { cragData ->
                if (cragData != null) {
                    val cragId = cragData.id
                    val cragName = cragData.name
                    val date = LocalDate.now().toString()
                    sharedPreferences.edit().putString("cragName", cragName).apply()
                    sharedPreferences.edit().putLong("cragId", cragId).apply()

                    binding.tvTitle.text = cragName
                    val recordData = ClimbingRecordData(
                        id = 1,
                        gymId = cragId,
                        gymName = cragName,
                        date = date,
                        time = "",
                        avgDifficulty = 0,
                    )

                    recordVM.saveDraft(recordData)
                }
            },
        )
    }

    private fun setAverage() {
        recordVM.avgCompleteRate.observe(
            viewLifecycleOwner,
            Observer { rate ->
                val progress = (rate * 100).roundToInt()
                binding.pbAvgComplete.progress = progress
                binding.tvAvgComplete.text = "$progress%"
            },
        )
    }

    private fun setAverageByLevel() {
        recordVM.updateRecord.observe(
            viewLifecycleOwner,
            Observer {
                var items: List<LevelItemForAvg>
                val levelAdapter = RecordAverageAdapter()
                binding.rvAvgRecord.adapter = levelAdapter

                viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                    val levelData = repository.getAllLevelRecord()
                    val firstRecords = mutableListOf<RouteRecordData>()

                    val levelNameSet = mutableSetOf<String>()
                    for (record in levelData) {
                        if (!levelNameSet.contains(record.levelName)) {
                            levelNameSet.add(record.levelName)
                            firstRecords.add(record)
                        }
                    }
                    items = firstRecords.map { data ->
                        val attemptCount = repository.getAttemptCount(data.levelName)
                        val successCount = repository.getSuccessCount(data.levelName)
                        val levelColor = data.levelColor
                        LevelItemForAvg(data.levelName, levelColor, attemptCount, successCount)
                    }
                    withContext(Dispatchers.Main) {
                        levelAdapter.setItems(items)
                    }
                }
            },
        )
    }

    private fun setRouteRecyclerView() {
        binding.rvSectorName.adapter = SectorNameAdapter()
        binding.rvSectorLevel.adapter = GymLevelAdapter()
        binding.rvSectorImage.adapter = RouteImageAdapter()
        binding.rvRouteRecord.adapter = routeItemAdapter
        binding.rvSectorName.itemAnimator = null
        binding.rvSectorLevel.itemAnimator = null
        binding.rvSectorImage.itemAnimator = null
    }

    private fun initRouteObserve() {
        recordVM.itemsLiveData.observe(viewLifecycleOwner) { items ->
            routeItemAdapter.submitList(items)
        }
    }

    override fun onStart() {
        super.onStart()
        val serviceState = TimerService.serviceRunning

        if (serviceState.value != null) {
            val id = sharedPreferences.getLong("cragId", 0)
            val name = sharedPreferences.getString("cragName", getString(R.string.timer_crag_set_inform))
            if (id > 0L && name != null) {
                recordVM.restoreSelectedCrag(id, name)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val restoredCragId = sharedPreferences.getLong("cragId", 0L)
        if (timerVM.isStop.value == true &&
            recordVM.submissionState.value !is TimerSubmissionState.Preserved &&
            restoredCragId <= 0L
        ) {
            binding.tvTitle.text = getString(R.string.timer_crag_set_inform)
            recordVM.totalRoute.value = "--"
            recordVM.totalComplete.value = "--"
            recordVM.avgLevel.value = "--"
        } else {
            binding.tvTitle.text = sharedPreferences.getString("cragName", getString(R.string.timer_crag_set_inform))
        }

        setViewModel()
        timerObserve()
    }

    override fun onDestroy() {
        super.onDestroy()
        timerVM.unregisterReceiver(requireContext())
    }

    private fun setViewModel() {
        timerVM.isStart.value = sharedPreferences.getBoolean(KEY_IS_START, false)
        timerVM.isPaused.value = sharedPreferences.getBoolean(KEY_IS_PAUSE, false)
        timerVM.isRestart.value = sharedPreferences.getBoolean(KEY_IS_RESTART, false)
        timerVM.isStop.value = sharedPreferences.getBoolean(KEY_IS_STOP, true)
        timerVM.isRunning.value = sharedPreferences.getBoolean(KEY_IS_RUNNING, true)
        timerVM.pauseTime.value = sharedPreferences.getLong(PAUSE_TIME, 0L)
    }

    private fun updateStatePref() {
        sharedPreferences.edit {
            timerVM.isStart.value?.let { putBoolean(KEY_IS_START, it) }
            timerVM.isPaused.value?.let { putBoolean(KEY_IS_PAUSE, it) }
            timerVM.isRestart.value?.let { putBoolean(KEY_IS_RESTART, it) }
            timerVM.isStop.value?.let { putBoolean(KEY_IS_STOP, it) }
            timerVM.isRunning.value?.let { putBoolean(KEY_IS_RUNNING, it) }
        }
    }

    private fun timerObserve() {
        if (recordVM.submissionState.value is TimerSubmissionState.Preserved) {
            recordVM.restoredDuration.value?.let { binding.tvTime.text = it }
            return
        }
        timerVM.pauseTimeFormat.observe(viewLifecycleOwner) { pauseTime ->
            val isEditableDraft = recordVM.submissionState.value == TimerSubmissionState.Draft
            if (isEditableDraft && pauseTime != null && pauseTime != "00:00") {
                sharedPreferences.edit { putString("pauseTimeFormat", pauseTime) }
                binding.tvTime.text = pauseTime
            }
        }

        if (timerVM.isPaused.value == true) {
            val time = sharedPreferences.getString("pauseTimeFormat", "00:00")
            binding.tvTime.text = time
        } else {
            timerVM.timeFormat.observe(viewLifecycleOwner) { timeFormat ->
                if (recordVM.submissionState.value == TimerSubmissionState.Draft) {
                    binding.tvTime.text = timeFormat
                }
            }
        }
    }

    private fun pauseObserve() {
        timerVM.pauseState.observe(viewLifecycleOwner) { state ->
            if (state == "yes") {
                viewMode(ViewMode.PAUSE)
                updateViewModel(
                    start = false,
                    pause = true,
                    restart = false,
                    stop = false,
                    running = false,
                )
            } else if (state == "no") {
                viewMode(ViewMode.RESTART)
                updateViewModel(
                    start = false,
                    pause = false,
                    restart = true,
                    stop = false,
                    running = true,
                )
            }
        }

        recordVM.resetView.observe(viewLifecycleOwner) { reset ->
            if (reset) {
                val canShowDetails = timerVM.isRunning.value != true &&
                    (timerVM.isPaused.value == true || recordVM.restoredElapsedTimeMillis.value != null)
                if (canShowDetails) {
                    showRestoredDraftDetails()
                }
            }
        }
    }

    private fun initTimerLayout() {
        timerVM.isStart.observe(viewLifecycleOwner) { isStart ->
            if (isStart) {
                viewMode(ViewMode.START)
                hidePausedDraftDetails()
            }
        }
        timerVM.isPaused.observe(viewLifecycleOwner) { isPaused ->
            if (isPaused) {
                viewMode(ViewMode.PAUSE)
                if (recordVM.itemsLiveData.value?.isEmpty() == false) {
                    showRestoredDraftDetails()
                }
            }
        }
        timerVM.isRestart.observe(viewLifecycleOwner) { isRestart ->
            if (isRestart) {
                viewMode(ViewMode.RESTART)
                timerObserve()
                hidePausedDraftDetails()
            }
        }
        timerVM.isStop.observe(viewLifecycleOwner) { isStop ->
            if (isStop) {
                viewMode(ViewMode.STOP)
                hidePausedDraftDetails()
            }
        }
    }

    private fun initClickListener() {
        binding.layoutSelectCrag.setOnClickListener {
            when {
                recordVM.isCatalogRetryAvailable.value -> recordVM.retryCatalog()
                recordVM.submissionState.value is TimerSubmissionState.Preserved -> showDiscardDraftDialog()
                timerVM.isStop.value == false -> {
                    NoticePopup.make(it, "운동중에는 암장을 바꿀 수 없어요!").show()
                }

                else -> showCragSelectBottomSheet()
            }
        }
        binding.ivStart.setOnClickListener {
            if (recordVM.submissionState.value is TimerSubmissionState.Preserved) {
                showDiscardDraftDialog()
            } else if (binding.tvTitle.text == getString(R.string.timer_crag_set_inform)) {
                NoticePopup.make(it, "운동 전, 암장을 먼저 선택해주세요.").show()
            } else {
                startStopwatch()
            }
        }
        binding.ivPause.setOnClickListener {
            pauseStopwatch()
        }
        binding.ivRestart.setOnClickListener {
            restartStopwatch()
        }
        binding.ivStop.setOnClickListener {
            NoticePopup.make(it, "정지 버튼을 길게 누르면\n운동이 종료됩니다.").show()
        }
        binding.ivStop.setOnLongClickListener {
            val endTime = if (timerVM.isPaused.value == true) {
                timerVM.pauseTime.value
            } else {
                timerVM.time.value
            } ?: return@setOnLongClickListener true
            sharedPreferences.edit(commit = true) { putLong("stopTime", endTime) }
            stopServiceForSubmission()
            recordVM.submitRecord(endTime)
            true
        }
    }

    private fun observeSubmission() {
        repeatOnStarted {
            recordVM.submissionState.collect { state ->
                binding.ivStop.isEnabled = state == TimerSubmissionState.Draft
                if (state == TimerSubmissionState.Submitting || state is TimerSubmissionState.Preserved) {
                    binding.tvTitle.text = recordVM.cragName.ifBlank {
                        sharedPreferences.getString("cragName", getString(R.string.timer_crag_set_inform))
                    }
                    recordVM.restoredDuration.value?.let { binding.tvTime.text = it }
                    recordVM.restoredElapsedTimeMillis.value?.let(timerVM::restoreElapsedTime)
                    if (recordVM.items.value.isNotEmpty()) showRestoredDraftDetails()
                }
            }
        }
        repeatOnStarted {
            recordVM.restoredDuration.collect { duration ->
                val state = recordVM.submissionState.value
                if (duration != null &&
                    state != TimerSubmissionState.Succeeded
                ) {
                    binding.tvTitle.text = recordVM.cragName
                    binding.tvTime.text = duration
                    recordVM.restoredElapsedTimeMillis.value?.let(timerVM::restoreElapsedTime)
                }
            }
        }
        repeatOnStarted {
            recordVM.submissionEffect.collect { effect ->
                when (effect) {
                    is TimerSubmissionEffect.ShowMessage -> showToastMessage(effect.message)
                    TimerSubmissionEffect.DraftDiscarded -> {
                        binding.tvTime.text = "00:00"
                        sharedPreferences.edit {
                            putLong(PAUSE_TIME, 0L)
                            putString("pauseTimeFormat", "00:00")
                        }
                        showCragSelectBottomSheet()
                    }
                    TimerSubmissionEffect.NavigateToComplete -> completeSubmission()
                }
            }
        }
    }

    private fun showDiscardDraftDialog() {
        DeleteDialog(
            requireContext(),
            "전송하지 못한 기록이 남아 있어요.\n삭제한 뒤 새 운동을 시작할까요?",
            "삭제 후 시작",
            "기록 유지",
        ) { shouldDiscard ->
            if (shouldDiscard) recordVM.discardPreservedDraft()
        }.show()
    }

    private fun stopServiceForSubmission() {
        if (TimerService.serviceRunning.value == true) {
            context?.stopService(Intent(context, TimerService::class.java))
        }
        updateViewModel(start = false, pause = false, restart = false, stop = true, running = false)
        updateStatePref()
    }

    private fun completeSubmission() {
        stopStopwatch()
        cragSelectVM.resetItem()
        recordVM.resetAtStop()
        recordVM.acknowledgeSubmissionSuccess()
        requireActivity().supportFragmentManager.beginTransaction()
            .add(R.id.main_container, ClimbingCompleteFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun startStopwatch() {
        viewMode(ViewMode.START)
        hidePausedDraftDetails()
        val initialElapsedTime = recordVM.restoredElapsedTimeMillis.value ?: 0L
        timerVM.restoreElapsedTime(initialElapsedTime)
        val intent = Intent(context, TimerService::class.java).apply {
            putExtra("command", "START")
            putExtra(TimerService.EXTRA_INITIAL_ELAPSED_TIME, initialElapsedTime)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(intent)
        } else {
            context?.startService(intent)
        }

        updateViewModel(start = true, pause = false, restart = false, stop = false, running = true)
    }

    private fun showRestoredDraftDetails() {
        routeItemAdapter.submitList(recordVM.items.value.toList())
        binding.layoutAvgComplete.visibility = View.VISIBLE
        binding.tvTimeTitle.visibility = View.VISIBLE
        binding.layoutRouteRecord.visibility = View.VISIBLE
        recordVM.isAvgToggleOn.value = false
        recordVM.isRouteToggleOn.value = true
        binding.rvRouteRecord.post(binding.rvRouteRecord::requestLayout)
    }

    private fun hidePausedDraftDetails() {
        binding.layoutAvgComplete.visibility = View.GONE
        binding.tvTimeTitle.visibility = View.GONE
        binding.layoutRouteRecord.visibility = View.GONE
    }

    private fun pauseStopwatch() {
        viewMode(ViewMode.PAUSE)
        val intent = Intent(context, TimerService::class.java).apply {
            putExtra("command", "PAUSE")
        }
        context?.startService(intent)

        timerVM.pauseTime.value = sharedPreferences.getLong("pauseTime", 0L)

        updateViewModel(start = false, pause = true, restart = false, stop = false, running = false)
    }

    private fun restartStopwatch() {
        viewMode(ViewMode.RESTART)
        hidePausedDraftDetails()
        val intent = Intent(context, TimerService::class.java).apply {
            putExtra("command", "RESTART")
        }
        context?.startService(intent)

        updateViewModel(start = false, pause = false, restart = true, stop = false, running = true)
    }

    private fun stopStopwatch() {
        viewMode(ViewMode.STOP)
        binding.tvTime.text = "00:00"

        if (TimerService.serviceRunning.value == true) {
            val intent = Intent(context, TimerService::class.java)
            context?.stopService(intent)
        }

        updateViewModel(start = false, pause = false, restart = false, stop = true, running = false)

        sharedPreferences.edit { putString(TOP_CHALLENGE, "--") }
        sharedPreferences.edit { putString(TOP_COMPLETE, "--") }
        sharedPreferences.edit { putString(TOP_LEVEL, "--") }
        sharedPreferences.edit {
            putLong(PAUSE_TIME, 0L)
            putString("pauseTimeFormat", "00:00")
        }
        binding.tvTitle.text = getString(R.string.timer_crag_set_inform)

        recordVM.totalRoute.value = "--"
        recordVM.totalComplete.value = "--"
        recordVM.avgLevel.value = "--"

        recordVM.apiCheck = false
        recordVM.isSelectedCrag.value = false
    }

    private fun showCragSelectBottomSheet() {
        cragSelectVM.resetItem()
        recordVM.apiCheck = false
        val bottomSheetFragment = TimerCragSelectBottomSheetFragment()
        bottomSheetFragment.show(parentFragmentManager, "timerCragSelectBottomSheet")
    }

    private fun updateViewModel(
        start: Boolean,
        pause: Boolean,
        restart: Boolean,
        stop: Boolean,
        running: Boolean,
    ) {
        timerVM.isStart.value = start
        timerVM.isPaused.value = pause
        timerVM.isRestart.value = restart
        timerVM.isStop.value = stop
        timerVM.isRunning.value = running
    }

    private fun viewMode(mode: ViewMode) {
        when (mode) {
            ViewMode.START -> {
                binding.ivStart.visibility = View.GONE
                binding.ivPause.visibility = View.VISIBLE
                binding.ivRestart.visibility = View.GONE
                binding.ivStop.visibility = View.VISIBLE
            }

            ViewMode.PAUSE -> {
                binding.ivStart.visibility = View.GONE
                binding.ivPause.visibility = View.GONE
                binding.ivRestart.visibility = View.VISIBLE
                binding.ivStop.visibility = View.VISIBLE
            }

            ViewMode.RESTART -> {
                binding.ivStart.visibility = View.GONE
                binding.ivPause.visibility = View.VISIBLE
                binding.ivRestart.visibility = View.GONE
                binding.ivStop.visibility = View.VISIBLE
            }

            ViewMode.STOP -> {
                binding.ivStart.visibility = View.VISIBLE
                binding.ivPause.visibility = View.GONE
                binding.ivRestart.visibility = View.GONE
                binding.ivStop.visibility = View.GONE
            }
        }
    }

    companion object {
        const val PREF_NAME = "timer_prefs"
        const val PAUSE_TIME = "pauseTime"
        const val KEY_IS_START = "isStart"
        const val KEY_IS_PAUSE = "isPause"
        const val KEY_IS_RESTART = "isRestart"
        const val KEY_IS_STOP = "isStop"
        const val KEY_IS_RUNNING = "isRunning"
        const val TOP_CHALLENGE = "timerChallenge"
        const val TOP_COMPLETE = "timerComplete"
        const val TOP_LEVEL = "timerLevel"
    }
}
