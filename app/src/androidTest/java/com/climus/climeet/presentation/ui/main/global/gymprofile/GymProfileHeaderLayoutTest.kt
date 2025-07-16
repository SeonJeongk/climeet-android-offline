package com.climus.climeet.presentation.ui.main.global.gymprofile

import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.platform.app.InstrumentationRegistry
import com.climus.climeet.R
import com.climus.climeet.databinding.FragmentGymProfileBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.roundToInt

class GymProfileHeaderLayoutTest {
    @Test
    fun shortAndLongNames_measureHeader_keepsBadgeAfterEllipsizedName() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = ContextThemeWrapper(instrumentation.targetContext, R.style.Theme_Climeet)
            val density = context.resources.displayMetrics.density
            for (widthDp in listOf(320, 360, 412)) {
                for (text in listOf("클밋", "Synthetic Climbing Gym With A Very Long Name")) {
                    val binding = FragmentGymProfileBinding.inflate(LayoutInflater.from(context))
                    binding.executePendingBindings()
                    binding.tvGymName.text = text
                    val width = (widthDp * density).roundToInt()
                    val height = (800 * density).roundToInt()
                    binding.root.measure(
                        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
                    )
                    binding.root.layout(0, 0, width, height)

                    val name = binding.tvGymName
                    val badge = binding.ivGymBadge
                    val parent = name.parent as View
                    val case = "$widthDp dp: $text"
                    assertEquals(case, 1, name.layout.lineCount)
                    assertEquals(case, text != "클밋", name.layout.getEllipsisCount(0) > 0)
                    assertEquals(case, 0, name.left)
                    assertEquals(case, name.right, badge.left)
                    assertTrue(case, badge.width > 0 && badge.right <= parent.width)
                    if (text == "클밋") {
                        val textWidth = ceil(name.paint.measureText(text)).toInt()
                        assertTrue(case, name.width - name.paddingLeft - name.paddingRight <= textWidth + 1)
                    }
                }
            }
        }
    }
}
