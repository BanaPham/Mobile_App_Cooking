package com.cooking.app.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doAfterTextChanged
import com.cooking.app.R
import com.cooking.app.models.*
import com.cooking.app.utils.photoPart
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

abstract class CommunityPage : PageFragment() {
    protected fun panel() = column().apply {
        background = box(R.color.paper, 17)
        setPadding(dp(12), dp(8), dp(12), dp(12))
    }

    protected fun author(target: LinearLayout, name: String, url: String?, date: String?) {
        val row = row()
        val portrait = ImageView(requireContext()).apply {
            background = box(R.color.moss, 19)
            clipToOutline = true
            contentDescription = "Ảnh đại diện của $name"
        }
        loadPhoto(portrait, url)
        row.addView(portrait, LinearLayout.LayoutParams(dp(38), dp(38)))
        val label = column()
        label.add(text(name, 15, true, R.color.forest))
        date?.take(10)?.let { label.add(text(it, 12, false, R.color.muted), 2) }
        row.weighted(label, 8)
        target.add(row, 3)
    }

    protected fun postActions(target: LinearLayout, post: Post, detail: Boolean, onChange: () -> Unit) {
        val actions = row()
        actions.weighted(link("${if (post.liked) "♥" else "♡"} ${post.likes}") { button ->
            work(button, progress = false) {
                api.likePost(post.id, Liked(!post.liked))
                onChange()
            }
        }.apply { contentDescription = if (post.liked) "Bỏ thích bài viết" else "Thích bài viết" })
        actions.weighted(link("${post.commentCount} bình luận") {
            if (!detail) go(R.id.post, "id" to post.id)
            else view?.findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll)?.fullScroll(View.FOCUS_DOWN)
        }, 5)
        if (detail) actions.weighted(link("Chia sẻ") {
            val share = Intent(Intent.ACTION_SEND).setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "${post.title}\n\n${post.content}")
            startActivity(Intent.createChooser(share, "Chia sẻ bài viết"))
        }, 5)
        target.add(actions, 8)
    }
}

class CommunityFragment : CommunityPage() {
    private var query = ""
    private var type: String? = null
    private var pending: Job? = null
    private lateinit var list: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header(getString(R.string.app_name), back = false, brand = true)
        title("Cộng đồng", size = 28)
        view.findViewById<View>(R.id.create_post_fab).apply {
            visibility = View.VISIBLE
            setOnClickListener { go(R.id.create_post) }
        }
        body.setPadding(body.paddingLeft, body.paddingTop, body.paddingRight, dp(82))
        val search = field(body, "Tìm bài viết", query, limit = 120)
        val tabs = row()
        val types = listOf(null to "Tất cả", "TIPS" to "Mẹo bếp", "QA" to "Hỏi đáp")
        types.forEachIndexed { index, (code, label) ->
            tabs.weighted(link(label) {
                type = code
                refresh()
            }, if (index == 0) 0 else 4)
        }
        body.add(tabs, 7)
        list = column()
        body.add(list)
        search.doAfterTextChanged { text -> query = text?.toString().orEmpty(); refresh(debounce = true) }
        refresh()
    }

    private fun refresh(debounce: Boolean = false, page: Int = 0, previous: List<Post> = emptyList()) {
        pending?.cancel()
        pending = work(progress = !debounce, error = { error ->
            if (page == 0) list.removeAllViews()
            empty(error, list)
            list.add(link("Thử lại") { refresh() }, 8)
        }) {
            if (debounce) delay(300)
            val result = api.posts(type, query.ifBlank { null }, page)
            val entries = previous + result
            list.removeAllViews()
            if (entries.isEmpty()) empty("Chưa có bài viết phù hợp.", list)
            entries.forEach { post ->
                val card = panel()
                author(card, post.author, post.avatarUrl, post.createdAt)
                text(if (post.type == "QA") "Hỏi đáp" else "Mẹo bếp", 12, true, R.color.orange).also { card.add(it, 8) }
                title(post.title, card, 18).setOnClickListener { go(R.id.post, "id" to post.id) }
                paragraph(post.content, card).apply {
                    maxLines = 3
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setOnClickListener { go(R.id.post, "id" to post.id) }
                }
                post.imageUrl?.let { image(card, it, 180, post.title).setOnClickListener { go(R.id.post, "id" to post.id) } }
                postActions(card, post, false) { refresh() }
                list.add(card, 12)
            }
            if (result.size == 24) list.add(link("Xem thêm") { refresh(page = page + 1, previous = entries) })
        }
    }
}

