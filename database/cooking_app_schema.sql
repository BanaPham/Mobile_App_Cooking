-- =========================================================
-- DATABASE CREATION & SETUP SCRIPT CHO ỨNG DỤNG NẤU ĂN VÀ CÔNG THỨC
-- Hệ quản trị cơ sở dữ liệu: Microsoft SQL Server
-- =========================================================

CREATE DATABASE CookingAppDB;
GO

USE CookingAppDB;
GO

-- 1. Phân hệ người dùng và xác thực
CREATE TABLE Users (
    user_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    email VARCHAR(254) NULL,
    phone VARCHAR(20) NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name NVARCHAR(120) NOT NULL,
    avatar_url VARCHAR(2048) NULL,
    bio NVARCHAR(1000) NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER', -- USER, ADMIN
    account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, LOCKED
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    updated_at DATETIME2 NOT NULL DEFAULT SYSDATETIME()
);
GO

-- 2. Phân hệ công thức và danh mục
CREATE TABLE Categories (
    category_id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(100) NOT NULL UNIQUE,
    slug VARCHAR(120) NOT NULL UNIQUE,
    icon_url VARCHAR(2048) NULL,
    display_order INT NOT NULL DEFAULT 0
);
GO

CREATE TABLE Recipes (
    recipe_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    author_id BIGINT NOT NULL,
    title NVARCHAR(200) NOT NULL,
    description NVARCHAR(MAX) NULL,
    image_url VARCHAR(2048) NULL,
    cook_time_mins INT NOT NULL,
    default_servings INT NOT NULL,
    is_featured BIT NOT NULL DEFAULT 0,
    view_count INT NOT NULL DEFAULT 0,
    parent_recipe_id BIGINT NULL,
    visibility VARCHAR(20) NOT NULL DEFAULT 'PUBLIC', -- PRIVATE, PUBLIC
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT', -- DRAFT, PENDING, PUBLISHED, REJECTED, HIDDEN
    difficulty NVARCHAR(20) NOT NULL, -- Dễ, Trung bình, Khó
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_Recipes_Author FOREIGN KEY (author_id) REFERENCES Users(user_id),
    CONSTRAINT FK_Recipes_Parent FOREIGN KEY (parent_recipe_id) REFERENCES Recipes(recipe_id)
);
GO

CREATE TABLE Recipe_Categories (
    recipe_id BIGINT NOT NULL,
    category_id INT NOT NULL,
    PRIMARY KEY (recipe_id, category_id),
    CONSTRAINT FK_RecipeCategories_Recipe FOREIGN KEY (recipe_id) REFERENCES Recipes(recipe_id) ON DELETE CASCADE,
    CONSTRAINT FK_RecipeCategories_Category FOREIGN KEY (category_id) REFERENCES Categories(category_id) ON DELETE CASCADE
);
GO

CREATE TABLE Recipe_Steps (
    step_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    step_title NVARCHAR(200) NULL,
    step_number INT NOT NULL,
    image_url VARCHAR(2048) NULL,
    instruction NVARCHAR(MAX) NOT NULL,
    tip_text NVARCHAR(100) NULL,
    timer_seconds INT NULL,
    CONSTRAINT FK_RecipeSteps_Recipe FOREIGN KEY (recipe_id) REFERENCES Recipes(recipe_id) ON DELETE CASCADE,
    CONSTRAINT UQ_Recipe_StepNumber UNIQUE (recipe_id, step_number)
);
GO

CREATE TABLE Recipe_Nutrition (
    recipe_id BIGINT PRIMARY KEY,
    calories FLOAT NULL,
    protein_g FLOAT NULL,
    carbs_g FLOAT NULL,
    fat_g FLOAT NULL,
    fiber_g FLOAT NULL,
    extra_info NVARCHAR(MAX) NULL,
    CONSTRAINT FK_RecipeNutrition_Recipe FOREIGN KEY (recipe_id) REFERENCES Recipes(recipe_id) ON DELETE CASCADE
);
GO

-- 3. Phân hệ nguyên liệu
CREATE TABLE Ingredients (
    ingredient_id INT IDENTITY(1,1) PRIMARY KEY,
    name NVARCHAR(150) NOT NULL UNIQUE,
    category_group NVARCHAR(100) NOT NULL,
    default_unit NVARCHAR(30) NOT NULL
);
GO

CREATE TABLE Recipe_Ingredients (
    recipe_ingredient_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    ingredient_id INT NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit NVARCHAR(30) NOT NULL,
    note NVARCHAR(500) NULL,
    CONSTRAINT FK_RecipeIngredients_Recipe FOREIGN KEY (recipe_id) REFERENCES Recipes(recipe_id) ON DELETE CASCADE,
    CONSTRAINT FK_RecipeIngredients_Ingredient FOREIGN KEY (ingredient_id) REFERENCES Ingredients(ingredient_id),
    CONSTRAINT UQ_Recipe_Ingredient UNIQUE (recipe_id, ingredient_id)
);
GO

