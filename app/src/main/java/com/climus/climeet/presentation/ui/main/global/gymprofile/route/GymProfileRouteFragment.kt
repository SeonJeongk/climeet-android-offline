package com.climus.climeet.presentation.ui.main.global.gymprofile.route

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.navigation.fragment.findNavController
import com.climus.climeet.BuildConfig
import com.climus.climeet.R
import com.climus.climeet.data.repository.route.Freshness
import com.climus.climeet.databinding.FragmentGymProfileRouteBinding
import com.climus.climeet.presentation.base.BaseFragment
import com.climus.climeet.presentation.customview.selectdate.SelectDateBottomSheet
import com.climus.climeet.presentation.customview.selectdate.SelectDateBottomSheetViewModel
import com.climus.climeet.presentation.ui.main.global.gymprofile.GymProfileData
import com.climus.climeet.presentation.ui.main.global.gymprofile.GymProfileViewModel
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.GymLevelAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.RouteImageAdapter
import com.climus.climeet.presentation.ui.main.global.selectsector.adapter.SectorNameAdapter
import com.climus.climeet.presentation.ui.main.shorts.adapter.ShortsThumbnailAdapter
import com.climus.climeet.presentation.ui.main.shorts.player.ShortsOption
import com.climus.climeet.presentation.ui.main.shorts.player.ShortsPlayerEvent
import com.climus.climeet.presentation.ui.main.shorts.player.ShortsPlayerViewModel
import com.climus.climeet.presentation.ui.toShortsPlayer
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GymProfileRouteFragment : BaseFragment<FragmentGymProfileRouteBinding>(R.layout.fragment_gym_profile_route) {
    private val parentViewModel: GymProfileViewModel by activityViewModels()
    private val sharedViewModel: ShortsPlayerViewModel by activityViewModels()
    private val dateViewModel: SelectDateBottomSheetViewModel by activityViewModels()
    private val viewModel: GymProfileRouteViewModel by viewModels()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        binding.svm = sharedViewModel
        binding.vm = viewModel

        setRouteTab()
        setRecyclerView()
        initEventObserve()
        initShortsEventObserve()
        addOnScrollListener()

        observeRouteState()
    }

    private fun setRouteTab() {
        parentViewModel.gymId.observe(
            viewLifecycleOwner,
            Observer { id ->

                sharedViewModel.setCurFilter(id)
                sharedViewModel.getShorts(ShortsOption.NEW_SORT)

                viewModel.setCragInfo(id, parentViewModel.uiState.value.gymName)
            },
        )
    }

    private fun observeRouteState() {
        binding.btnRetryRoutes.setOnClickListener { viewModel.retry() }
        repeatOnStarted {
            viewModel.uiState.collect { state ->
                val loadState = state.loadState
                binding.progressRoutes.visibility =
                    if (loadState is RouteLoadState.Loading || state.isRefreshing) View.VISIBLE else View.GONE
                binding.btnRetryRoutes.visibility =
                    if (loadState is RouteLoadState.RetryableError) View.VISIBLE else View.GONE
                val message = when (loadState) {
                    RouteLoadState.Empty -> getString(R.string.gym_routes_empty)
                    is RouteLoadState.RetryableError -> loadState.error.msg
                    is RouteLoadState.TerminalError -> loadState.error.msg
                    else -> ""
                }
                binding.tvRouteStatus.text = message
                binding.tvRouteStatus.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
                binding.rvSectorImage.contentDescription =
                    if (BuildConfig.MEASUREMENT_MARKERS_ENABLED) {
                        listOf(
                            "catalogComplete=true",
                            "catalogLabels=${state.routeList.joinToString(",") { it.gymLevelName }}",
                            "routeIds=${state.routeList.joinToString(",") { it.routeId.toString() }}",
                            "cacheFreshness=${state.cacheFreshness.name}",
                            "staleAgeMs=${state.staleAgeMs()}",
                        ).joinToString(";")
                    } else {
                        null
                    }
            }
        }
    }

    private fun GymProfileSelectSectorUiState.staleAgeMs(): String =
        if (cacheFreshness == Freshness.STALE && cacheReceivedAtEpochMs != null) {
            (System.currentTimeMillis() - cacheReceivedAtEpochMs).coerceAtLeast(0L).toString()
        } else {
            ""
        }

    private fun addOnScrollListener() {
        binding.layoutScrollview.setOnScrollChangeListener { v, _, scrollY, _, _ ->
            if (scrollY == binding.layoutScrollview.getChildAt(0).measuredHeight - v.measuredHeight) {
                sharedViewModel.getShorts(ShortsOption.NEXT_PAGE)
            }
        }
    }

    private fun setRecyclerView() {
        binding.rvShortsThumbnail.adapter = ShortsThumbnailAdapter()
        binding.rvSectorName.adapter = SectorNameAdapter()
        binding.rvSectorLevel.adapter = GymLevelAdapter()
        binding.rvSectorImage.adapter = RouteImageAdapter()
        binding.rvSectorName.itemAnimator = null
        binding.rvSectorLevel.itemAnimator = null
        binding.rvSectorImage.itemAnimator = null
    }

    private fun initEventObserve() {
        repeatOnStarted {
            viewModel.event.collect { event ->
                when (event) {
                    is GymProfileRouteEvent.ShowDatePicker -> {
                        SelectDateBottomSheet(
                            requireContext(),
                            dateViewModel,
                            GymProfileData.selectedDate,
                            GymProfileData::setSelectedDate,
                        ) { date ->
                            viewModel.setSelectedDate(date)
                        }.show()
                    }

                    is GymProfileRouteEvent.DeleteFilter -> {
                        sharedViewModel.gymProfileDelete.value = true
                        sharedViewModel.deleteFilter()
                    }

                    is GymProfileRouteEvent.ApplyFilter -> {
                        sharedViewModel.applyFilter(event.filter)
                    }

                    is GymProfileRouteEvent.ShowToastMessage -> {
                        showToastMessage(event.msg)
                    }
                }
            }
        }
    }

    private fun initShortsEventObserve() {
        repeatOnStarted {
            sharedViewModel.event.collect {
                when (it) {
                    is ShortsPlayerEvent.ShowToastMessage -> showToastMessage(it.msg)
                    is ShortsPlayerEvent.NavigateToShortsPlayer ->
                        findNavController().toShortsPlayer(
                            it.shortsId,
                            it.position,
                        )
                    else -> {}
                }
            }
        }
    }
}
