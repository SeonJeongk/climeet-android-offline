package com.climus.climeet.presentation.ui.main.record.session.record

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.climus.climeet.databinding.ItemSessionCompletionRateBinding
import com.climus.climeet.presentation.ui.main.record.model.LevelItemForAvg
import kotlin.math.roundToInt

class CompletionRateAdapter : RecyclerView.Adapter<CompletionRateAdapter.RecordAvgViewHolder>() {
    private var items: List<LevelItemForAvg> = emptyList()

    fun setItems(items: List<LevelItemForAvg>) {
        this.items = items
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ): RecordAvgViewHolder {
        val binding = ItemSessionCompletionRateBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return RecordAvgViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: RecordAvgViewHolder,
        position: Int,
    ) {
        holder.bind(items[position])
    }

    class RecordAvgViewHolder(
        val binding: ItemSessionCompletionRateBinding,
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LevelItemForAvg) {
            val successRate = if (item.attemptCount > 0) item.successCount / item.attemptCount.toFloat() else 0f

            binding.levelName = item.levelName
            binding.levelColor = item.levelColor
            binding.pbAvgComplete.progress = (successRate * 100).roundToInt()
            binding.tvAvgComplete.text = "${item.successCount}/${item.attemptCount}"
        }
    }
}
