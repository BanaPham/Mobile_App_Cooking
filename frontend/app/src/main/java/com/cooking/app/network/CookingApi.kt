package com.cooking.app.network

import com.cooking.app.models.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface CookingApi {
    @POST("api/auth/register") suspend fun register(@Body data: Register): Message
    @POST("api/auth/login") suspend fun login(@Body data: Login): Session
    @POST("api/auth/forgot-password") suspend fun forgot(@Body data: Forgot): Message
    @POST("api/auth/reset-password") suspend fun reset(@Body data: Reset): Message
    @GET("api/me") suspend fun profile(): Profile
    @PUT("api/me") suspend fun updateProfile(@Body data: ProfileUpdate)
    @PUT("api/me/password") suspend fun changePassword(@Body data: ChangePassword): Session
    @GET("api/categories") suspend fun categories(): List<Category>
    @GET("api/ingredients") suspend fun ingredients(): List<IngredientOption>
    @GET("api/recipes") suspend fun recipes(
        @Query("scope") scope: String, @Query("q") query: String?, @Query("category") category: Int?,
        @Query("difficulty") difficulty: String?, @Query("min") min: Int?, @Query("max") max: Int?,
        @Query("sort") sort: String, @Query("page") page: Int
    ): List<Recipe>
    @GET("api/recipes/{id}") suspend fun recipe(@Path("id") id: Long): RecipeDetail
    @PUT("api/recipes/{id}/favorite") suspend fun favorite(@Path("id") id: Long): Created
    @DELETE("api/recipes/{id}/favorite") suspend fun unfavorite(@Path("id") id: Long)
    @PUT("api/recipes/{id}") suspend fun editRecipe(@Path("id") id: Long, @Body data: RecipeEdit)
    @POST("api/recipes/{id}/complete") suspend fun complete(@Path("id") id: Long): Created
    @PUT("api/recipes/{id}/review") suspend fun review(@Path("id") id: Long, @Body data: Review)
    @POST("api/recipes/{id}/shopping") suspend fun addShopping(@Path("id") id: Long, @Body data: ShoppingAdd): Created
    @GET("api/shopping") suspend fun shopping(): List<Group>
    @POST("api/shopping/{id}/items") suspend fun addItem(@Path("id") id: Long, @Body data: ItemInput): Created
    @PUT("api/shopping/items/{id}") suspend fun editItem(@Path("id") id: Long, @Body data: ItemInput)
    @PUT("api/shopping/items/{id}/purchased") suspend fun purchased(@Path("id") id: Long, @Body data: Purchased)
    @DELETE("api/shopping/items/{id}") suspend fun deleteItem(@Path("id") id: Long)
    @GET("api/posts") suspend fun posts(@Query("type") type: String?, @Query("q") query: String?, @Query("page") page: Int): List<Post>
    @POST("api/posts") suspend fun createPost(@Body data: PostInput): Created
    @GET("api/posts/{id}") suspend fun post(@Path("id") id: Long): Post
    @PUT("api/posts/{id}/like") suspend fun likePost(@Path("id") id: Long, @Body data: Liked)
    @POST("api/posts/{id}/comments") suspend fun comment(@Path("id") id: Long, @Body data: CommentInput): Created
    @PUT("api/posts/{id}/comments/{comment}/like") suspend fun likeComment(
        @Path("id") id: Long, @Path("comment") comment: Long, @Body data: Liked
    )
    @Multipart @POST("api/media") suspend fun upload(@Part file: MultipartBody.Part): Uploaded
}
