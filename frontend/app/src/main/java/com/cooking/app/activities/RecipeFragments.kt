package com.cooking.app.activities

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import com.cooking.app.R
import com.cooking.app.models.*
import com.cooking.app.utils.CookingAlarm
import com.cooking.app.viewmodels.ScreenState
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.ceil

class RecipeFragment : PageFragment() {
    private val state by viewModels<ScreenState>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("Chi tiết món ăn")
        loadOrRetry { render(api.recipe(itemId)) }
    }

    private fun render(recipe: RecipeDetail) {
        body.removeAllViews()
        image(body, recipe.imageUrl, 238, recipe.title)
        title(recipe.title, size = 29)
        val stars = recipe.rating?.let { "★ ${num(it)} (${recipe.reviewCount} đánh giá)  ·  " }.orEmpty()
        text("$stars${recipe.author}", 14, true, R.color.orange).also { body.add(it, 5) }
        val stats = row()
        listOf("${recipe.cookMinutes} phút", recipe.difficulty, "${recipe.servings} khẩu phần",
            recipe.nutrition?.calories?.let { "${num(it)} kcal" } ?: "— kcal").forEachIndexed { index, label ->
            stats.weighted(text(label, 13, true, R.color.forest).apply {
                gravity = Gravity.CENTER
                background = box(R.color.moss, 10)
                setPadding(dp(3), dp(9), dp(3), dp(9))
            }, if (index == 0) 0 else 4)
        }
        body.add(stats, 14)
        recipe.description?.let { paragraph(it) }
        if (state.servings == 0) state.servings = recipe.servings.coerceIn(1, 99)
        val quantity = row()
        quantity.weighted(text("Nguyên liệu", 20, true, R.color.forest))
        val ingredientList = column()
        val servingText = text("", 14, true)
        val checkedIngredients = state.checkedIngredientIds.toMutableSet()
        fun recalc() {
            servingText.text = "  ${state.servings} người  "
            ingredientList.removeAllViews()
            recipe.ingredients.forEach { ingredient ->
                val amount = (ingredient.quantity * state.servings / recipe.servings.coerceAtLeast(1)).coerceAtLeast(0.01)
                ingredientList.add(CheckBox(requireContext()).apply {
                    text = listOf("${num(amount)} ${ingredient.unit} ${ingredient.name}", ingredient.note)
                        .filterNotNull().joinToString(" · ")
                    setTextColor(color(R.color.ink))
                    isChecked = ingredient.ingredientId in checkedIngredients
                    minHeight = dp(44)
                    setOnCheckedChangeListener { _, selected ->
                        if (selected) checkedIngredients.add(ingredient.ingredientId) else checkedIngredients.remove(ingredient.ingredientId)
                        state.checkedIngredientIds = checkedIngredients.toIntArray()
                    }
                })
            }
            if (recipe.ingredients.isEmpty()) empty("Công thức chưa có danh sách nguyên liệu.", ingredientList)
        }
        quantity.addView(link("−") { state.servings = (state.servings - 1).coerceAtLeast(1); recalc() }
            .apply { minimumWidth = 0; contentDescription = "Giảm khẩu phần" })
        quantity.addView(servingText)
        quantity.addView(link("+") { state.servings = (state.servings + 1).coerceAtMost(99); recalc() }
            .apply { minimumWidth = 0; contentDescription = "Tăng khẩu phần" })
        body.add(quantity, 16)
        body.add(action("Thêm tất cả vào giỏ", orange = true) { button ->
            work(button) {
                val group = api.addShopping(recipe.id, ShoppingAdd(state.servings))
                app.showTab(R.id.shopping, group.id)
            }
        }.apply { isEnabled = recipe.ingredients.isNotEmpty() }, 4)
        body.add(ingredientList, 5)
        recalc()
        recipe.nutrition?.let { nutrition ->
            val labels = listOfNotNull(nutrition.calories?.let { "${num(it)} kcal" },
                nutrition.protein?.let { "Đạm ${num(it)} g" }, nutrition.carbs?.let { "Carb ${num(it)} g" },
                nutrition.fat?.let { "Chất béo ${num(it)} g" }, nutrition.fiber?.let { "Chất xơ ${num(it)} g" })
            if (labels.isNotEmpty()) {
                title("Dinh dưỡng mỗi khẩu phần", size = 17)
                paragraph(labels.joinToString("  ·  "))
            }
        }
        val controls = row()
        controls.weighted(action("Bắt đầu nấu") { go(R.id.cook, "id" to recipe.id, "servings" to state.servings) }
            .apply { isEnabled = recipe.steps.isNotEmpty() })
        controls.addView(icon(if (recipe.favorite) R.drawable.ic_heart_fill else R.drawable.ic_heart,
            if (recipe.favorite) "Bỏ yêu thích" else "Yêu thích") { button ->
            work(button) {
                if (recipe.favorite) api.unfavorite(recipe.id) else api.favorite(recipe.id)
                render(api.recipe(recipe.id))
            }
        })
        body.add(controls, 10)
    }
}

