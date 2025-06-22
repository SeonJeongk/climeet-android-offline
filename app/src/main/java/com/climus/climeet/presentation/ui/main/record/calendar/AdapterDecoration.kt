package com.climus.climeet.presentation.ui.main.record.calendar

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class AdapterDecoration : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)
        outRect.bottom = 40
    }
}
