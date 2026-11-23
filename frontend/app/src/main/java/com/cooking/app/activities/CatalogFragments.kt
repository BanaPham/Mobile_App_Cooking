package com.cooking.app.activities

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.RatingBar
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputEditText
import java.text.Normalizer
import java.util.Locale
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cooking.app.R
import com.cooking.app.adapters.RecipeAdapter
import com.cooking.app.models.Category
import com.cooking.app.models.Filters
import com.cooking.app.models.Recipe
import com.cooking.app.models.Review
import com.cooking.app.viewmodels.ScreenState
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

abstract class CatalogFragment : PageFragment() {
    protected val state by viewModels<ScreenState>()
    protected var categories = listOf<Category>()

    protected fun recipes(parent: LinearLayout, data: List<Recipe>, display: String, mode: String, refresh: () -> Unit) {
        val list = RecyclerView(requireContext()).apply {
            isNestedScrollingEnabled = false
            layoutManager = when (display) {
                "grid" -> GridLayoutManager(requireContext(), 2)
                "hero", "strip" -> LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
                else -> LinearLayoutManager(requireContext())
            }
            adapter = RecipeAdapter(data, mode, display,
                { go(R.id.recipe, "id" to it.id) },
                { recipe, button ->
                    work(button, progress = false) {
                        if (recipe.favorite) api.unfavorite(recipe.id) else api.favorite(recipe.id)
                        refresh()
                    }
                },
                { recipe, button ->
                    if (mode == "cooked") showReview(recipe, button, refresh)
                    else go(R.id.cook, "id" to (recipe.copyId ?: recipe.id), "editing" to true)
                })
        }
        parent.add(list, 8)
    }

    private fun showReview(recipe: Recipe, button: View, refresh: () -> Unit) {
        work(button) {
            val current = api.recipe(recipe.id).myReview
            val form = column().apply { setPadding(dp(18), dp(8), dp(18), dp(16)) }
            val stars = RatingBar(requireContext()).apply {
                numStars = 5
                stepSize = 1f
                rating = (current?.rating ?: 0).toFloat()
                contentDescription = "Điểm đánh giá từ 1 đến 5 sao"
                layoutParams = LinearLayout.LayoutParams(-2, -2)
            }
            form.addView(stars)
            val reviewError = text("", 14, tint = R.color.error).apply { isVisible = false }
            form.add(reviewError, 6)
            val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Đánh giá ${recipe.title}")
                .setView(form).setNegativeButton("Hủy", null).setPositiveButton("Gửi", null).show()
            val submit = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
            submit.setOnClickListener {
                val rating = stars.rating.toInt()
                if (rating == 0) {
                    reviewError.showError("Chọn ít nhất 1 sao")
                    return@setOnClickListener
                }
                reviewError.showError("")
                work(submit, progress = false, error = { reviewError.showError(it) }) {
                    api.review(recipe.id, Review(rating))
                    dialog.dismiss()
                    info("Đã lưu đánh giá")
                    refresh()
                }
            }
        }
    }

    protected fun filterDialog(selected: Filters, apply: (Filters) -> Unit) {
        val dialog = BottomSheetDialog(requireContext())
        val content = column().apply { setPadding(dp(18), dp(12), dp(18), dp(18)) }
        val scroll = android.widget.ScrollView(requireContext()).apply { addView(content) }
        dialog.setContentView(scroll)
        title("Bộ lọc", content, 24)
        var category = selected.category
        var difficulty = selected.difficulty
        var times = selected.min to selected.max
        title("Loại món ăn", content, 16)
        choices(content, listOf(null to "Tất cả") + categories.map { it.id to it.name }, category) { category = it }
        title("Thời gian nấu", content, 16)
        choices(content, listOf((null to null) to "Tất cả", (null to 14) to "Dưới 15 phút",
            (15 to 30) to "15–30 phút", (31 to 45) to "31–45 phút", (46 to null) to "Trên 45 phút"), times) { times = it }
        title("Độ khó", content, 16)
        choices(content, listOf(null to "Tất cả", "Dễ" to "Dễ", "Trung bình" to "Trung bình", "Khó" to "Khó"),
            difficulty) { difficulty = it }
        content.add(action("Áp dụng", orange = true) {
            dialog.dismiss()
            apply(Filters(category, difficulty, times.first, times.second))
        }, 12)
        dialog.show()
    }

