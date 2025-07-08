package com.climus.climeet.presentation.ui.main.global.searchcrag

import com.climus.climeet.data.model.response.SearchGymItem

data class SearchCragUiData(
    val id: Long = -1,
    val imgUrl: String = "",
    val name: String = "",
    val keyword: String = "",
    val onClickListener: (Long, String) -> Unit,
)

fun SearchGymItem.toSearchCragUiData(
    keyword: String,
    onClickListener: (Long, String) -> Unit,
) = SearchCragUiData(
    id = id,
    imgUrl = "",
    name = name,
    keyword = keyword,
    onClickListener = onClickListener,
)
