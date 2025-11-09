package com.climus.climeet.presentation.ui.main.record.session.stopwatch

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
import com.climus.climeet.databinding.FragmentStopwatchBinding
import com.climus.climeet.presentation.base.BaseFragment
import com.climus.climeet.presentation.customview.DeleteDialog
import com.climus.climeet.presentation.customview.NoticePopup
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.GymLevelAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.RouteImageAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.SectorNameAdapter
import com.climus.climeet.presentation.ui.main.record.model.LevelItemForAvg
import com.climus.climeet.presentation.ui.main.record.session.gym.GymSelectBottomSheetFragment
import com.climus.climeet.presentation.ui.main.record.session.gym.GymSelectViewModel
import com.climus.climeet.presentation.ui.main.record.session.record.SessionRecordViewModel
import com.climus.climeet.presentation.ui.main.record.session.record.adapter.CompletionRateAdapter
import com.climus.climeet.presentation.ui.main.record.session.record.adapter.SessionRouteAdapter
import com.climus.climeet.presentation.ui.main.record.session.record.submission.SessionSubmissionEffect
import com.climus.climeet.presentation.ui.main.record.session.record.submission.SessionSubmissionState
import com.climus.climeet.service.StopwatchService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

enum class StopwatchMode {
    START,
    PAUSE,
    RESTART,
    STOP,
}

@AndroidEntryPoint
class StopwatchFragment : BaseFragment<FragmentStopwatchBinding>(R.layout.fragment_stopwatch) {
    @Inject
    lateinit var repository: MainRepository

    private val stopwatchViewModel: StopwatchViewModel by activityViewModels()
    private val sessionRecordViewModel: SessionRecordViewModel by activityViewModels()
    private val gymSelectViewModel: GymSelectViewModel by activityViewModels()

    private lateinit var routeItemAdapter: SessionRouteAdapter

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.stopwatchViewModel = stopwatchViewModel
        binding.sessionRecordViewModel = sessionRecordViewModel

        routeItemAdapter = SessionRouteAdapter(sessionRecordViewModel)

        stopwatchViewModel.registerReceiver(requireContext())
        sharedPreferences = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

        checkService()
        observeStopwatchState()
        initClickListener()
        observeStopwatchTime()
        pauseObserve()
        initRouteObserve()
        observeSubmission()