    private fun <T> choices(parent: LinearLayout, options: List<Pair<T, String>>, selected: T, update: (T) -> Unit) {
        val group = ChipGroup(requireContext()).apply {
            isSingleSelection = true
            isSelectionRequired = true
        }
        for ((value, label) in options) {
            group.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                text = label
                isCheckable = true
                isChecked = value == selected
                setOnCheckedChangeListener { _, checked -> if (checked) update(value) }
            })
        }
        parent.add(group, 4)
    }
}

class DiscoverFragment : CatalogFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.isVisible = false
        loadOrRetry { refresh() }
    }

    private suspend fun refresh() {
        categories = api.categories()
        val featured = repo.recipes(sort = "featured")
        val newest = repo.recipes()
        val suggested = repo.recipes(sort = "recommended")
        body.removeAllViews()
        val search = row()
        search.weighted(link("Tìm kiếm công thức, nguyên liệu…") { go(R.id.search, "focus" to true) })
        search.addView(icon(R.drawable.ic_filter, "Mở bộ lọc") {
            filterDialog(Filters()) { go(R.id.search, "category" to it.category, "difficulty" to it.difficulty,
                "min" to it.min, "max" to it.max) }
        })
        body.add(search, 2)
        title("Danh mục", size = 19)
        val group = ChipGroup(requireContext())
        for (category in categories) {
            group.addView(Chip(requireContext()).apply {
                val emoji = when (category.slug) {
                    "mon-sang" -> "🍳"; "mon-chinh" -> "🍛"; "mon-canh" -> "🍲"
                    "mon-an-vat" -> "🥜"; "do-uong" -> "🍹"; "mon-chay" -> "🥬"; else -> "🍮"
                }
                text = "$emoji  ${category.name}"
                setOnClickListener { go(R.id.search, "category" to category.id) }
            })
        }
        body.add(group, 2)
        section("Món ăn nổi bật", featured, "hero")
        section("Món ăn mới", newest, "strip")
        section("Gợi ý cho bạn", suggested, "strip")
    }

    private fun section(label: String, data: List<Recipe>, display: String) {
        title(label, size = 20)
        if (data.isEmpty()) empty("Chưa có công thức.")
        else recipes(body, data.take(10), display, "catalog") { work { refresh() } }
    }
}