class PostFragment : CommunityPage() {
    private var reply: Comment? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("Bài viết")
        loadOrRetry { showPost(api.post(itemId)) }
    }

    private fun showPost(post: Post) {
        body.removeAllViews()
        val card = panel()
        author(card, post.author, post.avatarUrl, post.createdAt)
        title(post.title, card, 25)
        post.images.orEmpty().take(1).forEach { image(card, it.url, 212, post.title) }
        paragraph(post.content, card)
        post.images.orEmpty().drop(1).forEach { image(card, it.url, 174, "Ảnh trong bài viết") }
        if (post.recipeId != null && post.recipeTitle != null) card.add(link("Xem công thức: ${post.recipeTitle}") {
            go(R.id.recipe, "id" to post.recipeId)
        }, 8)
        postActions(card, post, true) { work { showPost(api.post(itemId)) } }
        body.add(card)
        title("Bình luận (${post.commentCount})")
        val comments = post.comments.orEmpty()
        val replies = comments.groupBy { it.parentId }
        val byId = comments.associateBy { it.id }
        val queued = ArrayDeque<Pair<Comment, Int>>()
        replies[null].orEmpty().asReversed().forEach { queued.addLast(it to 0) }
        var updateReply: () -> Unit = {}
        while (queued.isNotEmpty()) {
            val (comment, depth) = queued.removeLast()
            val panel = panel()
            title(comment.author, panel, 15)
            byId[comment.parentId]?.author?.let { label -> empty("Trả lời $label", panel) }
            paragraph(comment.content, panel)
            val controls = row()
            controls.weighted(link("${if (comment.liked) "♥" else "♡"} ${comment.likes}") { button ->
                work(button, progress = false) {
                    api.likeComment(post.id, comment.id, Liked(!comment.liked))
                    showPost(api.post(itemId))
                }
            })
            controls.weighted(link("Trả lời") {
                reply = comment
                updateReply()
            }, 5)
            panel.add(controls, 3)
            body.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); marginStart = dp(depth.coerceAtMost(3) * 12) })
            replies[comment.id].orEmpty().asReversed().forEach { queued.addLast(it to depth + 1) }
        }
        if (comments.isEmpty()) empty("Hãy là người đầu tiên bình luận.")
        val replyButton = link("") {
            reply = null
            updateReply()
        }
        body.add(replyButton, 8)
        val input = field(body, "Viết bình luận", lines = 2, limit = 2000)
        updateReply = {
            replyButton.visibility = if (reply == null) View.GONE else View.VISIBLE
            replyButton.text = reply?.let { "Đang trả lời ${it.author} · Hủy" }.orEmpty()
            if (reply != null) input.requestFocus()
        }
        updateReply()
        body.add(action("Gửi bình luận", orange = true) { button ->
            hideKeyboard()
            work(button) {
                api.comment(post.id, CommentInput(input.value(), reply?.id))
                reply = null
                showPost(api.post(itemId))
            }
        }, 6)
    }
}

