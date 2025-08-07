package com.climus.climeet.presentation.ui.main.record.session.gym

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.climus.climeet.databinding.ItemSessionGymSearchBinding
import com.climus.climeet.presentation.ui.main.global.searchcrag.SearchCragUiData
import com.climus.climeet.presentation.ui.main.record.session.record.SessionRecordViewModel

class GymSelectAdapter(
    private val viewModel: GymSelectViewModel,
    private val routeVM: SessionRecordViewModel,
) : RecyclerView.Adapter<GymSelectAdapter.GymSelectViewHolder>() {
    private var searchList: List<SearchCragUiData> = emptyList()
    private var keyword: String = ""

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): GymSelectViewHolder =
        GymSelectViewHolder(
            ItemSessionGymSearchBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            ),
        )

    override fun onBindViewHolder(
        holder: GymSelectViewHolder,
        position: Int,
    ) {
        val gym = searchList[position]
        holder.bind(gym, keyword)

        holder.binding.btnSelect.setOnClickListener {
            viewModel.selectItem(gym)
            routeVM.isGymSelected.value = true
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

    class GymSelectViewHolder(
        val binding: ItemSessionGymSearchBinding,
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
