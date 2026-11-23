package com.cooking.app.activities

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.cooking.app.R
import com.cooking.app.models.Group
import com.cooking.app.models.ItemInput
import com.cooking.app.models.Purchased
import com.cooking.app.models.ShoppingItem
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ShoppingFragment : PageFragment() {
    private lateinit var groupsArea: LinearLayout
    private var groups = listOf<Group>()
    private var selectedGroup: Long? = null
    private var query = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header(getString(R.string.app_name), back = false, brand = true)
        title("Danh sách mua sắm", size = 26)
        selectedGroup = arguments?.getLong("group")?.takeIf { it > 0 }
        val search = field(body, "Tìm món hoặc nguyên liệu", query, limit = 120)
        groupsArea = column()
        body.add(groupsArea)
        search.doAfterTextChanged { value -> query = value?.toString().orEmpty(); showGroups() }
        work(error = {
            empty(it, groupsArea)
            groupsArea.add(link("Tải lại") { work { refresh() } }, 8)
        }) { refresh() }
    }

    private suspend fun refresh() {
        groups = api.shopping()
        showGroups()
    }

    private fun showGroups() {
        groupsArea.removeAllViews()
        val shown = groups.filter { it.title.contains(query, true) || it.items.any { item -> item.name.contains(query, true) } }
        if (shown.isEmpty()) {
            empty(if (groups.isEmpty()) "Mở công thức và chọn Thêm tất cả vào giỏ để lên danh sách đi chợ."
                else "Không tìm thấy nguyên liệu phù hợp.", groupsArea)
            if (groups.isEmpty()) groupsArea.add(action("Khám phá món ăn") { app.showTab(R.id.discover) }, 12)
            return
        }
        shown.forEach { group ->
            val card = column().apply {
                setPadding(dp(12), dp(10), dp(12), dp(12))
                background = box(if (selectedGroup == group.id) R.color.moss else R.color.paper, 16)
            }
            val info = row()
            val image = android.widget.ImageView(requireContext()).apply {
                background = box(R.color.moss, 8)
                clipToOutline = true
                contentDescription = group.title
            }
            loadPhoto(image, group.imageUrl)
            info.addView(image, LinearLayout.LayoutParams(dp(53), dp(53)))
            info.weighted(column().also { title(group.title, it, 18); paragraph("${group.servings} khẩu phần", it) }, 10)
            card.add(info)
            val selected = if (group.title.contains(query, true)) group.items
                else group.items.filter { it.name.contains(query, true) }
            selected.forEach { item -> showItem(card, group.id, item) }
            if (group.items.isEmpty()) empty("Nhóm chưa có nguyên liệu.", card)
            card.add(link("+ Thêm nguyên liệu") { itemDialog(group.id, null) }, 8)
            groupsArea.add(card, 14)
        }
        val all = groups.sumOf { it.items.size }
        val done = groups.sumOf { group -> group.items.count { it.purchased } }
        empty("Đã mua $done / $all nguyên liệu", groupsArea)
    }

    private fun showItem(card: LinearLayout, group: Long, item: ShoppingItem) {
        val line = row()
        val check = CheckBox(requireContext()).apply {
            text = "${item.name}\n${num(item.quantity)} ${item.unit}"
            setTextColor(color(R.color.ink))
            isChecked = item.purchased
            minHeight = dp(44)
        }
        check.setOnClickListener {
            val next = check.isChecked
            work(check, progress = false, error = { message -> check.isChecked = item.purchased; info(message) }) {
                api.purchased(item.id, Purchased(next))
                refresh()
            }
        }
        line.weighted(check)
        line.addView(icon(R.drawable.ic_edit, "Sửa ${item.name}") { itemDialog(group, item) })
        line.addView(link("Xóa") { control ->
            MaterialAlertDialogBuilder(requireContext()).setTitle("Xóa ${item.name}?")
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Xóa") { _, _ ->
                    work(control, progress = false) { api.deleteItem(item.id); refresh() }
                }.show()
        }.apply { minimumWidth = 0 })
        card.add(line, 3)
    }

    private fun itemDialog(group: Long, item: ShoppingItem?) {
        val form = column().apply { setPadding(dp(18), dp(4), dp(18), dp(14)) }
        val name = field(form, "Tên nguyên liệu", item?.name.orEmpty(), limit = 200)
        val quantity = field(form, "Số lượng", item?.quantity?.let(::num).orEmpty(), NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        val unit = field(form, "Đơn vị", item?.unit.orEmpty(), limit = 30)
        val error = text("", 13, false, R.color.error).also { form.add(it, 6) }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (item == null) "Thêm nguyên liệu" else "Sửa nguyên liệu")
            .setView(form).setNegativeButton("Hủy", null).setPositiveButton("Lưu", null).show()
        val button = dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
        button.setOnClickListener {
            error.text = ""
            val amount = quantity.value().replace(',', '.').toDoubleOrNull()
            val data = ItemInput(name.value(), amount, unit.value())
            if (amount == null || !amount.isFinite() || amount <= 0.0) {
                error.text = "Số lượng phải lớn hơn 0"
            } else work(button, progress = false, error = { error.text = it }) {
                if (item == null) api.addItem(group, data) else api.editItem(item.id, data)
                dialog.dismiss()
                refresh()
            }
        }
    }
}