class CreatePostFragment : CommunityPage() {
    private val photos = mutableListOf<Uri>()
    private val uploaded = mutableMapOf<Uri, String>()
    private lateinit var previews: LinearLayout
    private var recipe: Recipe? = null
    private var type = "TIPS"
    private val picker = registerForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { selected ->
        photos.addAll(selected.filterNot { it in photos }.take(5 - photos.size))
        if (this::previews.isInitialized && view != null) showPhotos()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        header("Đăng bài mới")
        val title = field(body, "Tiêu đề bài viết", limit = 200)
        body.add(link(if (type == "QA") "Loại bài viết: Hỏi đáp" else "Loại bài viết: Mẹo bếp") { button ->
            val choices = arrayOf("Mẹo bếp", "Hỏi đáp")
            MaterialAlertDialogBuilder(requireContext()).setTitle("Loại bài viết").setItems(choices) { _, index ->
                type = if (index == 0) "TIPS" else "QA"
                (button as android.widget.TextView).text = "Loại bài viết: ${choices[index]}"
            }.show()
        }, 8)
        previews = column()
        body.add(previews, 8)
        body.add(link("+ Thêm ảnh") {
            if (photos.size == 5) info("Chỉ được đăng tối đa 5 ảnh.")
            else picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, 6)
        showPhotos()
        val content = field(body, "Nội dung bài viết", lines = 5, limit = 8000)
        val ingredient = field(body, "Nguyên liệu liên quan (tùy chọn)", lines = 2, limit = 1000)
        body.add(link(recipe?.let { "Công thức: ${it.title}" } ?: "Chọn công thức liên quan") { button ->
            chooseRecipe(button)
        }, 8)
        val error = lineError()
        body.add(action("Đăng bài", orange = true) { button ->
            if (title.value().isBlank() || content.value().isBlank()) {
                error.showError("Vui lòng nhập tiêu đề và nội dung")
                return@action
            }
            hideKeyboard()
            error.showError("")
            work(button, error = { error.showError(it) }) {
                val selectedPhotos = photos.toList()
                val images = selectedPhotos.map { uri ->
                    uploaded[uri] ?: api.upload(photoPart(requireContext().contentResolver, uri)).url.also { uploaded[uri] = it }
                }
                val text = if (ingredient.value().isBlank()) content.value()
                    else "${content.value()}\n\nNguyên liệu liên quan\n${ingredient.value()}"
                val post = api.createPost(PostInput(type, title.value(), text, recipe?.id, images))
                findNavAfterPost(post.id)
            }
        }, 10)
    }

    private fun findNavAfterPost(id: Long) {
        val nav = androidx.navigation.fragment.NavHostFragment.findNavController(this)
        nav.navigate(R.id.post, androidx.core.os.bundleOf("id" to id),
            androidx.navigation.NavOptions.Builder().setPopUpTo(R.id.create_post, true).build())
    }

    private fun chooseRecipe(button: View) {
        val form = column().apply { setPadding(dp(16), dp(6), dp(16), dp(16)) }
        val search = field(form, "Tìm công thức công khai", limit = 120)
        val resultPane = column()
        val scroll = android.widget.ScrollView(requireContext()).apply { addView(resultPane) }
        form.addView(scroll, LinearLayout.LayoutParams(-1, dp(230)))
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle("Đính kèm công thức")
            .setView(form).setNegativeButton("Đóng", null).setNeutralButton("Bỏ liên kết") { _, _ ->
                recipe = null
                (button as android.widget.TextView).text = "Chọn công thức liên quan"
            }.show()
        var pending: Job? = null
        fun update() {
            pending?.cancel()
            pending = work(progress = false, error = { empty(it, resultPane) }) {
                delay(220)
                val found = repo.recipes(query = search.value())
                if (!dialog.isShowing) return@work
                resultPane.removeAllViews()
                if (found.isEmpty()) empty("Không tìm thấy công thức.", resultPane)
                found.forEach { candidate ->
                    resultPane.add(link(candidate.title) {
                        recipe = candidate
                        (button as android.widget.TextView).text = "Công thức: ${candidate.title}"
                        dialog.dismiss()
                    })
                }
            }
        }
        search.doAfterTextChanged { update() }
        dialog.setOnDismissListener { pending?.cancel() }
        update()
    }

    private fun showPhotos() {
        previews.removeAllViews()
        photos.forEachIndexed { index, uri ->
            val picture = image(previews, null, 125, "Ảnh ${index + 1}")
            com.bumptech.glide.Glide.with(this).load(uri).centerCrop().into(picture)
            previews.add(link("Bỏ ảnh ${index + 1}") { photos.remove(uri); uploaded.remove(uri); showPhotos() })
        }
    }

}