class CookFragment : PageFragment() {
    private val state by viewModels<ScreenState>()
    private val alarm by lazy { CookingAlarm(requireContext().applicationContext) }
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private var timerLabel: android.widget.TextView? = null
    private val editing get() = arguments?.getBoolean("editing") == true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header(if (editing) "Sửa công thức" else "Đang nấu")
        loadOrRetry {
            render(api.recipe(itemId))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                if (alarm.finishIfDue()) info("Hết giờ nấu!")
                updateTimerLabel()
                delay(400)
            }
        }
    }

    override fun onDestroyView() {
        timerLabel = null
        super.onDestroyView()
    }

    private fun render(recipe: RecipeDetail) {
        body.removeAllViews()
        if (recipe.steps.isEmpty()) {
            empty("Công thức chưa có bước nấu.")
            return
        }
        state.step = state.step.coerceIn(recipe.steps.indices)
        val step = recipe.steps[state.step]
        val progress = com.google.android.material.progressindicator.LinearProgressIndicator(requireContext()).apply {
            max = recipe.steps.size
            setProgressCompat(state.step + 1, false)
            trackCornerRadius = dp(6)
        }
        text("Bước ${state.step + 1} / ${recipe.steps.size}", 14, true, R.color.orange).also { body.add(it, 4) }
        body.add(progress, 8)
        image(body, step.imageUrl ?: recipe.imageUrl, 210, step.title ?: recipe.title)
        title(step.title ?: "Bước ${step.number}")
        paragraph(step.instruction)
        if (!editing) {
            val recipeServings = recipe.servings.coerceAtLeast(1)
            val servings = arguments?.getInt("servings", recipeServings) ?: recipeServings
            if (recipe.ingredients.isNotEmpty()) showIngredients(recipe.ingredients, servings, recipeServings)
            timerLabel = text("", 15, true, R.color.forest).also { body.add(it, 10) }
            body.add(link("Hẹn giờ") { timerDialog(step, recipe.title) })
            updateTimerLabel()
        }
        step.tip?.takeIf(String::isNotBlank)?.let {
            val tip = column().apply { background = box(R.color.moss, 12); setPadding(dp(12), dp(7), dp(12), dp(10)) }
            title("Mẹo nhỏ", tip, 16)
            paragraph(it, tip)
            body.add(tip, 12)
        }
        val buttons = row()
        buttons.weighted(link("Quay lại") { state.step--; render(recipe) }.apply { isEnabled = state.step > 0 })
        if (editing) buttons.weighted(action("Chỉnh sửa") {
            go(R.id.edit_recipe, "id" to recipe.id, "step" to state.step)
        }, 4)
        val last = state.step == recipe.steps.lastIndex
        buttons.weighted(action(if (!last) "Tiếp theo" else if (editing) "Xong" else "Hoàn thành", orange = true) { button ->
            when {
                !last -> { state.step++; render(recipe) }
                editing -> findNavController().navigateUp()
                else -> work(button) {
                    api.complete(recipe.id)
                    findNavController().navigate(R.id.complete, androidx.core.os.bundleOf("title" to recipe.title),
                        NavOptions.Builder().setPopUpTo(R.id.cook, true).build())
                }
            }
        }, 4)
        body.add(buttons, 12)
    }

    private fun showIngredients(ingredients: List<Ingredient>, servings: Int, originalServings: Int) {
        val checked = state.checkedIngredientIds.toMutableSet()
        val list = column()
        list.visibility = View.GONE
        body.add(link("Nguyên liệu · $servings người") {
            list.visibility = if (list.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }, 8)
        ingredients.forEach { ingredient ->
            val amount = (ingredient.quantity * servings / originalServings).coerceAtLeast(0.01)
            list.add(CheckBox(requireContext()).apply {
                text = "${num(amount)} ${ingredient.unit} ${ingredient.name}"
                isChecked = ingredient.ingredientId in checked
                minHeight = dp(44)
                setOnCheckedChangeListener { _, selected ->
                    if (selected) checked.add(ingredient.ingredientId) else checked.remove(ingredient.ingredientId)
                    state.checkedIngredientIds = checked.toIntArray()
                }
            })
        }
        body.add(list)
    }

    private fun updateTimerLabel() {
        val deadline = alarm.deadline
        timerLabel?.text = if (deadline == 0L) "" else {
            val left = ceil((deadline - System.currentTimeMillis()) / 1000.0).toLong().coerceAtLeast(0)
            "${alarm.label}   ${String.format(Locale.ROOT, "%02d:%02d", left / 60, left % 60)}"
        }
        timerLabel?.visibility = if (deadline == 0L) View.GONE else View.VISIBLE
    }

    private fun timerDialog(step: Step, recipeTitle: String) {
        val pane = column().apply { setPadding(dp(18), dp(8), dp(18), dp(14)) }
        val seconds = step.timerSeconds ?: 300
        val minutes = field(pane, "Phút", (seconds / 60).toString(), NUMBER, limit = 4)
        val remaining = field(pane, "Giây", (seconds % 60).toString(), NUMBER, limit = 2)
        if (!alarm.canScheduleExactly && Build.VERSION.SDK_INT >= 31) {
            empty("Cho phép báo giờ chính xác để chuông hoạt động đúng lúc khi app chạy nền.", pane)
            pane.add(link("Mở cài đặt báo giờ") {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${requireContext().packageName}")))
            })
        }
        val old = alarm.deadline != 0L
        val builder = MaterialAlertDialogBuilder(requireContext()).setTitle(if (old) "Thay hẹn giờ đang chạy?" else "Hẹn giờ nấu")
            .setView(pane).setNegativeButton("Đóng", null).setPositiveButton("Bắt đầu", null)
        if (old) builder.setNeutralButton("Hủy hẹn giờ") { _, _ -> alarm.cancel(); updateTimerLabel() }
        val dialog = builder.show()
        dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            val m = minutes.value().toIntOrNull() ?: -1
            val s = remaining.value().toIntOrNull() ?: -1
            if (m !in 0..1440 || s !in 0..59 || m * 60 + s !in 1..86400) {
                (remaining.parent.parent as? com.google.android.material.textfield.TextInputLayout)?.error =
                    "Nhập thời lượng từ 1 giây đến 24 giờ"
                return@setOnClickListener
            }
            alarm.start(m * 60 + s, step.title ?: recipeTitle)
            updateTimerLabel()
            dialog.dismiss()
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

class CompleteFragment : PageFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("Hoàn thành món")
        title("Chúc mừng bạn!", size = 32)
        paragraph("Bạn đã hoàn thành ${arguments?.getString("title").orEmpty()} và lưu món vào lịch sử nấu.")
        body.add(action("Về trang khám phá") { app.showTab(R.id.discover) }, 16)
        body.add(link("Xem mục yêu thích và đã thực hiện") { app.showTab(R.id.favorites) }, 6)
    }
}

