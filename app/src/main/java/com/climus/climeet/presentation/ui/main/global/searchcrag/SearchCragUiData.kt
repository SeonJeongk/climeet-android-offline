package com.climus.climeet.presentation.ui.main.global.searchcrag

import com.climus.climeet.data.model.response.SearchGymItem
import com.climus.climeet.presentation.util.Constants.TEST_IMG

data class SearchCragUiData(
    val id: Long = -1,
    val imgUrl: String = "",
    val name: String = "",
    val keyword: String = "",
    val onClickListener: (Long, String, String) -> Unit,
)

fun SearchGymItem.toSearchCragUiData(
    keyword: String,
    onClickListener: (Long, String, String) -> Unit,
) = SearchCragUiData(
    id = id,
    imgUrl = TEST_IMG,
    name = name,
    keyword = keyword,
    onClickListener = onClickListener,
)