class SearchFragment : CatalogFragment() {
    private lateinit var results: LinearLayout
    private lateinit var suggestions: ChipGroup
    private lateinit var searchInput: TextInputEditText
    private var ingredientNames: List<String>? = null
    private var loadJob: Job? = null
    private var page = 0
    private var sort = "new"
    private val current = mutableListOf<Recipe>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("Tìm công thức")
        if (savedInstanceState == null && state.filters == Filters()) {
            val args = arguments
            state.filters = Filters(args?.getInt("category")?.takeIf { it > 0 }, args?.getString("difficulty"),
                args?.getInt("min", -1)?.takeIf { it >= 0 }, args?.getInt("max", -1)?.takeIf { it >= 0 })
        }
        val searchRow = row()
        val searchColumn = column()
        searchRow.weighted(searchColumn)
        val search = field(searchColumn, "Tên món hoặc nguyên liệu", state.query, limit = 120)
        searchInput = search
        search.imeOptions = EditorInfo.IME_ACTION_SEARCH
        searchRow.addView(icon(R.drawable.ic_filter, "Mở bộ lọc") { button ->
            work(button, progress = false) {
                if (categories.isEmpty()) categories = api.categories()
                filterDialog(state.filters) { state.filters = it; request() }
            }
        })
        body.add(searchRow)
        suggestions = ChipGroup(requireContext())
        body.add(suggestions)
        val sortRow = row()
        sortRow.weighted(link("Mới nhất") { sort = "new"; request() })
        sortRow.weighted(link("Nấu nhanh") { sort = "fast"; request() }, 5)
        sortRow.weighted(link("Đánh giá cao") { sort = "popular"; request() }, 5)
        body.add(sortRow, 8)
        results = column()
        body.add(results)
        search.doAfterTextChanged {
            state.query = it?.toString().orEmpty()
            request(debounce = true)
        }
        search.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) false else {
                hideKeyboard()
                request()
                true
            }
        }
        if (arguments?.getBoolean("focus") == true && savedInstanceState == null && state.query.isBlank()) {
            search.requestFocus()
            search.post {
                if (search.isAttachedToWindow) (requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as android.view.inputmethod.InputMethodManager).showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
        }
        request()
    }

    private fun fold(value: String): String =
        Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace('đ', 'd')

    private fun request(debounce: Boolean = false, append: Boolean = false) {
        loadJob?.cancel()
        loadJob = work(progress = !debounce, error = { message ->
            results.removeAllViews()
            empty(message, results)
            results.add(link("Thử lại") { request() })
        }) {
            if (debounce) delay(300)
            val query = state.query.trim()
            val target = if (append) page + 1 else 0
            if (categories.isEmpty()) categories = api.categories()
            val found = repo.recipes(query = query, filters = state.filters, sort = sort, page = target)
            if (!append) current.clear()
            page = target
            current.addAll(found)
            suggestions.removeAllViews()
            if (query.isNotBlank() && !append) {
                if (ingredientNames == null) ingredientNames = api.ingredients().map { it.name }
                val prefix = fold(query)
                val ingredients = ingredientNames.orEmpty().filter { fold(it).startsWith(prefix) && fold(it) != prefix }
                (ingredients.take(2) + found.map { it.title })
                    .distinct().filter { fold(it) != prefix }.take(3).forEach { label ->
                        suggestions.addView(Chip(requireContext()).apply {
                            text = label
                            setOnClickListener {
                                searchInput.setText(label)
                                searchInput.setSelection(label.length)
                                hideKeyboard()
                            }
                        })
                    }
            }
            results.removeAllViews()
            val active = state.filters
            if (active != Filters()) {
                val caption = listOfNotNull(categories.find { it.id == active.category }?.name, active.difficulty,
                    active.min?.let { "Từ $it phút" }, active.max?.let { "Đến $it phút" }).joinToString("  ·  ")
                results.add(link(if (caption.isBlank()) "Đang lọc · Xóa bộ lọc" else "$caption · Xóa lọc") {
                    state.filters = Filters()
                    request()
                })
            }
            if (current.isEmpty()) empty("Không tìm thấy món phù hợp.", results)
            else recipes(results, current.toList(), "list", "catalog") { request() }
            if (found.size == 24) results.add(link("Xem thêm") { request(append = true) })
        }
    }
}

class FavoritesFragment : CatalogFragment() {
    private lateinit var results: LinearLayout
    private lateinit var tabs: LinearLayout
    private var loadJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header(getString(R.string.app_name), back = false, brand = true)
        tabs = row()
        body.add(tabs)
        val search = row()
        val input = column()
        search.weighted(input)
        val query = field(input, "Tìm trong công thức", state.query, limit = 120)
        search.addView(icon(R.drawable.ic_filter, "Mở bộ lọc") { button ->
            work(button, progress = false) {
                if (categories.isEmpty()) categories = api.categories()
                filterDialog(state.filters) { state.filters = it; refresh() }
            }
        })
        body.add(search, 4)
        results = column()
        body.add(results)
        query.doAfterTextChanged { state.query = it?.toString().orEmpty(); refresh(debounce = true) }
        refresh()
    }

    private fun refresh(debounce: Boolean = false, page: Int = 0, previous: List<Recipe> = emptyList()) {
        tabs.removeAllViews()
        listOf("favorites" to "Món yêu thích", "cooked" to "Đã thực hiện").forEachIndexed { index, (scope, label) ->
            val button = if (state.scope == scope) action(label, orange = true) {} else link(label) { state.scope = scope; refresh() }
            tabs.weighted(button, if (index == 0) 0 else 7)
        }
        loadJob?.cancel()
        loadJob = work(progress = !debounce, error = { message ->
            results.removeAllViews()
            empty(message, results)
            results.add(link("Thử lại") { refresh() })
        }) {
            if (debounce) delay(300)
            val scope = state.scope
            val found = repo.recipes(scope, state.query, state.filters, page = page)
            val complete = previous + found
            results.removeAllViews()
            if (state.filters != Filters()) results.add(link("Xóa bộ lọc") { state.filters = Filters(); refresh() })
            if (complete.isEmpty()) empty(if (scope == "favorites") "Bạn chưa lưu món nào." else "Bạn chưa hoàn thành món nào.", results)
            else recipes(results, complete, "grid", scope) { refresh() }
            if (found.size == 24) results.add(link("Xem thêm") { refresh(page = page + 1, previous = complete) })
        }
    }
}
