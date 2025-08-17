package com.climus.climeet.presentation.ui.main.home.recycler.homegym

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.climus.climeet.data.model.response.UserHomeGymSimpleResponse
import com.climus.climeet.databinding.ItemHomeGymBinding
import com.climus.climeet.presentation.bindingadapters.setGymProfileImage
import com.climus.climeet.presentation.ui.main.global.toProfileHomeGymUiData
import kotlin.math.min

class HomeGymRVAdapter(
    private val homeGymList: List<UserHomeGymSimpleResponse>,
    private val itemClickAction: (Long) -> Unit,
) : RecyclerView.Adapter<HomeGymRVAdapter.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): ViewHolder {
        val binding: ItemHomeGymBinding = ItemHomeGymBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int,
    ) {
        holder.bind(homeGymList[position])

        holder.binding.root.setOnClickListener {
            itemClickAction(homeGymList[position].gymId)
        }
    }

    override fun getItemCount(): Int {
        // 최대 10개의 아이템으로 제한
        return min(homeGymList.size, 10)
    }

    inner class ViewHolder(
        val binding: ItemHomeGymBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(homeGym: UserHomeGymSimpleResponse) {
            binding.item = homeGym.toProfileHomeGymUiData(itemClickAction)
            binding.cragProfileArea.setGymProfileImage(homeGym.gymProfileUrl)
        }
    }
}
