package com.cooking.app.dto;

import java.math.BigDecimal;
import java.util.List;

public final class CookingDtos {
    private CookingDtos() {}

    public record StepInput(String title, String instruction, String tip, String imageUrl, Integer timerSeconds) {}
    public record IngredientInput(Integer ingredientId, BigDecimal quantity, String unit, String note) {}
    public record RecipeEdit(String title, String description, Integer servings, Integer cookMinutes, String difficulty,
                             List<IngredientInput> ingredients, List<StepInput> steps) {}
    public record ShoppingAdd(Integer servings) {}
    public record ShoppingItemInput(String name, BigDecimal quantity, String unit) {}
    public record Purchased(Boolean purchased) {}
    public record Review(Integer rating) {}
    public record PostInput(String type, String title, String content, Long recipeId, List<String> images) {}
    public record CommentInput(String content, Long parentId) {}
    public record LikeInput(Boolean liked) {}
    public record Id(long id) {}
}
