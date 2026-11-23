package com.cooking.app.controller;

import com.cooking.app.dto.AuthDtos.*;
import com.cooking.app.dto.CookingDtos.*;
import com.cooking.app.repository.CookingRepository;
import com.cooking.app.service.*;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class CookingController {
    private final RecipeService recipes;
    private final ShoppingService shopping;
    private final CommunityService community;
    private final AuthService auth;
    private final CookingRepository db;

    public CookingController(RecipeService recipes, ShoppingService shopping, CommunityService community,
                             AuthService auth, CookingRepository db) {
        this.recipes = recipes;
        this.shopping = shopping;
        this.community = community;
        this.auth = auth;
        this.db = db;
    }

    @GetMapping("/me")
    Map<String, Object> profile(@AuthenticationPrincipal Jwt jwt) {
        long user = user(jwt);
        return db.one("""
            SELECT user_id AS id, full_name AS fullName, email, phone, avatar_url AS avatarUrl, bio,
                (SELECT COUNT(*) FROM User_Favorites WHERE user_id=?) AS favorites,
                (SELECT COUNT(*) FROM User_Cooked_History WHERE user_id=?) AS cooked,
                (SELECT COUNT(*) FROM Community_Posts WHERE user_id=?) AS posts
            FROM Users WHERE user_id=?
            """, user, user, user, user);
    }

    @PutMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changeProfile(@AuthenticationPrincipal Jwt jwt, @RequestBody ProfileUpdate body) { auth.updateProfile(user(jwt), body); }

    @PutMapping("/me/password")
    Session changePassword(@AuthenticationPrincipal Jwt jwt, @RequestBody PasswordChange body) {
        return auth.change(user(jwt), body);
    }

    @GetMapping("/categories")
    List<Map<String, Object>> categories() { return recipes.categories(); }

    @GetMapping("/ingredients")
    List<Map<String, Object>> ingredients() { return recipes.ingredients(); }

    @GetMapping("/recipes")
    List<Map<String, Object>> list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "catalog") String scope,
            @RequestParam(required = false) String q, @RequestParam(required = false) Integer category,
            @RequestParam(required = false) String difficulty, @RequestParam(required = false) Integer min,
            @RequestParam(required = false) Integer max, @RequestParam(defaultValue = "new") String sort,
            @RequestParam(defaultValue = "0") int page) {
        return recipes.list(user(jwt), scope, q, category, difficulty, min, max, sort, page);
    }

    @GetMapping("/recipes/{id}")
    Map<String, Object> recipe(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return recipes.detail(user(jwt), id);
    }

    @PutMapping("/recipes/{id}/favorite")
    Id favorite(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return recipes.favorite(user(jwt), id); }

    @DeleteMapping("/recipes/{id}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfavorite(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { recipes.unfavorite(user(jwt), id); }

    @PutMapping("/recipes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void edit(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody RecipeEdit body) {
        recipes.edit(user(jwt), id, body);
    }

    @PostMapping("/recipes/{id}/complete")
    Id complete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) { return recipes.complete(user(jwt), id); }

    @PutMapping("/recipes/{id}/review")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void review(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody Review body) {
        recipes.review(user(jwt), id, body);
    }

    @GetMapping("/shopping")
    List<Map<String, Object>> shopping(@AuthenticationPrincipal Jwt jwt) { return shopping.list(user(jwt)); }

    @PostMapping("/recipes/{id}/shopping")
    Id shopRecipe(@AuthenticationPrincipal Jwt jwt, @PathVariable long id, @RequestBody ShoppingAdd body) {
        return shopping.add(user(jwt), id, body);
    }

    @PostMapping("/shopping/{group}/items")
    Id shopItem(@AuthenticationPrincipal Jwt jwt, @PathVariable long group, @RequestBody ShoppingItemInput body) {
        return shopping.addItem(user(jwt), group, body);
    }

    @PutMapping("/shopping/items/{item}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void editItem(@AuthenticationPrincipal Jwt jwt, @PathVariable long item, @RequestBody ShoppingItemInput body) {
        shopping.edit(user(jwt), item, body);
    }

    @PutMapping("/shopping/items/{item}/purchased")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void purchased(@AuthenticationPrincipal Jwt jwt, @PathVariable long item, @RequestBody Purchased body) {
        shopping.purchased(user(jwt), item, body);
    }

    @DeleteMapping("/shopping/items/{item}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteItem(@AuthenticationPrincipal Jwt jwt, @PathVariable long item) { shopping.delete(user(jwt), item); }

    @GetMapping("/posts")
    List<Map<String, Object>> posts(@AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String type,
            @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page) {
        return community.posts(user(jwt), type, q, page);
    }

    @PostMapping("/posts")
    Id createPost(@AuthenticationPrincipal Jwt jwt, @RequestBody PostInput body) { return community.create(user(jwt), body); }

    @GetMapping("/posts/{post}")
    Map<String, Object> post(@AuthenticationPrincipal Jwt jwt, @PathVariable long post) {
        return community.detail(user(jwt), post);
    }

    @PostMapping("/posts/{post}/comments")
    Id comment(@AuthenticationPrincipal Jwt jwt, @PathVariable long post, @RequestBody CommentInput body) {
        return community.comment(user(jwt), post, body);
    }

    @PutMapping("/posts/{post}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void like(@AuthenticationPrincipal Jwt jwt, @PathVariable long post, @RequestBody LikeInput body) {
        community.likePost(user(jwt), post, body);
    }

    @PutMapping("/posts/{post}/comments/{comment}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void likeComment(@AuthenticationPrincipal Jwt jwt, @PathVariable long post, @PathVariable long comment,
                     @RequestBody LikeInput body) {
        community.likeComment(user(jwt), post, comment, body);
    }

    private long user(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
