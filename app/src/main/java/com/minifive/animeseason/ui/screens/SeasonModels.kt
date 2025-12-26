package com.minifive.animeseason.ui.screens

data class SeasonAnimeUi(
    val id: String,
    val title: String,
    val typeLabel: String,     // TV / Movie / OVA ...
    val startDateText: String, // 2025-01-05 開播 / 開播日未提供
    val scheduleText: String   // 每週五 23:00 更新 / 更新資訊未提供
)
