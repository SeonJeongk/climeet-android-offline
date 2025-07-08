package com.climus.climeet.presentation.bindingadapters

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import com.climus.climeet.R
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultBindingTest {
    @Test
    fun repeatedKeyword_bindSearchResult_highlightsFirstMatch() {
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
            assertEquals(view.context.getColor(R.color.cm_main), spans.single().foregroundColor)
            assertEquals(Spanned.SPAN_EXCLUSIVE_INCLUSIVE, text.getSpanFlags(spans.single()))
        }
    }

    @Test
    fun previousHighlight_bindSearchResult_replacesHighlight() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val view = TextView(instrumentation.targetContext)
            for ((keyword, result) in listOf("앞" to "앞부분", "끝" to "맨끝", "전체" to "전체")) {
                bindSearchResult(view, keyword, result)
                val text = view.text as Spanned
                val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
                assertEquals(result, text.toString())
                assertEquals(1, spans.size)
                assertEquals(result.indexOf(keyword), text.getSpanStart(spans.single()))
                assertEquals(result.indexOf(keyword) + keyword.length, text.getSpanEnd(spans.single()))
            }
        }
    }

    @Test
    fun unmatchedOrMissingSearch_bindSearchResult_preservesExistingText() {
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
