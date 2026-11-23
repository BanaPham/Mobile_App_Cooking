package com.cooking.app.models

data class Message(val message: String)
data class Session(val token: String)
data class Created(val id: Long)
data class Uploaded(val url: String)
data class Register(val fullName: String, val email: String, val phone: String, val password: String, val confirmation: String)
data class Login(val identifier: String, val password: String)
data class Forgot(val email: String)
data class Reset(val token: String, val password: String, val confirmation: String)
data class ChangePassword(val currentPassword: String, val password: String, val confirmation: String)
data class ProfileUpdate(val fullName: String, val email: String, val phone: String, val bio: String, val avatarUrl: String)
data class Profile(val id: Long, val fullName: String, val email: String, val phone: String?, val bio: String?,
                   val avatarUrl: String?, val favorites: Int, val cooked: Int, val posts: Int)

data class Category(val id: Int, val name: String, val slug: String)
data class IngredientOption(val id: Int, val name: String, val unit: String)
data class Filters(val category: Int? = null, val difficulty: String? = null, val min: Int? = null, val max: Int? = null)
data class Recipe(
    val id: Long, val title: String, val imageUrl: String?, val cookMinutes: Int, val difficulty: String,
    val featured: Boolean, val favorite: Boolean, val copyId: Long?, val timesCooked: Int,
    val rating: Double?, val reviewCount: Int
)
data class RecipeDetail(
    val id: Long, val title: String, val imageUrl: String?, val cookMinutes: Int, val difficulty: String,
    val favorite: Boolean, val rating: Double?, val reviewCount: Int, val description: String?, val servings: Int,
    val parentId: Long?, val author: String, val ingredients: List<Ingredient>, val steps: List<Step>,
    val nutrition: Nutrition?, val myReview: Review?
)
data class Ingredient(val ingredientId: Int, val name: String, val quantity: Double, val unit: String, val note: String?)
data class Step(val number: Int, val title: String?, val instruction: String, val imageUrl: String?,
                val tip: String?, val timerSeconds: Int?)
data class Nutrition(val calories: Double?, val protein: Double?, val carbs: Double?, val fat: Double?, val fiber: Double?)
data class IngredientEdit(val ingredientId: Int, val quantity: Double, val unit: String, val note: String?)
data class StepEdit(val title: String, val instruction: String, val tip: String, val imageUrl: String?, val timerSeconds: Int?)
data class RecipeEdit(val title: String, val description: String, val servings: Int?, val cookMinutes: Int?,
                      val difficulty: String, val ingredients: List<IngredientEdit>, val steps: List<StepEdit>)
data class Review(val rating: Int)
data class ShoppingAdd(val servings: Int)
data class Group(val id: Long, val recipeId: Long, val title: String, val imageUrl: String?, val servings: Int,
                 val items: List<ShoppingItem>)
data class ShoppingItem(val id: Long, val name: String, val quantity: Double, val unit: String, val purchased: Boolean)
data class ItemInput(val name: String, val quantity: Double?, val unit: String)
data class Purchased(val purchased: Boolean)

data class Post(
    val id: Long, val title: String, val content: String, val type: String, val recipeId: Long?,
    val recipeTitle: String?, val likes: Int, val author: String, val avatarUrl: String?, val createdAt: String?,
    val imageUrl: String?, val liked: Boolean, val commentCount: Int, val comments: List<Comment>?,
    val images: List<PostImage>?
)
data class PostImage(val url: String)
data class Comment(val id: Long, val parentId: Long?, val content: String, val author: String, val likes: Int,
                   val liked: Boolean, val createdAt: String?)
data class PostInput(val type: String, val title: String, val content: String, val recipeId: Long?, val images: List<String>)
data class CommentInput(val content: String, val parentId: Long?)
data class Liked(val liked: Boolean)
