package com.cooking.app.service;

import com.cooking.app.dto.CookingDtos.*;
import com.cooking.app.repository.CookingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ShoppingService {
    private final CookingRepository db;
    private final RecipeService recipes;

    public ShoppingService(CookingRepository db, RecipeService recipes) {
        this.db = db;
        this.recipes = recipes;
    }

    public List<Map<String, Object>> list(long user) {
        var groups = new LinkedHashMap<Long, Map<String, Object>>();
        for (var group : db.rows("""
            SELECT g.group_id AS id, r.recipe_id AS recipeId, r.title, r.image_url AS imageUrl,
                g.selected_servings AS servings FROM Shopping_Groups g JOIN Recipes r ON g.recipe_id=r.recipe_id
            WHERE g.user_id=? ORDER BY g.group_id DESC
            """, user)) {
            Map<String, Object> copy = new LinkedHashMap<>(group);
            copy.put("items", new java.util.ArrayList<Map<String, Object>>());
            groups.put(((Number) group.get("id")).longValue(), copy);
        }
        for (var item : db.rows("""
            SELECT s.item_id AS id, s.group_id AS groupId, COALESCE(s.custom_item_name,i.name) AS name,
                s.quantity, s.unit, s.is_purchased AS purchased
            FROM Shopping_Items s JOIN Shopping_Groups g ON g.group_id=s.group_id
            LEFT JOIN Ingredients i ON i.ingredient_id=s.ingredient_id
            WHERE g.user_id=? ORDER BY s.item_id
            """, user)) {
            var group = groups.get(((Number) item.get("groupId")).longValue());
            if (group != null) {
                @SuppressWarnings("unchecked")
                var items = (List<Map<String, Object>>) group.get("items");
                items.add(item);
            }
        }
        return List.copyOf(groups.values());
    }

    @Transactional
    public Id add(long user, long recipe, ShoppingAdd input) {
        Map<String, Object> source = recipes.readable(user, recipe);
        int servings = Input.between(input.servings(), 1, 99, "Khẩu phần");
        int base = ((Number) source.get("servings")).intValue();
        if (base < 1) throw ApiException.bad("Công thức có khẩu phần gốc không hợp lệ");
        var ingredients = db.rows("SELECT ingredient_id, quantity, unit FROM Recipe_Ingredients WHERE recipe_id=?", recipe);
        if (ingredients.isEmpty()) throw ApiException.bad("Món này chưa có nguyên liệu");
        long group = db.id("""
            INSERT INTO Shopping_Groups(user_id, recipe_id, selected_servings)
            OUTPUT inserted.group_id VALUES (?,?,?)
            """, user, recipe, servings);
        for (var ingredient : ingredients) {
            BigDecimal quantity = ((BigDecimal) ingredient.get("quantity")).multiply(BigDecimal.valueOf(servings))
                .divide(BigDecimal.valueOf(base), 2, RoundingMode.HALF_UP).max(new BigDecimal("0.01"));
            db.update("INSERT INTO Shopping_Items(group_id, ingredient_id, quantity, unit) VALUES (?,?,?,?)",
                group, ingredient.get("ingredient_id"), Input.quantity(quantity), ingredient.get("unit"));
        }
        return new Id(group);
    }

    public Id addItem(long user, long group, ShoppingItemInput input) {
        String name = Input.text(input.name(), "tên nguyên liệu", 200);
        var rows = db.rows("""
            INSERT INTO Shopping_Items(group_id,custom_item_name,quantity,unit) OUTPUT inserted.item_id AS id
            SELECT group_id,?,?,? FROM Shopping_Groups WHERE group_id=? AND user_id=?
            """, name, Input.quantity(input.quantity()), Input.text(input.unit(), "đơn vị", 30), group, user);
        if (rows.isEmpty()) throw ApiException.missing("Không tìm thấy nhóm mua sắm");
        return new Id(((Number) rows.getFirst().get("id")).longValue());
    }

    public void purchased(long user, long item, Purchased input) {
        if (input.purchased() == null) throw ApiException.bad("Thiếu trạng thái mua");
        changed(db.update("""
            UPDATE s SET is_purchased=? FROM Shopping_Items s JOIN Shopping_Groups g ON g.group_id=s.group_id
            WHERE s.item_id=? AND g.user_id=?
            """, input.purchased(), item, user));
    }

    public void edit(long user, long item, ShoppingItemInput input) {
        changed(db.update("""
            UPDATE s SET custom_item_name=?, quantity=?, unit=? FROM Shopping_Items s
            JOIN Shopping_Groups g ON g.group_id=s.group_id WHERE s.item_id=? AND g.user_id=?
            """, Input.text(input.name(), "tên nguyên liệu", 200), Input.quantity(input.quantity()),
            Input.text(input.unit(), "đơn vị", 30), item, user));
    }

    public void delete(long user, long item) {
        changed(db.update("""
            DELETE s FROM Shopping_Items s JOIN Shopping_Groups g ON g.group_id=s.group_id
            WHERE s.item_id=? AND g.user_id=?
            """, item, user));
    }

    private void changed(int rows) {
        if (rows == 0) throw ApiException.missing("Không tìm thấy nguyên liệu trong danh sách");
    }
}