        observeSelectedGym()
        setAverage()
        setAverageByLevel()
        setRouteRecyclerView()
    }

    private fun checkService() {
        val serviceState = StopwatchService.hasStartedInProcess

        if (serviceState.value == null) {
            viewMode(StopwatchMode.STOP)
            stopwatchViewModel.isStart.value = false
            stopwatchViewModel.isPaused.value = false
            stopwatchViewModel.isRestart.value = false
            stopwatchViewModel.isStop.value = true
            stopwatchViewModel.isRunning.value = false
            stopwatchViewModel.pauseTime.value = 0L
            stopwatchViewModel.pauseState.value = ""
            updateStatePref()
            sessionRecordViewModel.restoreDraft()
        } else {
            setViewModel()
        }
    }

    private fun observeSelectedGym() {
        gymSelectViewModel.selectedGym.observe(
            viewLifecycleOwner,
            Observer { gym ->
                if (gym != null) {
                    val gymId = gym.id
                    val gymName = gym.name
                    val date = LocalDate.now().toString()
                    sharedPreferences.edit {
                        putString("gymName", gymName)
                        putLong("gymId", gymId)
                    }

                    binding.tvTitle.text = gymName
                    val recordData = ClimbingRecordData(
                        id = 1,
                        gymId = gymId,
                        gymName = gymName,
                        date = date,
                        time = "",
                        avgDifficulty = 0,
                    )

                    sessionRecordViewModel.saveDraft(recordData)
                }
            },
        )
    }

    private fun setAverage() {
        sessionRecordViewModel.avgCompleteRate.observe(
            viewLifecycleOwner,
            Observer { rate ->
                val progress = (rate * 100).roundToInt()
                binding.pbAvgComplete.progress = progress
                binding.tvAvgComplete.text = "$progress%"
            },
        )
    }

    private fun setAverageByLevel() {
        sessionRecordViewModel.routeRecordsChanged.observe(
            viewLifecycleOwner,
            Observer {
                var items: List<LevelItemForAvg>
                val levelAdapter = CompletionRateAdapter()
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
        sessionRecordViewModel.itemsLiveData.observe(viewLifecycleOwner) { items ->
            routeItemAdapter.submitList(items)
        }
    }

    override fun onStart() {
        super.onStart()
        val serviceState = StopwatchService.hasStartedInProcess

        if (serviceState.value != null) {
            val id = sharedPreferences.getLong("gymId", 0)
            val name = sharedPreferences.getString("gymName", getString(R.string.session_gym_select_prompt))
            if (id > 0L && name != null) {
                sessionRecordViewModel.restoreSelectedGym(id, name)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val restoredGymId = sharedPreferences.getLong("gymId", 0L)
        if (stopwatchViewModel.isStop.value == true &&
            sessionRecordViewModel.submissionState.value !is SessionSubmissionState.Preserved &&
            restoredGymId <= 0L
        ) {
            binding.tvTitle.text = getString(R.string.session_gym_select_prompt)
            sessionRecordViewModel.totalRoute.value = "--"
            sessionRecordViewModel.totalComplete.value = "--"
            sessionRecordViewModel.avgLevel.value = "--"
        } else {
            binding.tvTitle.text = sharedPreferences.getString("gymName", getString(R.string.session_gym_select_prompt))
        }

        setViewModel()
        observeStopwatchTime()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopwatchViewModel.unregisterReceiver(requireContext())
    }

    private fun setViewModel() {
        stopwatchViewModel.isStart.value = sharedPreferences.getBoolean(KEY_IS_START, false)
        stopwatchViewModel.isPaused.value = sharedPreferences.getBoolean(KEY_IS_PAUSE, false)
        stopwatchViewModel.isRestart.value = sharedPreferences.getBoolean(KEY_IS_RESTART, false)
        stopwatchViewModel.isStop.value = sharedPreferences.getBoolean(KEY_IS_STOP, true)
        stopwatchViewModel.isRunning.value = sharedPreferences.getBoolean(KEY_IS_RUNNING, true)
        stopwatchViewModel.pauseTime.value = sharedPreferences.getLong(PAUSE_TIME, 0L)
    }

    private fun updateStatePref() {
        sharedPreferences.edit {
            stopwatchViewModel.isStart.value?.let { putBoolean(KEY_IS_START, it) }
            stopwatchViewModel.isPaused.value?.let { putBoolean(KEY_IS_PAUSE, it) }
            stopwatchViewModel.isRestart.value?.let { putBoolean(KEY_IS_RESTART, it) }
            stopwatchViewModel.isStop.value?.let { putBoolean(KEY_IS_STOP, it) }
            stopwatchViewModel.isRunning.value?.let { putBoolean(KEY_IS_RUNNING, it) }
        }
    }

    private fun observeStopwatchTime() {
        if (sessionRecordViewModel.submissionState.value is SessionSubmissionState.Preserved) {
            sessionRecordViewModel.restoredDuration.value?.let { binding.tvTime.text = it }
            return
        }
        stopwatchViewModel.pauseTimeFormat.observe(viewLifecycleOwner) { pauseTime ->
            val isEditableDraft = sessionRecordViewModel.submissionState.value == SessionSubmissionState.Draft
            if (isEditableDraft && pauseTime != null && pauseTime != "00:00") {
                sharedPreferences.edit { putString("pauseTimeFormat", pauseTime) }
                binding.tvTime.text = pauseTime
            }
        }

        if (stopwatchViewModel.isPaused.value == true) {
            val time = sharedPreferences.getString("pauseTimeFormat", "00:00")
            binding.tvTime.text = time
        } else {
            stopwatchViewModel.timeFormat.observe(viewLifecycleOwner) { timeFormat ->
                if (sessionRecordViewModel.submissionState.value == SessionSubmissionState.Draft) {
                    binding.tvTime.text = timeFormat
                }
            }
        }
    }

    private fun pauseObserve() {
        stopwatchViewModel.pauseState.observe(viewLifecycleOwner) { state ->
            if (state == "yes") {
                viewMode(StopwatchMode.PAUSE)
                updateViewModel(
                    start = false,
                    pause = true,
                    restart = false,
                    stop = false,
                    running = false,
                )
            } else if (state == "no") {
                viewMode(StopwatchMode.RESTART)
                updateViewModel(
                    start = false,
                    pause = false,
                    restart = true,
                    stop = false,
                    running = true,
                )
            }
        }

        sessionRecordViewModel.draftDetailsRestored.observe(viewLifecycleOwner) { reset ->
            if (reset) {
                val hasRestoredElapsedTime =
                    sessionRecordViewModel.restoredElapsedTimeMillis.value != null
                val canShowDetails = stopwatchViewModel.isRunning.value != true &&
                    (stopwatchViewModel.isPaused.value == true || hasRestoredElapsedTime)
                if (canShowDetails) {
                    showRestoredDraftDetails()
                }
            }
        }
    }

    private fun observeStopwatchState() {
        stopwatchViewModel.isStart.observe(viewLifecycleOwner) { isStart ->
            if (isStart) {
                viewMode(StopwatchMode.START)
                hidePausedDraftDetails()
            }
        }
        stopwatchViewModel.isPaused.observe(viewLifecycleOwner) { isPaused ->
            if (isPaused) {
                viewMode(StopwatchMode.PAUSE)
                if (sessionRecordViewModel.itemsLiveData.value?.isEmpty() == false) {
                    showRestoredDraftDetails()
                }
            }
        }
        stopwatchViewModel.isRestart.observe(viewLifecycleOwner) { isRestart ->
            if (isRestart) {
                viewMode(StopwatchMode.RESTART)
                observeStopwatchTime()
                hidePausedDraftDetails()
            }
        }
        stopwatchViewModel.isStop.observe(viewLifecycleOwner) { isStop ->
            if (isStop) {
                viewMode(StopwatchMode.STOP)
                hidePausedDraftDetails()
            }
        }
    }

    private fun initClickListener() {
        binding.layoutSelectGym.setOnClickListener {
            when {
                sessionRecordViewModel.isCatalogRetryAvailable.value -> sessionRecordViewModel.retryCatalog()
                sessionRecordViewModel.submissionState.value is SessionSubmissionState.Preserved ->
                    showDiscardDraftDialog()
                stopwatchViewModel.isStop.value == false -> {
                    NoticePopup.make(it, "운동중에는 암장을 바꿀 수 없어요!").show()
                }

                else -> showGymSelectBottomSheet()
            }
        }
        binding.ivStart.setOnClickListener {
            if (sessionRecordViewModel.submissionState.value is SessionSubmissionState.Preserved) {
                showDiscardDraftDialog()
            } else if (binding.tvTitle.text == getString(R.string.session_gym_select_prompt)) {
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
            val endTime = if (stopwatchViewModel.isPaused.value == true) {
                stopwatchViewModel.pauseTime.value
            } else {
                stopwatchViewModel.time.value
            } ?: return@setOnLongClickListener true
            sharedPreferences.edit(commit = true) { putLong("stopTime", endTime) }
            stopServiceForSubmission()
            sessionRecordViewModel.submitRecord(endTime)
            true
        }
    }

    private fun observeSubmission() {
        repeatOnStarted {
            sessionRecordViewModel.submissionState.collect { state ->
                binding.ivStop.isEnabled = state == SessionSubmissionState.Draft
                if (state == SessionSubmissionState.Submitting || state is SessionSubmissionState.Preserved) {
                    binding.tvTitle.text = sessionRecordViewModel.gymName.ifBlank {
                        sharedPreferences.getString("gymName", getString(R.string.session_gym_select_prompt))
                    }
                    sessionRecordViewModel.restoredDuration.value?.let { binding.tvTime.text = it }
                    sessionRecordViewModel.restoredElapsedTimeMillis.value?.let(stopwatchViewModel::restoreElapsedTime)
                    if (sessionRecordViewModel.items.value.isNotEmpty()) showRestoredDraftDetails()
                }
            }
        }
        repeatOnStarted {
            sessionRecordViewModel.restoredDuration.collect { duration ->
                val state = sessionRecordViewModel.submissionState.value
                if (duration != null &&
                    state != SessionSubmissionState.Succeeded
                ) {
                    binding.tvTitle.text = sessionRecordViewModel.gymName
                    binding.tvTime.text = duration
                    sessionRecordViewModel.restoredElapsedTimeMillis.value?.let(stopwatchViewModel::restoreElapsedTime)
                }
            }
        }
        repeatOnStarted {
            sessionRecordViewModel.submissionEffect.collect { effect ->
                when (effect) {
                    is SessionSubmissionEffect.ShowMessage -> showToastMessage(effect.message)
                    SessionSubmissionEffect.DraftDiscarded -> {
                        binding.tvTime.text = "00:00"
                        sharedPreferences.edit {
                            putLong(PAUSE_TIME, 0L)
                            putString("pauseTimeFormat", "00:00")
                        }
                        showGymSelectBottomSheet()
                    }
                    SessionSubmissionEffect.NavigateToComplete -> completeSubmission()
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
            if (shouldDiscard) sessionRecordViewModel.discardPreservedDraft()
        }.show()
    }

    private fun stopServiceForSubmission() {
        if (StopwatchService.hasStartedInProcess.value == true) {
            context?.stopService(Intent(context, StopwatchService::class.java))
        }
        updateViewModel(start = false, pause = false, restart = false, stop = true, running = false)
        updateStatePref()
    }

    private fun completeSubmission() {
        stopStopwatch()
        gymSelectViewModel.resetItem()
        sessionRecordViewModel.resetAtStop()
        sessionRecordViewModel.acknowledgeSubmissionSuccess()
        requireActivity().supportFragmentManager.beginTransaction()
            .add(R.id.main_container, SessionCompleteFragment())
            .addToBackStack(null)
            .commit()
    }

    private fun startStopwatch() {
        viewMode(StopwatchMode.START)
        hidePausedDraftDetails()
        val initialElapsedTime = sessionRecordViewModel.restoredElapsedTimeMillis.value ?: 0L
        stopwatchViewModel.restoreElapsedTime(initialElapsedTime)
        val intent = Intent(context, StopwatchService::class.java).apply {
            putExtra("command", "START")
            putExtra(StopwatchService.EXTRA_INITIAL_ELAPSED_TIME, initialElapsedTime)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(intent)
        } else {
            context?.startService(intent)
        }

        updateViewModel(start = true, pause = false, restart = false, stop = false, running = true)
    }

    private fun showRestoredDraftDetails() {
        routeItemAdapter.submitList(sessionRecordViewModel.items.value.toList())
        binding.layoutAvgComplete.visibility = View.VISIBLE
        binding.tvTimeTitle.visibility = View.VISIBLE
        binding.layoutRouteRecord.visibility = View.VISIBLE
        sessionRecordViewModel.isAvgToggleOn.value = false
        sessionRecordViewModel.isRouteToggleOn.value = true
        binding.rvRouteRecord.post(binding.rvRouteRecord::requestLayout)
    }

    private fun hidePausedDraftDetails() {
        binding.layoutAvgComplete.visibility = View.GONE
        binding.tvTimeTitle.visibility = View.GONE
        binding.layoutRouteRecord.visibility = View.GONE
    }

    private fun pauseStopwatch() {
        viewMode(StopwatchMode.PAUSE)
        val intent = Intent(context, StopwatchService::class.java).apply {
            putExtra("command", "PAUSE")
        }
        context?.startService(intent)

        stopwatchViewModel.pauseTime.value = sharedPreferences.getLong("pauseTime", 0L)

        updateViewModel(start = false, pause = true, restart = false, stop = false, running = false)
    }

    private fun restartStopwatch() {
        viewMode(StopwatchMode.RESTART)
        hidePausedDraftDetails()
        val intent = Intent(context, StopwatchService::class.java).apply {
            putExtra("command", "RESTART")
        }
        context?.startService(intent)

        updateViewModel(start = false, pause = false, restart = true, stop = false, running = true)
    }

    private fun stopStopwatch() {
        viewMode(StopwatchMode.STOP)
        binding.tvTime.text = "00:00"

        if (StopwatchService.hasStartedInProcess.value == true) {
            val intent = Intent(context, StopwatchService::class.java)
            context?.stopService(intent)
        }

        updateViewModel(start = false, pause = false, restart = false, stop = true, running = false)

        sharedPreferences.edit { putString(PREF_TOTAL_ATTEMPTS, "--") }
        sharedPreferences.edit { putString(PREF_TOTAL_COMPLETIONS, "--") }
        sharedPreferences.edit { putString(PREF_AVERAGE_LEVEL, "--") }
        sharedPreferences.edit {
            putLong(PAUSE_TIME, 0L)
            putString("pauseTimeFormat", "00:00")
        }
        binding.tvTitle.text = getString(R.string.session_gym_select_prompt)

        sessionRecordViewModel.totalRoute.value = "--"
        sessionRecordViewModel.totalComplete.value = "--"
        sessionRecordViewModel.avgLevel.value = "--"

        sessionRecordViewModel.prepareGymChange()
        sessionRecordViewModel.isGymSelected.value = false
    }

    private fun showGymSelectBottomSheet() {
        gymSelectViewModel.resetItem()
        sessionRecordViewModel.prepareGymChange()
        val bottomSheetFragment = GymSelectBottomSheetFragment()
        bottomSheetFragment.show(parentFragmentManager, "gymSelectBottomSheet")
    }

    private fun updateViewModel(
        start: Boolean,
        pause: Boolean,
        restart: Boolean,
        stop: Boolean,
        running: Boolean,
    ) {
        stopwatchViewModel.isStart.value = start
        stopwatchViewModel.isPaused.value = pause
        stopwatchViewModel.isRestart.value = restart
        stopwatchViewModel.isStop.value = stop
        stopwatchViewModel.isRunning.value = running
    }

    private fun viewMode(mode: StopwatchMode) {
        when (mode) {
            StopwatchMode.START -> {
                binding.ivStart.visibility = View.GONE
                binding.ivPause.visibility = View.VISIBLE
                binding.ivRestart.visibility = View.GONE
                binding.ivStop.visibility = View.VISIBLE
            }

            StopwatchMode.PAUSE -> {
                binding.ivStart.visibility = View.GONE
                binding.ivPause.visibility = View.GONE
                binding.ivRestart.visibility = View.VISIBLE
                binding.ivStop.visibility = View.VISIBLE
            }

            StopwatchMode.RESTART -> {
                binding.ivStart.visibility = View.GONE
                binding.ivPause.visibility = View.VISIBLE
                binding.ivRestart.visibility = View.GONE
                binding.ivStop.visibility = View.VISIBLE
            }

            StopwatchMode.STOP -> {
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
        const val PREF_TOTAL_ATTEMPTS = "timerChallenge"
        const val PREF_TOTAL_COMPLETIONS = "timerComplete"
        const val PREF_AVERAGE_LEVEL = "timerLevel"
    }
}
