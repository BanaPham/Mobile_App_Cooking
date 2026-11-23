package com.cooking.app.service;

import com.cooking.app.dto.CookingDtos.*;
import com.cooking.app.repository.CookingRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecipeService {
    private static final String CARD = """
        SELECT r.recipe_id AS id, r.title, r.image_url AS imageUrl, r.cook_time_mins AS cookMinutes,
        r.difficulty, r.is_featured AS featured, CAST(CASE WHEN f.favorite_id IS NULL THEN 0 ELSE 1 END AS bit) AS favorite,
        f.recipe_id AS copyId, COALESCE(h.timesCooked, 0) AS timesCooked,
        stats.rating AS rating, stats.reviewCount AS reviewCount
        FROM Recipes r
        OUTER APPLY (SELECT TOP (1) f.favorite_id, f.recipe_id FROM User_Favorites f
            JOIN Recipes saved ON saved.recipe_id=f.recipe_id WHERE f.user_id=?
            AND (f.recipe_id=r.recipe_id OR (saved.parent_recipe_id=r.recipe_id AND saved.author_id=f.user_id))
            ORDER BY f.favorite_id) f
        OUTER APPLY (SELECT COUNT(*) AS timesCooked, MAX(cooked_at) AS lastCooked
            FROM User_Cooked_History WHERE user_id=? AND recipe_id=r.recipe_id) h
        OUTER APPLY (SELECT AVG(CAST(rating AS float)) AS rating, COUNT(*) AS reviewCount
            FROM Recipe_Reviews WHERE recipe_id=COALESCE(r.parent_recipe_id,r.recipe_id)) stats
        """;
    private static final String PUBLIC = "r.visibility='PUBLIC' AND r.status='PUBLISHED' AND r.parent_recipe_id IS NULL";
    private final CookingRepository db;

    public RecipeService(CookingRepository db) {
        this.db = db;
    }

    public List<Map<String, Object>> categories() {
        return db.rows("SELECT category_id AS id, name, slug FROM Categories ORDER BY display_order, category_id");
    }

    public List<Map<String, Object>> ingredients() {
        return db.rows("SELECT ingredient_id AS id, name, default_unit AS unit FROM Ingredients ORDER BY name");
    }

    public List<Map<String, Object>> list(long user, String scope, String query, Integer category, String difficulty,
                                          Integer min, Integer max, String sort, int page) {
        String predicate = switch (scope) {
            case "catalog" -> PUBLIC;
            case "favorites" -> "r.author_id=? AND r.parent_recipe_id IS NOT NULL AND f.recipe_id=r.recipe_id";
            case "cooked" -> """
                h.timesCooked>0 AND (r.author_id=? OR (r.visibility='PUBLIC' AND r.status='PUBLISHED'))
                """;
            default -> throw ApiException.bad("Danh sách công thức không hợp lệ");
        };
        List<Object> args = new ArrayList<>(List.of(user, user));
        if (!"catalog".equals(scope)) args.add(user);
        StringBuilder sql = new StringBuilder(CARD).append(" WHERE ").append(predicate);
        String keyword = Input.optional(query, "Từ khóa", 120);
        if (keyword != null) {
            for (String word : keyword.split("\\s+")) {
                String match = "%" + word.replace("!", "!!").replace("%", "!%").replace("_", "!_")
                    .replace("[", "![") + "%";
                sql.append("""
                    AND (r.title COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!' OR EXISTS (
                        SELECT 1 FROM Recipe_Ingredients ri JOIN Ingredients i ON i.ingredient_id=ri.ingredient_id
                        WHERE ri.recipe_id=r.recipe_id AND i.name COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!'))
                    """);
                args.add(match);
                args.add(match);
            }
        }
        if (category != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM Recipe_Categories WHERE recipe_id=r.recipe_id AND category_id=?)");
            args.add(category);
        }
        if (difficulty != null && !difficulty.isBlank()) {
            sql.append(" AND r.difficulty=?");
            args.add(difficulty(difficulty));
        }
        if (min != null) {
            sql.append(" AND r.cook_time_mins>=?");
            args.add(Input.between(min, 0, 1440, "Thời gian tối thiểu"));
        }
        if (max != null) {
            sql.append(" AND r.cook_time_mins<=?");
            args.add(Input.between(max, 0, 1440, "Thời gian tối đa"));
        }
        if (min != null && max != null && min > max) throw ApiException.bad("Khoảng thời gian không hợp lệ");
        if ("featured".equals(sort) && "catalog".equals(scope)) sql.append(" AND r.is_featured=1");
        String order = switch (sort) {
            case "popular" -> "stats.rating DESC, stats.reviewCount DESC, r.view_count DESC";
            case "recommended" -> """
                (SELECT COUNT(*) FROM User_Favorites fav JOIN Recipes copy ON copy.recipe_id=fav.recipe_id
                    WHERE copy.recipe_id=r.recipe_id OR copy.parent_recipe_id=r.recipe_id) DESC,
                stats.rating DESC, stats.reviewCount DESC
                """;
            case "fast" -> "r.cook_time_mins ASC";
            default -> "cooked".equals(scope) ? "h.lastCooked DESC" : "r.created_at DESC";
        };
        sql.append(" ORDER BY ").append(order).append(", r.recipe_id DESC OFFSET ? ROWS FETCH NEXT 24 ROWS ONLY");
        args.add(Input.between(page, 0, 10000, "Trang") * 24);
        return db.rows(sql.toString(), args.toArray());
    }

    public Map<String, Object> readable(long user, long recipe) {
        return db.one("""
            SELECT recipe_id AS id, author_id AS authorId, parent_recipe_id AS parentId,
                default_servings AS servings, title, image_url AS imageUrl
            FROM Recipes r WHERE recipe_id=? AND (author_id=? OR (
            visibility='PUBLIC' AND status='PUBLISHED' AND parent_recipe_id IS NULL))
            """, recipe, user);
    }

    public Map<String, Object> detail(long user, long recipe) {
        Map<String, Object> access = readable(user, recipe);
        Map<String, Object> detail = new LinkedHashMap<>(db.one(CARD + " WHERE r.recipe_id=?", user, user, recipe));
        detail.putAll(db.one("""
            SELECT r.description, r.default_servings AS servings, r.parent_recipe_id AS parentId, u.full_name AS author
            FROM Recipes r JOIN Users u ON u.user_id=r.author_id WHERE r.recipe_id=?
            """, recipe));
        detail.put("ingredients", db.rows("""
            SELECT i.ingredient_id AS ingredientId, i.name, ri.quantity, ri.unit, ri.note
            FROM Recipe_Ingredients ri JOIN Ingredients i ON i.ingredient_id=ri.ingredient_id
            WHERE ri.recipe_id=? ORDER BY ri.recipe_ingredient_id
            """, recipe));
        detail.put("steps", db.rows("""
            SELECT step_number AS number, step_title AS title, instruction, image_url AS imageUrl, tip_text AS tip,
                timer_seconds AS timerSeconds FROM Recipe_Steps WHERE recipe_id=? ORDER BY step_number
            """, recipe));
        detail.put("nutrition", db.rows("""
            SELECT calories, protein_g AS protein, carbs_g AS carbs, fat_g AS fat, fiber_g AS fiber
            FROM Recipe_Nutrition WHERE recipe_id=?
            """, recipe).stream().findFirst().orElse(null));
        long original = access.get("parentId") == null ? recipe : ((Number) access.get("parentId")).longValue();
        detail.put("myReview", db.rows("""
            SELECT rating FROM Recipe_Reviews WHERE recipe_id=? AND user_id=?
            """, original, user).stream().findFirst().orElse(null));
        return detail;
    }

    @Transactional
    public Id favorite(long user, long recipe) {
        db.lockUser(user);
        Map<String, Object> access = readable(user, recipe);
        long original = access.get("parentId") == null ? recipe : ((Number) access.get("parentId")).longValue();
        List<Map<String, Object>> copies = db.rows("""
            SELECT TOP (1) recipe_id AS id FROM Recipes WHERE author_id=? AND parent_recipe_id=? ORDER BY recipe_id
            """, user, original);
        long copy = copies.isEmpty() ? copyRecipe(user, original) : ((Number) copies.getFirst().get("id")).longValue();
        db.update("""
            INSERT INTO User_Favorites(user_id, recipe_id)
            SELECT ?,? WHERE NOT EXISTS (SELECT 1 FROM User_Favorites WHERE user_id=? AND recipe_id=?)
            """, user, copy, user, copy);
        return new Id(copy);
    }

    @Transactional
    public void unfavorite(long user, long recipe) {
        db.lockUser(user);
        readable(user, recipe);
        db.update("""
            DELETE f FROM User_Favorites f JOIN Recipes r ON r.recipe_id=f.recipe_id
            WHERE f.user_id=? AND (r.recipe_id=? OR r.parent_recipe_id=?)
            """, user, recipe, recipe);
    }

    private long copyRecipe(long user, long original) {
        long copy = db.id("""
            INSERT INTO Recipes(author_id,title,description,image_url,cook_time_mins,default_servings,
                parent_recipe_id,visibility,status,difficulty)
            OUTPUT inserted.recipe_id
            SELECT ?,title,description,image_url,cook_time_mins,default_servings,recipe_id,'PRIVATE','PUBLISHED',difficulty
            FROM Recipes WHERE recipe_id=?
            """, user, original);
        db.update("""
            INSERT INTO Recipe_Categories(recipe_id,category_id)
            SELECT ?,category_id FROM Recipe_Categories WHERE recipe_id=?
            """, copy, original);
        db.update("""
            INSERT INTO Recipe_Ingredients(recipe_id,ingredient_id,quantity,unit,note)
            SELECT ?,ingredient_id,quantity,unit,note FROM Recipe_Ingredients WHERE recipe_id=?
            """, copy, original);
        db.update("""
            INSERT INTO Recipe_Steps(recipe_id,step_number,step_title,instruction,image_url,tip_text,timer_seconds)
            SELECT ?,step_number,step_title,instruction,image_url,tip_text,timer_seconds FROM Recipe_Steps WHERE recipe_id=?
            """, copy, original);
        db.update("""
            INSERT INTO Recipe_Nutrition(recipe_id,calories,protein_g,carbs_g,fat_g,fiber_g,extra_info)
            SELECT ?,calories,protein_g,carbs_g,fat_g,fiber_g,extra_info FROM Recipe_Nutrition WHERE recipe_id=?
            """, copy, original);
        return copy;
    }

    @Transactional
    public void edit(long user, long recipe, RecipeEdit input) {
        db.lockUser(user);
        db.one("""
            SELECT r.recipe_id FROM Recipes r JOIN User_Favorites f ON f.recipe_id=r.recipe_id
            WHERE r.recipe_id=? AND r.author_id=? AND f.user_id=? AND r.parent_recipe_id IS NOT NULL
            """, recipe, user, user);
        if (input.ingredients() == null || input.ingredients().isEmpty() || input.ingredients().size() > 100
            || input.steps() == null || input.steps().isEmpty() || input.steps().size() > 100)
            throw ApiException.bad("Công thức cần từ 1 đến 100 nguyên liệu và bước nấu");
        db.update("""
            UPDATE Recipes SET title=?, description=?, default_servings=?, cook_time_mins=?, difficulty=? WHERE recipe_id=?
            """, Input.text(input.title(), "tên món", 200), Input.optional(input.description(), "Mô tả", 4000),
            Input.between(input.servings(), 1, 99, "Khẩu phần"), Input.between(input.cookMinutes(), 1, 1440, "Thời gian nấu"),
            difficulty(input.difficulty()), recipe);
        db.update("DELETE FROM Recipe_Ingredients WHERE recipe_id=?", recipe);
        var used = new java.util.HashSet<Integer>();
        for (IngredientInput ingredient : input.ingredients()) {
            if (ingredient == null || ingredient.ingredientId() == null || !used.add(ingredient.ingredientId()))
                throw ApiException.bad("Mỗi nguyên liệu chỉ được thêm một lần");
            db.one("SELECT ingredient_id FROM Ingredients WHERE ingredient_id=?", ingredient.ingredientId());
            db.update("""
                INSERT INTO Recipe_Ingredients(recipe_id,ingredient_id,quantity,unit,note) VALUES (?,?,?,?,?)
                """, recipe, ingredient.ingredientId(), Input.quantity(ingredient.quantity()),
                Input.text(ingredient.unit(), "đơn vị", 30), Input.optional(ingredient.note(), "Ghi chú", 500));
        }
        db.update("DELETE FROM Recipe_Steps WHERE recipe_id=?", recipe);
        int index = 1;
        for (StepInput step : input.steps()) {
            if (step == null) throw ApiException.bad("Bước nấu không hợp lệ");
            db.update("""
                INSERT INTO Recipe_Steps(recipe_id,step_number,step_title,instruction,image_url,tip_text,timer_seconds)
                VALUES (?,?,?,?,?,?,?)
                """, recipe, index++, Input.optional(step.title(), "Tên bước", 200),
                Input.text(step.instruction(), "hướng dẫn", 4000), Input.httpsUrl(step.imageUrl(), "Ảnh bước nấu"),
                Input.optional(step.tip(), "Mẹo", 100), step.timerSeconds() == null ? null :
                    Input.between(step.timerSeconds(), 1, 86400, "Số giây hẹn giờ"));
        }
    }

    public Id complete(long user, long recipe) {
        readable(user, recipe);
        db.one("SELECT TOP (1) step_id FROM Recipe_Steps WHERE recipe_id=?", recipe);
        return new Id(db.id("""
            INSERT INTO User_Cooked_History(user_id,recipe_id) OUTPUT inserted.history_id VALUES (?,?)
            """, user, recipe));
    }

    @Transactional
    public void review(long user, long recipe, Review input) {
        db.lockUser(user);
        Map<String, Object> access = readable(user, recipe);
        long original = access.get("parentId") == null ? recipe : ((Number) access.get("parentId")).longValue();
        if (db.rows("""
            SELECT TOP (1) h.history_id FROM User_Cooked_History h JOIN Recipes r ON r.recipe_id=h.recipe_id
            WHERE h.user_id=? AND (r.recipe_id=? OR r.parent_recipe_id=?)
            """, user, original, original).isEmpty()) throw ApiException.bad("Bạn cần hoàn thành món trước khi đánh giá");
        int rating = Input.between(input.rating(), 1, 5, "Điểm đánh giá");
        if (db.update("UPDATE Recipe_Reviews SET rating=? WHERE recipe_id=? AND user_id=?",
            rating, original, user) == 0) {
            db.update("INSERT INTO Recipe_Reviews(recipe_id,user_id,rating) VALUES (?,?,?)",
                original, user, rating);
        }
    }

    private String difficulty(String value) {
        if (!List.of("Dễ", "Trung bình", "Khó").contains(value == null ? "" : value))
            throw ApiException.bad("Độ khó không hợp lệ");
        return value;
    }
}
