package com.climus.climeet.presentation.ui.main.record.timer.stopwatch.selectcrag

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.climus.climeet.R
import com.climus.climeet.databinding.FragmentTimerSelectCragBottomSheetBinding
import com.climus.climeet.presentation.ui.main.record.timer.setrecord.SetTimerClimbingRecordViewModel
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class TimerCragSelectBottomSheetFragment : BottomSheetDialogFragment() {
    private val viewModel: TimerCragSelectBottomSheetViewModel by activityViewModels()
    private val routeVM: SetTimerClimbingRecordViewModel by activityViewModels()
    private lateinit var binding: FragmentTimerSelectCragBottomSheetBinding

    private var cragSearchAdapter: TimerCragSelectRVAdapter? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        binding = FragmentTimerSelectCragBottomSheetBinding.inflate(inflater, container, false)
        binding.vm = viewModel

        binding.layoutBottom.background = context?.let {
            ContextCompat.getDrawable(
                it,
                R.drawable.rect_grey9fill_nostroke_top20radius,
            )
        }

        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            setupRatio(bottomSheetDialog)
        }
        return dialog
    }

    private fun setupRatio(bottomSheetDialog: BottomSheetDialog) {
        val bottomSheet =
            bottomSheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as View
        BottomSheetBehavior.from(bottomSheet).state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerview()
        initStateObserve()
        initEventObserve()
        closeBottomSheet()
    }

    private fun initRecyclerview() {
        cragSearchAdapter = TimerCragSelectRVAdapter(viewModel, routeVM)
        binding.rvSearchCrag.layoutManager = LinearLayoutManager(context)
        binding.rvSearchCrag.adapter = cragSearchAdapter
    }

    private fun initStateObserve() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { uiState ->
                cragSearchAdapter?.setList(uiState.searchList, viewModel.keyword.value)

                // XML의 visibility가 반영되지 않아 수집한 상태로 직접 갱신합니다.
                if (uiState.progressState) {
                    binding.pbLoading.visibility = View.VISIBLE
                } else {
                    binding.pbLoading.visibility = View.INVISIBLE
                }

                if (uiState.emptyResultState) {
                    binding.layoutSearchNone.visibility = View.VISIBLE
                } else {
                    binding.layoutSearchNone.visibility = View.INVISIBLE
                }

                if (uiState.emptyTextState) {
                    binding.etCragName.setText("")
                }
            }
        }
    }

    private fun initEventObserve() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.event.collect {
                when (it) {
                    is CragSelectEvent.NavigateToBack -> findNavController().navigateUp()
                    is CragSelectEvent.ShowToastMessage -> showToastMessage(it.msg)
                }
            }
        }
    }

    private fun closeBottomSheet() {
        viewModel.selectedCrag.observe(
            viewLifecycleOwner,
            Observer { cragName ->
                if (cragName != null) {
                    dismiss()
                }
            },
        )
    }

    fun showToastMessage(message: String) {
        val toast = Toast.makeText(activity, message, Toast.LENGTH_SHORT)
        toast.show()
    }
}
