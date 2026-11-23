package com.cooking.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.cooking.app.R
import com.cooking.app.activities.loadPhoto
import com.cooking.app.activities.num
import com.cooking.app.models.Recipe
import kotlin.math.roundToInt

class RecipeAdapter(private val recipes: List<Recipe>, private val mode: String, private val layout: String,
                    private val select: (Recipe) -> Unit, private val heart: (Recipe, View) -> Unit,
                    private val edit: (Recipe, View) -> Unit) : RecyclerView.Adapter<RecipeAdapter.Holder>() {
    class Holder(val root: View) : RecyclerView.ViewHolder(root) {
        val content: LinearLayout = root.findViewById(R.id.recipe_content)
        val image: ImageView = root.findViewById(R.id.recipe_image)
        val title: TextView = root.findViewById(R.id.recipe_title)
        val meta: TextView = root.findViewById(R.id.recipe_meta)
        val favorite: ImageButton = root.findViewById(R.id.recipe_favorite)
        val edit: ImageButton = root.findViewById(R.id.recipe_edit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val resource = if (layout == "hero") R.layout.item_featured else R.layout.item_recipe
        val root = LayoutInflater.from(parent.context).inflate(resource, parent, false)
        val holder = Holder(root)
        fun dp(n: Int) = (parent.resources.displayMetrics.density * n).roundToInt()
        val width = when (layout) { "hero" -> dp(278); "strip" -> dp(166); else -> -1 }
        root.layoutParams = RecyclerView.LayoutParams(width, if (layout == "hero") dp(178) else -2).apply {
            marginEnd = if (layout == "list") 0 else dp(8)
            bottomMargin = dp(8)
        }
        if (layout == "list") {
            holder.content.orientation = LinearLayout.HORIZONTAL
            holder.image.layoutParams = LinearLayout.LayoutParams(dp(113), dp(108))
        }
        return holder
    }

    override fun getItemCount() = recipes.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val recipe = recipes[position]
        holder.title.text = recipe.title
        val rating = recipe.rating?.let { "  ·  ★ ${num(it)}" }.orEmpty()
        holder.meta.text = "${recipe.cookMinutes} phút  ·  ${recipe.difficulty}$rating"
        holder.root.setOnClickListener { select(recipe) }
        holder.favorite.isVisible = mode != "cooked"
        holder.favorite.setImageResource(if (recipe.favorite) R.drawable.ic_heart_fill else R.drawable.ic_heart)
        holder.favorite.contentDescription = if (recipe.favorite) "Bỏ yêu thích ${recipe.title}" else "Yêu thích ${recipe.title}"
        holder.favorite.setOnClickListener { heart(recipe, it) }
        holder.edit.isVisible = mode == "favorites" || mode == "cooked"
        holder.edit.contentDescription = if (mode == "cooked") "Đánh giá ${recipe.title}" else "Sửa ${recipe.title}"
        holder.edit.setOnClickListener { edit(recipe, it) }
        holder.image.contentDescription = recipe.title
        loadPhoto(holder.image, recipe.imageUrl)
    }
}
