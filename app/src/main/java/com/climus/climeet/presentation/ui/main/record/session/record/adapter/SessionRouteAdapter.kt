package com.climus.climeet.presentation.ui.main.record.session.record.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.climus.climeet.databinding.ItemSessionRouteRecordBinding
import com.climus.climeet.presentation.ui.main.global.selectsector.model.RouteUiData
import com.climus.climeet.presentation.ui.main.record.session.record.SessionRecordViewModel

class SessionRouteAdapter(
    private val viewModel: SessionRecordViewModel,
) : ListAdapter<RouteUiData, SessionRouteViewHolder>(diffCallback) {
    companion object {
        private val diffCallback = object : DiffUtil.ItemCallback<RouteUiData>() {
            override fun areItemsTheSame(
                oldItem: RouteUiData,
                newItem: RouteUiData,
            ): Boolean = oldItem.routeId == newItem.routeId

            override fun areContentsTheSame(
                oldItem: RouteUiData,
                newItem: RouteUiData,
            ): Boolean = oldItem == newItem
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): SessionRouteViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemSessionRouteRecordBinding.inflate(inflater, parent, false)
        return SessionRouteViewHolder(binding, viewModel)
    }

    override fun onBindViewHolder(
        holder: SessionRouteViewHolder,
        position: Int,
    ) {
        val context = holder.itemView.context
        holder.bind(getItem(position), context)
    }
}

class SessionRouteViewHolder(
    val binding: ItemSessionRouteRecordBinding,
    private val viewModel: SessionRecordViewModel,
) : RecyclerView.ViewHolder(binding.root) {
    fun bind(
        item: RouteUiData,
        context: Context,
    ) {
        binding.item = item

        binding.root.setOnClickListener {
            viewModel.selectRoute(item)
            viewModel.setChallengeNum(item.challengeNum)
        }

        binding.btnIncrease.setOnClickListener {
            viewModel.selectRoute(item)
            viewModel.itemIncrease(item.routeId, 1)
        }

        binding.btnDecrease.setOnClickListener {
            viewModel.selectRoute(item)
            viewModel.itemDecrease(item.routeId)
        }

        binding.ivDelete.setOnClickListener {
            viewModel.showDeleteDialog(context, item.routeId)
        }

        binding.ivClear.setOnClickListener {
            viewModel.selectRoute(item)
            viewModel.setBtnState(item.routeId)
        }
    }
}
