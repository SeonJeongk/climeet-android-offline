package com.climus.climeet.presentation.bindingadapters

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultBindingTest {
    @Test
    fun highlightsFirstMatch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = TextView(instrumentation.targetContext)
            bindSearchResult(view, "클밋", "안녕 클밋 클밋")
            val text = view.text as Spanned
            val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            assertEquals("안녕 클밋 클밋", text.toString())
            assertEquals(1, spans.size)
            assertEquals(3, text.getSpanStart(spans.single()))
            assertEquals(5, text.getSpanEnd(spans.single()))
        }
    }

    @Test
    fun preservesExistingTextWhenSearchCannotMatch() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = TextView(instrumentation.targetContext)
            view.text = "기존 표시"
            for ((keyword, result) in listOf(null to "클밋", " " to "클밋", "클밋" to null, "없음" to "클밋")) {
                bindSearchResult(view, keyword, result)
                assertEquals("기존 표시", view.text.toString())
            }
        }
    }
}
