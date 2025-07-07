package com.climus.climeet.presentation.ui.main.home.popularroutes

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.climus.climeet.R
import com.climus.climeet.data.model.response.BestRouteDetailInfoResponse
import com.climus.climeet.databinding.FragmentPopularRoutesBinding
import com.climus.climeet.presentation.base.BaseFragment
import com.climus.climeet.presentation.ui.main.home.popularroutes.adapter.PopularRoutesAllRVadapter
import com.climus.climeet.presentation.ui.toSearchProfile
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class PopularRoutesFragment : BaseFragment<FragmentPopularRoutesBinding>(R.layout.fragment_popular_routes) {
    private val viewModel: PopularRoutesViewModel by viewModels()
    private var recyclerRoute: List<BestRouteDetailInfoResponse> = emptyList()

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.getRouteRankingOrderSelectionCount()
        initEventObserve()
        setupOnClickListener()
    }

    private fun initEventObserve() {
        repeatOnStarted {
            viewModel?.let { vm ->
                vm.uiState.collect { uiState ->

                    uiState.routeList?.let { routeList ->
                        Log.d("RouteList", routeList.toString())
                        recyclerRoute = routeList
                        setupPopularRoutesList()
                    }
                }
            }
        }
    }

    private fun setupOnClickListener() {
        binding.icPopularRoutesBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.icPopularRoutesSearch.setOnClickListener {
            findNavController().toSearchProfile()
        }
    }

    private fun setupPopularRoutesList() {
        val popularRoutesAllRVAdapter = PopularRoutesAllRVadapter(recyclerRoute)
        setupRecyclerView(binding.rvPopularRoutes, popularRoutesAllRVAdapter)
    }

    private fun setupRecyclerView(
        recyclerView: RecyclerView,
        adapter: RecyclerView.Adapter<*>,
    ) {
        recyclerView.adapter = adapter
        val spanCount = 3
        recyclerView.layoutManager = GridLayoutManager(requireActivity(), spanCount)
    }
}
