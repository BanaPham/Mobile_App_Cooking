package com.cooking.app.service;

import com.cooking.app.dto.CookingDtos.*;
import com.cooking.app.repository.CookingRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CommunityService {
    private static final String POST = """
        SELECT p.post_id AS id, p.user_id AS userId, p.title, p.content, p.post_type AS type,
            p.related_recipe_id AS recipeId, r.title AS recipeTitle, p.like_count AS likes,
            u.full_name AS author, u.avatar_url AS avatarUrl, CONVERT(varchar(19),p.created_at,126) AS createdAt,
            m.media_url AS imageUrl,
            CAST(CASE WHEN l.like_id IS NULL THEN 0 ELSE 1 END AS bit) AS liked,
            (SELECT COUNT(*) FROM Post_Comments WHERE post_id=p.post_id) AS commentCount
        FROM Community_Posts p JOIN Users u ON u.user_id=p.user_id
        LEFT JOIN Recipes r ON r.recipe_id=p.related_recipe_id AND r.visibility='PUBLIC' AND r.status='PUBLISHED'
        LEFT JOIN Post_Likes l ON l.post_id=p.post_id AND l.user_id=?
        OUTER APPLY (SELECT TOP (1) media_url FROM Post_Media
            WHERE post_id=p.post_id AND media_type='IMAGE' ORDER BY display_order, media_id) m
        """;
    private final CookingRepository db;
    private final MediaService media;

    public CommunityService(CookingRepository db, MediaService media) {
        this.db = db;
        this.media = media;
    }

    public List<Map<String, Object>> posts(long user, String type, String query, int page) {
        String filter = type == null || type.isBlank() ? "" : type(type);
        String keyword = Input.optional(query, "Từ khóa", 120);
        String match = keyword == null ? "%" : "%" + keyword.replace("!", "!!").replace("%", "!%")
            .replace("_", "!_").replace("[", "![") + "%";
        return db.rows(POST + """
             WHERE (?='' OR p.post_type=?) AND (p.title COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!'
             OR p.content COLLATE Latin1_General_100_CI_AI LIKE ? ESCAPE '!')
             ORDER BY p.created_at DESC, p.post_id DESC OFFSET ? ROWS FETCH NEXT 24 ROWS ONLY
            """, user, filter, filter, match, match, Input.between(page, 0, 10000, "Trang") * 24);
    }

    public Map<String, Object> detail(long user, long post) {
        var result = db.one(POST + " WHERE p.post_id=?", user, post);
        result.put("images", db.rows("""
            SELECT media_url AS url FROM Post_Media WHERE post_id=? AND media_type='IMAGE' ORDER BY display_order, media_id
            """, post));
        result.put("comments", db.rows("""
            SELECT c.comment_id AS id, c.parent_comment_id AS parentId, c.content, u.full_name AS author,
                CONVERT(varchar(19),c.created_at,126) AS createdAt, u.avatar_url AS avatarUrl,
                CAST(CASE WHEN l.comment_like_id IS NULL THEN 0 ELSE 1 END AS bit) AS liked,
                (SELECT COUNT(*) FROM Comment_Likes WHERE comment_id=c.comment_id) AS likes
            FROM Post_Comments c JOIN Users u ON u.user_id=c.user_id
            LEFT JOIN Comment_Likes l ON l.comment_id=c.comment_id AND l.user_id=?
            WHERE c.post_id=? ORDER BY c.comment_id
            """, user, post));
        return result;
    }

    @Transactional
    public Id create(long user, PostInput input) {
        if (input.recipeId() != null) db.one("""
            SELECT recipe_id FROM Recipes WHERE recipe_id=? AND visibility='PUBLIC' AND status='PUBLISHED'
            AND parent_recipe_id IS NULL
            """, input.recipeId());
        var images = input.images() == null ? List.<String>of() : input.images();
        if (images.size() > 5) throw ApiException.bad("Chỉ được đăng tối đa 5 ảnh");
        for (String image : images) media.validateOwner(image, user);
        long post = db.id("""
            INSERT INTO Community_Posts(user_id,post_type,related_recipe_id,title,content)
            OUTPUT inserted.post_id VALUES (?,?,?,?,?)
            """, user, type(input.type()), input.recipeId(), Input.text(input.title(), "tiêu đề", 200),
            Input.text(input.content(), "nội dung", 8000));
        int order = 0;
        for (String image : images) db.update("""
            INSERT INTO Post_Media(post_id,media_type,media_url,display_order) VALUES (?,'IMAGE',?,?)
            """, post, image, order++);
        return new Id(post);
    }

    @Transactional
    public Id comment(long user, long post, CommentInput input) {
        db.one("SELECT post_id FROM Community_Posts WHERE post_id=?", post);
        if (input.parentId() != null)
            db.one("SELECT comment_id FROM Post_Comments WHERE comment_id=? AND post_id=?", input.parentId(), post);
        return new Id(db.id("""
            INSERT INTO Post_Comments(post_id,user_id,parent_comment_id,content)
            OUTPUT inserted.comment_id VALUES (?,?,?,?)
            """, post, user, input.parentId(), Input.text(input.content(), "bình luận", 2000)));
    }

    @Transactional
    public void likePost(long user, long post, LikeInput input) {
        boolean like = liked(input);
        db.one("SELECT post_id FROM Community_Posts WITH (UPDLOCK,HOLDLOCK) WHERE post_id=?", post);
        if (like) db.update("""
            INSERT INTO Post_Likes(post_id,user_id)
            SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM Post_Likes WHERE post_id=? AND user_id=?)
            """, post, user, post, user);
        else db.update("DELETE FROM Post_Likes WHERE post_id=? AND user_id=?", post, user);
        db.update("UPDATE Community_Posts SET like_count=(SELECT COUNT(*) FROM Post_Likes WHERE post_id=?) WHERE post_id=?",
            post, post);
    }

    @Transactional
    public void likeComment(long user, long post, long comment, LikeInput input) {
        boolean like = liked(input);
        db.one("SELECT comment_id FROM Post_Comments WITH (UPDLOCK,HOLDLOCK) WHERE comment_id=? AND post_id=?", comment, post);
        if (like) db.update("""
            INSERT INTO Comment_Likes(comment_id,user_id)
            SELECT ?,? WHERE NOT EXISTS(SELECT 1 FROM Comment_Likes WHERE comment_id=? AND user_id=?)
            """, comment, user, comment, user);
        else db.update("DELETE FROM Comment_Likes WHERE comment_id=? AND user_id=?", comment, user);
    }

    private boolean liked(LikeInput input) {
        if (input.liked() == null) throw ApiException.bad("Thiếu trạng thái thích");
        return input.liked();
    }

    private String type(String value) {
        if (!"TIPS".equals(value) && !"QA".equals(value)) throw ApiException.bad("Vui lòng chọn loại bài viết");
        return value;
    }
}
