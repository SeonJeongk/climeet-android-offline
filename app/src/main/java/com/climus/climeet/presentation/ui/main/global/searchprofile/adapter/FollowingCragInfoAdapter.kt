package com.climus.climeet.presentation.ui.main.global.searchprofile.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.climus.climeet.data.model.response.RouteSimpleInfo
import com.climus.climeet.data.model.response.UserHomeGymDetailResponse
import com.climus.climeet.databinding.ItemHomegymRouteBinding
import com.climus.climeet.presentation.bindingadapters.setGymProfileImage

class FollowingCragInfoAdapter(
    private var followingCrags: List<UserHomeGymDetailResponse>,
    private val itemClickAction: (Long) -> Unit,
) : RecyclerView.Adapter<FollowingCragInfoAdapter.FollowingCragInfoViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): FollowingCragInfoViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemHomegymRouteBinding.inflate(layoutInflater, parent, false)
        return FollowingCragInfoViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: FollowingCragInfoViewHolder,
        position: Int,
    ) {
        holder.bind(followingCrags[position])
        holder.itemView.setOnClickListener {
            itemClickAction(followingCrags[position].gymId)
        }
    }

    override fun getItemCount(): Int = followingCrags.size

    fun updateGyms(newFollowingCrags: List<UserHomeGymDetailResponse>) {
        followingCrags = newFollowingCrags
        notifyDataSetChanged()
    }

    class FollowingCragInfoViewHolder(
        private val binding: ItemHomegymRouteBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        private lateinit var routeAdapter: RouteRVAdapter

        fun bind(gym: UserHomeGymDetailResponse) {
            binding.tvHomgymName.text = gym.gymName
            binding.ivCragProfileArea.setGymProfileImage(gym.gymProfileUrl)

            setupRouteRecyclerView(gym.routeSimpleInfos, gym.gymName)
        }

        private fun setupRouteRecyclerView(
            routes: List<RouteSimpleInfo>,
            gymName: String,
        ) {
            routeAdapter = RouteRVAdapter(routes, gymName)
            binding.rvSearchPopularRoutes.apply {
                layoutManager = LinearLayoutManager(binding.root.context, RecyclerView.HORIZONTAL, false)
                adapter = routeAdapter
                setRecycledViewPool(RecyclerView.RecycledViewPool())
            }
        }
    }
}
