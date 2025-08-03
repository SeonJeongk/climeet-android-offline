package com.climus.climeet.presentation.ui.main.record.timer.stopwatch.selectcrag

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.climus.climeet.databinding.ItemCragSearchTimerBinding
import com.climus.climeet.presentation.ui.main.global.searchcrag.SearchCragUiData
import com.climus.climeet.presentation.ui.main.record.timer.setrecord.SetTimerClimbingRecordViewModel

class TimerCragSelectRVAdapter(
    private val viewModel: TimerCragSelectBottomSheetViewModel,
    private val routeVM: SetTimerClimbingRecordViewModel,
) : RecyclerView.Adapter<TimerCragSelectRVAdapter.CragSelectViewHolder>() {
    private var searchList: List<SearchCragUiData> = emptyList()
    private var keyword: String = ""

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): CragSelectViewHolder =
        CragSelectViewHolder(
            ItemCragSearchTimerBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(
        holder: CragSelectViewHolder,
        position: Int,
    ) {
        val cragData = searchList[position]
        holder.bind(cragData, keyword)

        holder.binding.btnSelect.setOnClickListener {
            viewModel.selectItem(cragData)
            routeVM.isSelectedCrag.value = true
        }
    }

    override fun getItemCount(): Int = searchList.size

    @SuppressLint("NotifyDataSetChanged")
    fun setList(
        list: List<SearchCragUiData>,
        keyword: String,
    ) {
        searchList = list
        this.keyword = keyword
        notifyDataSetChanged()
    }

    class CragSelectViewHolder(
        val binding: ItemCragSearchTimerBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(
            data: SearchCragUiData,
            keyword: String,
        ) {
            binding.keyword = keyword
            binding.data = data

            binding.btnSelect.setOnClickListener {
                data.onClickListener(data.id, data.name)
            }
        }
    }
}
