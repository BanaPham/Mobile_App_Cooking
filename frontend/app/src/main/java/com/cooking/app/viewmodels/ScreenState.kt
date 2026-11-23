package com.cooking.app.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.cooking.app.models.Filters

class ScreenState(private val saved: SavedStateHandle) : ViewModel() {
    var query: String
        get() = saved["query"] ?: ""
        set(value) { saved["query"] = value }
    var filters: Filters
        get() = Filters(saved["category"], saved["difficulty"], saved["min"], saved["max"])
        set(value) {
            saved["category"] = value.category
            saved["difficulty"] = value.difficulty
            saved["min"] = value.min
            saved["max"] = value.max
        }
    var scope: String
        get() = saved["scope"] ?: "favorites"
        set(value) { saved["scope"] = value }
    var step: Int
        get() = saved["step"] ?: 0
        set(value) { saved["step"] = value }

    var checkedIngredientIds: IntArray
        get() = saved["checkedIngredients"] ?: intArrayOf()
        set(value) { saved["checkedIngredients"] = value }

    var servings: Int
        get() = saved["servingsChoice"] ?: 0
        set(value) { saved["servingsChoice"] = value }
}