class EditRecipeFragment : PageFragment() {
    private data class IngredientForm(val option: IngredientOption, val quantity: android.widget.EditText,
                                      val unit: android.widget.EditText, val note: android.widget.EditText)
    private data class StepForm(val title: android.widget.EditText, val instruction: android.widget.EditText,
                                val tip: android.widget.EditText, val timer: android.widget.EditText, val image: String?)
    private val ingredients = linkedMapOf<LinearLayout, IngredientForm>()
    private val steps = linkedMapOf<LinearLayout, StepForm>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("Chỉnh sửa công thức")
        loadOrRetry { showForm(api.recipe(itemId), api.ingredients()) }
    }

    private fun showForm(recipe: RecipeDetail, options: List<IngredientOption>) {
        body.removeAllViews()
        ingredients.clear()
        steps.clear()
        val titleField = field(body, "Tên món", recipe.title, limit = 200)
        val description = field(body, "Mô tả", recipe.description.orEmpty(), lines = 3, limit = 4000)
        val numbers = row()
        val servingColumn = column()
        val timeColumn = column()
        numbers.weighted(servingColumn)
        numbers.weighted(timeColumn, 8)
        val servings = field(servingColumn, "Khẩu phần", recipe.servings.toString(), NUMBER)
        val time = field(timeColumn, "Phút nấu", recipe.cookMinutes.toString(), NUMBER)
        body.add(numbers)
        var difficulty = recipe.difficulty
        fun chooseDifficulty(button: View) {
            val values = arrayOf("Dễ", "Trung bình", "Khó")
            MaterialAlertDialogBuilder(requireContext()).setTitle("Độ khó").setItems(values) { _, pos ->
                difficulty = values[pos]
                (button as android.widget.TextView).text = "Độ khó: $difficulty"
            }.show()
        }
        body.add(link("Độ khó: $difficulty", ::chooseDifficulty), 8)
        title("Nguyên liệu")
        val items = column()
        body.add(items)
        recipe.ingredients.forEach {
            addIngredient(items, IngredientOption(it.ingredientId, it.name, it.unit), it.quantity, it.unit, it.note.orEmpty())
        }
        body.add(link("Thêm nguyên liệu") {
            val available = options.filter { option -> ingredients.values.none { it.option.id == option.id } }
            if (available.isEmpty()) info("Không còn nguyên liệu để thêm.")
            else MaterialAlertDialogBuilder(requireContext()).setTitle("Chọn nguyên liệu")
                .setItems(available.map { it.name }.toTypedArray()) { _, position -> addIngredient(items, available[position]) }.show()
        }, 10)
        title("Các bước thực hiện")
        val stepList = column()
        body.add(stepList)
        recipe.steps.forEach { addStep(stepList, it) }
        body.add(link("Thêm bước nấu") { addStep(stepList, null) }, 10)
        val error = lineError()
        body.add(action("Lưu công thức", orange = true) { button ->
            val parsed = ingredients.values.map {
                IngredientEdit(it.option.id, it.quantity.value().replace(',', '.').toDoubleOrNull() ?: 0.0,
                    it.unit.value(), it.note.value())
            }
            if (parsed.any { !it.quantity.isFinite() || it.quantity <= 0 }) {
                error.showError("Nhập số lượng lớn hơn 0 cho từng nguyên liệu")
                return@action
            }
            val edited = steps.values.map {
                StepEdit(it.title.value(), it.instruction.value(), it.tip.value(), it.image, it.timer.value().toIntOrNull())
            }
            error.showError("")
            hideKeyboard()
            work(button, error = { error.showError(it) }) {
                api.editRecipe(itemId, RecipeEdit(titleField.value(), description.value(), servings.value().toIntOrNull(),
                    time.value().toIntOrNull(), difficulty, parsed, edited))
                findNavController().navigateUp()
            }
        }, 12)
        val selected = arguments?.getInt("step", -1) ?: -1
        steps.keys.elementAtOrNull(selected)?.let { panel ->
            view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll)?.post {
                val offset = android.graphics.Rect()
                panel.getDrawingRect(offset)
                body.offsetDescendantRectToMyCoords(panel, offset)
                view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll)?.smoothScrollTo(0, offset.top)
            }
        }
    }

    private fun addIngredient(parent: LinearLayout, option: IngredientOption, amount: Double? = null,
                              unit: String = option.unit, note: String = "") {
        val panel = panel()
        title(option.name, panel, 17)
        val quantityField = field(panel, "Số lượng", amount?.let(::num).orEmpty(), NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val unitField = field(panel, "Đơn vị", unit, limit = 30)
        val noteField = field(panel, "Ghi chú sơ chế", note, limit = 500)
        ingredients[panel] = IngredientForm(option, quantityField, unitField, noteField)
        panel.add(link("Xóa nguyên liệu") { ingredients.remove(panel); parent.removeView(panel) })
        parent.add(panel, 8)
    }

    private fun addStep(parent: LinearLayout, step: Step?) {
        val panel = panel()
        val titleField = field(panel, "Tên bước", step?.title.orEmpty(), limit = 200)
        val instruction = field(panel, "Hướng dẫn", step?.instruction.orEmpty(), lines = 3, limit = 4000)
        val tip = field(panel, "Mẹo nhỏ", step?.tip.orEmpty(), limit = 100)
        val timer = field(panel, "Hẹn giờ (giây)", step?.timerSeconds?.toString().orEmpty(), NUMBER, limit = 5)
        steps[panel] = StepForm(titleField, instruction, tip, timer, step?.imageUrl)
        panel.add(link("Xóa bước nấu") { steps.remove(panel); parent.removeView(panel) })
        parent.add(panel, 8)
    }

    private fun panel() = column().apply {
        setPadding(dp(12), dp(6), dp(12), dp(8))
        background = box(R.color.paper, 14)
    }
}