-- 4. Phân hệ cá nhân hóa
CREATE TABLE User_Favorites (
    favorite_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recipe_id BIGINT NOT NULL,
    custom_servings INT NULL,
    custom_notes NVARCHAR(MAX) NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_UserFavorites_User FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT FK_UserFavorites_Recipe FOREIGN KEY (recipe_id) REFERENCES Recipes(recipe_id) ON DELETE CASCADE,
    CONSTRAINT UQ_User_Recipe_Favorite UNIQUE (user_id, recipe_id)
);
GO

CREATE TABLE User_Cooked_History (
    history_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recipe_id BIGINT NOT NULL,
    cooked_at DATETIME2 NULL DEFAULT SYSDATETIME(),
    user_image_url VARCHAR(2048) NULL,
    CONSTRAINT FK_UserCookedHistory_User FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT FK_UserCookedHistory_Recipe FOREIGN KEY (recipe_id) REFERENCES Recipes(recipe_id) ON DELETE CASCADE
);
GO

-- 5. Phân hệ danh sách đi chợ
CREATE TABLE Shopping_Groups (
    group_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    recipe_id BIGINT NOT NULL,
    selected_servings INT NOT NULL CHECK (selected_servings > 0),
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_ShoppingGroups_User FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT FK_ShoppingGroups_Recipe FOREIGN KEY (recipe_id) REFERENCES Recipes(recipe_id) ON DELETE CASCADE
);
GO

CREATE TABLE Shopping_Items (
    item_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    group_id BIGINT NOT NULL,
    ingredient_id INT NULL,
    custom_item_name NVARCHAR(200) NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit NVARCHAR(30) NOT NULL,
    is_purchased BIT NOT NULL DEFAULT 0,
    CONSTRAINT FK_ShoppingItems_Group FOREIGN KEY (group_id) REFERENCES Shopping_Groups(group_id) ON DELETE CASCADE,
    CONSTRAINT FK_ShoppingItems_Ingredient FOREIGN KEY (ingredient_id) REFERENCES Ingredients(ingredient_id)
);
GO

-- 6. Phân hệ cộng đồng
CREATE TABLE Recipe_Reviews (
    review_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    recipe_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT FK_RecipeReviews_Recipe FOREIGN KEY (recipe_id) REFERENCES Recipes(recipe_id) ON DELETE CASCADE,
    CONSTRAINT FK_RecipeReviews_User FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT UQ_User_Recipe_Review UNIQUE (user_id, recipe_id)
);
GO

CREATE TABLE Community_Posts (
    post_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    post_type VARCHAR(10) NOT NULL, -- TIPS hoặc QA
    related_recipe_id BIGINT NULL,
    title NVARCHAR(200) NOT NULL,
    content NVARCHAR(MAX) NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_CommunityPosts_User FOREIGN KEY (user_id) REFERENCES Users(user_id) ON DELETE CASCADE,
    CONSTRAINT FK_CommunityPosts_Recipe FOREIGN KEY (related_recipe_id) REFERENCES Recipes(recipe_id)
);
GO

CREATE TABLE Post_Comments (
    comment_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_comment_id BIGINT NULL,
    content NVARCHAR(MAX) NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_PostComments_Post FOREIGN KEY (post_id) REFERENCES Community_Posts(post_id) ON DELETE CASCADE,
    CONSTRAINT FK_PostComments_User FOREIGN KEY (user_id) REFERENCES Users(user_id),
    CONSTRAINT FK_PostComments_Parent FOREIGN KEY (parent_comment_id) REFERENCES Post_Comments(comment_id)
);
GO

CREATE TABLE Post_Likes (
    like_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_PostLikes_Post FOREIGN KEY (post_id) REFERENCES Community_Posts(post_id) ON DELETE CASCADE,
    CONSTRAINT FK_PostLikes_User FOREIGN KEY (user_id) REFERENCES Users(user_id),
    CONSTRAINT UQ_User_Post_Like UNIQUE (user_id, post_id)
);
GO

CREATE TABLE Post_Media (
    media_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    post_id BIGINT NOT NULL,
    media_type VARCHAR(10) NOT NULL, -- IMAGE, VIDEO
    media_url VARCHAR(2048) NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT FK_PostMedia_Post FOREIGN KEY (post_id) REFERENCES Community_Posts(post_id) ON DELETE CASCADE
);
GO

CREATE TABLE Comment_Likes (
    comment_like_id BIGINT IDENTITY(1,1) PRIMARY KEY,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSDATETIME(),
    CONSTRAINT FK_CommentLikes_Comment FOREIGN KEY (comment_id) REFERENCES Post_Comments(comment_id) ON DELETE CASCADE,
    CONSTRAINT FK_CommentLikes_User FOREIGN KEY (user_id) REFERENCES Users(user_id),
    CONSTRAINT UQ_User_Comment_Like UNIQUE (user_id, comment_id)
);
GO
