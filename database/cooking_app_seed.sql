USE CookingAppDB;
GO
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @author BIGINT;
IF NOT EXISTS (SELECT 1 FROM Users WHERE email='recipes@bepnha.invalid')
    INSERT INTO Users(email,password_hash,full_name,account_status)
    VALUES ('recipes@bepnha.invalid','!SEED_ACCOUNT_LOCKED',N'Bếp Nhà','LOCKED');
SELECT @author=user_id FROM Users WHERE email='recipes@bepnha.invalid';

DECLARE @categories TABLE (slug VARCHAR(120) PRIMARY KEY, name NVARCHAR(100), ord INT);
INSERT @categories VALUES
('mon-sang',N'Món sáng',1),('mon-chinh',N'Món chính',2),('mon-canh',N'Món canh',3),
('mon-an-vat',N'Món ăn vặt',4),('do-uong',N'Đồ uống',5),('mon-chay',N'Món chay',6),
('mon-trang-mieng',N'Món tráng miệng',7);
INSERT INTO Categories(name,slug,display_order)
SELECT c.name,c.slug,c.ord FROM @categories c
WHERE NOT EXISTS (SELECT 1 FROM Categories x WHERE x.slug=c.slug OR x.name=c.name);

DECLARE @ingredients TABLE (name NVARCHAR(150) PRIMARY KEY, grp NVARCHAR(100), unit NVARCHAR(30));
INSERT @ingredients VALUES
(N'Bánh phở',N'Tinh bột',N'g'),(N'Thịt bò',N'Thịt',N'g'),(N'Nước dùng bò',N'Nước dùng',N'ml'),
(N'Hành lá',N'Rau',N'g'),(N'Gạo tấm',N'Tinh bột',N'g'),(N'Sườn heo',N'Thịt',N'g'),
(N'Nước mắm',N'Gia vị',N'ml'),(N'Mật ong',N'Gia vị',N'ml'),(N'Dưa leo',N'Rau',N'g'),
(N'Tôm',N'Hải sản',N'g'),(N'Bánh tráng',N'Tinh bột',N'cái'),(N'Bún tươi',N'Tinh bột',N'g'),
(N'Xà lách',N'Rau',N'g'),(N'Cá lóc',N'Cá',N'g'),(N'Cà chua',N'Rau',N'g'),
(N'Dứa',N'Rau',N'g'),(N'Me chua',N'Gia vị',N'g'),(N'Rau thơm',N'Rau',N'g'),
(N'Trà đen',N'Đồ uống',N'g'),(N'Tắc',N'Trái cây',N'quả'),(N'Đường',N'Gia vị',N'g'),
(N'Đậu hũ',N'Đậu',N'g'),(N'Nấm rơm',N'Rau',N'g'),(N'Nước tương',N'Gia vị',N'ml'),
(N'Đậu xanh',N'Đậu',N'g'),(N'Nước cốt dừa',N'Gia vị',N'ml'),(N'Thịt ba chỉ heo',N'Thịt',N'g'),
(N'Trứng gà',N'Trứng',N'quả'),(N'Nước dừa',N'Nước dùng',N'ml'),
(N'Tỏi',N'Gia vị',N'g'),(N'Sả',N'Gia vị',N'g');
INSERT INTO Ingredients(name,category_group,default_unit)
SELECT i.name,i.grp,i.unit FROM @ingredients i WHERE NOT EXISTS (SELECT 1 FROM Ingredients x WHERE x.name=i.name);

DECLARE @recipes TABLE (
  code VARCHAR(30) PRIMARY KEY, title NVARCHAR(200), description NVARCHAR(600), category VARCHAR(120),
  minutes INT, servings INT, difficulty NVARCHAR(20), featured BIT, calories FLOAT, protein FLOAT, carbs FLOAT, fat FLOAT
);
INSERT @recipes VALUES
('pho_bo',N'Phở bò gia truyền',N'Bánh phở mềm, nước dùng bò trong và thịt bò thái mỏng. Công thức dùng nước dùng nấu sẵn.', 'mon-sang',30,2,N'Dễ',1,430,27,55,11),
('com_tam',N'Cơm tấm sườn nướng',N'Cơm tấm mềm thơm ăn cùng sườn nướng sả, dưa leo và nước mắm.', 'mon-chinh',45,2,N'Trung bình',1,620,31,65,24),
('goi_cuon',N'Gỏi cuốn tôm',N'Cuốn bánh tráng với tôm, bún và rau tươi. Dùng ngay khi bánh còn mềm.', 'mon-an-vat',25,2,N'Dễ',1,290,20,42,4),
('canh_chua',N'Canh chua cá lóc',N'Canh cá nấu với dứa, cà chua và me, dậy mùi rau thơm.', 'mon-canh',30,3,N'Dễ',0,230,25,15,8),
('tra_tac',N'Trà tắc',N'Trà đen pha cùng nước tắc và một ít đường, dùng với đá.', 'do-uong',10,2,N'Dễ',0,56,0,14,0),
('dau_hu',N'Đậu hũ kho nấm',N'Đậu hũ vàng cạnh, kho nhẹ với nấm rơm và nước tương.', 'mon-chay',25,2,N'Dễ',0,260,18,17,14),
('che_dau',N'Chè đậu xanh cốt dừa',N'Đậu xanh ninh mềm với đường, thêm nước cốt dừa lúc ăn.', 'mon-trang-mieng',40,3,N'Dễ',0,285,10,46,8),
('thit_kho',N'Thịt kho trứng',N'Thịt ba chỉ kho nước dừa với trứng gà, ăn kèm cơm nóng.', 'mon-chinh',55,3,N'Trung bình',0,476,27,12,33);

INSERT INTO Recipes(author_id,title,description,image_url,cook_time_mins,default_servings,is_featured,visibility,status,difficulty)
SELECT @author,x.title,x.description,CONCAT('asset:',x.code),x.minutes,x.servings,x.featured,'PUBLIC','PUBLISHED',x.difficulty
FROM @recipes x WHERE NOT EXISTS (SELECT 1 FROM Recipes r WHERE r.title=x.title AND r.author_id=@author AND r.parent_recipe_id IS NULL);

DECLARE @matches TABLE (code VARCHAR(30) PRIMARY KEY,id BIGINT);
INSERT @matches
SELECT x.code,MIN(r.recipe_id) FROM @recipes x JOIN Recipes r ON x.title=r.title
WHERE r.author_id=@author AND r.parent_recipe_id IS NULL GROUP BY x.code;

INSERT INTO Recipe_Categories
SELECT m.id,c.category_id FROM @matches m JOIN @recipes x ON m.code=x.code JOIN Categories c ON c.slug=x.category
WHERE NOT EXISTS (SELECT 1 FROM Recipe_Categories v WHERE v.recipe_id=m.id AND v.category_id=c.category_id);
INSERT INTO Recipe_Nutrition(recipe_id,calories,protein_g,carbs_g,fat_g,extra_info)
SELECT m.id,x.calories,x.protein,x.carbs,x.fat,N'Ước tính mỗi khẩu phần. Số liệu thay đổi theo nguyên liệu thực tế.'
FROM @matches m JOIN @recipes x ON m.code=x.code
WHERE NOT EXISTS (SELECT 1 FROM Recipe_Nutrition n WHERE n.recipe_id=m.id);

DECLARE @amounts TABLE (code VARCHAR(30), name NVARCHAR(150), qty DECIMAL(10,2), note NVARCHAR(500));
INSERT @amounts VALUES
('pho_bo',N'Bánh phở',220,N'Trụng trước khi chan'),('pho_bo',N'Thịt bò',180,N'Thái lát mỏng'),
('pho_bo',N'Nước dùng bò',800,NULL),('pho_bo',N'Hành lá',18,N'Thái nhỏ'),('pho_bo',N'Rau thơm',25,NULL),
('com_tam',N'Gạo tấm',170,NULL),('com_tam',N'Sườn heo',350,NULL),('com_tam',N'Nước mắm',25,NULL),
('com_tam',N'Mật ong',12,NULL),('com_tam',N'Dưa leo',100,NULL),('com_tam',N'Sả',25,N'Băm nhỏ'),
('goi_cuon',N'Tôm',220,NULL),('goi_cuon',N'Bánh tráng',8,NULL),('goi_cuon',N'Bún tươi',160,NULL),
('goi_cuon',N'Xà lách',100,NULL),('goi_cuon',N'Rau thơm',30,NULL),
('canh_chua',N'Cá lóc',450,N'Cắt khúc'),('canh_chua',N'Cà chua',180,N'Bổ múi cau'),
('canh_chua',N'Dứa',120,NULL),('canh_chua',N'Me chua',25,NULL),('canh_chua',N'Nước mắm',18,NULL),
('canh_chua',N'Rau thơm',30,NULL),
('tra_tac',N'Trà đen',8,NULL),('tra_tac',N'Tắc',6,NULL),('tra_tac',N'Đường',25,NULL),
('dau_hu',N'Đậu hũ',350,N'Cắt miếng vừa ăn'),('dau_hu',N'Nấm rơm',175,NULL),
('dau_hu',N'Nước tương',35,NULL),('dau_hu',N'Đường',10,NULL),('dau_hu',N'Hành lá',16,NULL),
('che_dau',N'Đậu xanh',140,N'Đậu cà vỏ'),('che_dau',N'Nước cốt dừa',130,NULL),('che_dau',N'Đường',40,NULL),
('thit_kho',N'Thịt ba chỉ heo',400,N'Cắt khối'),('thit_kho',N'Trứng gà',3,NULL),
('thit_kho',N'Nước dừa',450,NULL),('thit_kho',N'Nước mắm',30,NULL),
('thit_kho',N'Đường',16,NULL),('thit_kho',N'Tỏi',12,N'Băm nhỏ');

INSERT INTO Recipe_Ingredients(recipe_id,ingredient_id,quantity,unit,note)
SELECT m.id,i.ingredient_id,a.qty,i.default_unit,a.note FROM @amounts a
JOIN @matches m ON m.code=a.code JOIN Ingredients i ON i.name=a.name
WHERE NOT EXISTS (SELECT 1 FROM Recipe_Ingredients v WHERE v.recipe_id=m.id AND v.ingredient_id=i.ingredient_id);

DECLARE @steps TABLE (code VARCHAR(30), seq INT, title NVARCHAR(200), guide NVARCHAR(1000), tip NVARCHAR(100), seconds INT);
INSERT @steps VALUES
('pho_bo',1,N'Đun nước dùng',N'Đun sôi nước dùng bò rồi hạ lửa. Nếm lại và giữ nước dùng nóng đến lúc chan.',N'Hớt bọt nhẹ để nước dùng trong.',300),
('pho_bo',2,N'Trụng bánh và nấu thịt',N'Trụng bánh phở bằng nước sôi, để ráo. Cho thịt thái lát vào nồi nước dùng sôi và nấu chín.',N'Thái thịt ngang thớ.',NULL),
('pho_bo',3,N'Chan phở',N'Xếp bánh phở và thịt bò vào tô. Thêm hành, chan nước dùng nóng và ăn cùng rau thơm.',NULL,NULL),
('com_tam',1,N'Nấu cơm tấm',N'Vo gạo tấm và nấu với lượng nước theo hướng dẫn trên bao bì. Để cơm trong nồi vài phút sau khi chín.',NULL,NULL),
('com_tam',2,N'Ướp sườn',N'Trộn sả băm, mật ong và một phần nước mắm. Phủ đều lên lát sườn rồi để trong tủ lạnh 15 phút.',N'Ướp trong hộp kín.',900),
('com_tam',3,N'Nướng sườn',N'Nướng cả hai mặt đến khi sườn chín, để nghỉ 3 phút. Dọn cùng cơm tấm, dưa leo và phần nước mắm còn lại.',N'Bỏ phần nước ướp đã tiếp xúc thịt sống.',NULL),
('goi_cuon',1,N'Sơ chế',N'Rửa rau, để ráo. Luộc tôm chín, bóc vỏ và chẻ đôi. Chuẩn bị bún và bánh tráng.',NULL,NULL),
('goi_cuon',2,N'Cuốn gỏi',N'Làm ẩm bánh tráng. Xếp rau, bún và tôm, gập hai mép rồi cuốn gọn.',N'Chỉ làm ẩm từng bánh khi chuẩn bị cuốn.',NULL),
('goi_cuon',3,N'Dọn món',N'Xếp các cuốn cách nhau trên đĩa. Dùng ngay với chén nước chấm tùy thích.',NULL,NULL),
('canh_chua',1,N'Lấy nước me',N'Ngâm me với nước nóng, dầm rồi lọc bỏ hạt. Rửa cá và các loại rau củ.',NULL,300),
('canh_chua',2,N'Nấu canh',N'Đun khoảng 1 lít nước, cho cà chua và dứa. Cho cá vào, nấu đến khi cá chín rồi thêm nước me và nước mắm.',N'Thêm nước me từ từ để dễ chỉnh độ chua.',NULL),
('canh_chua',3,N'Thêm rau thơm',N'Tắt bếp và thêm rau thơm thái nhỏ. Múc canh ra tô.',NULL,NULL),
('tra_tac',1,N'Ủ trà',N'Ủ trà trong 350 ml nước nóng, rồi lọc bã.',NULL,240),
('tra_tac',2,N'Pha trà tắc',N'Khuấy tan đường trong trà. Để nguội bớt, thêm nước tắc đã bỏ hạt rồi rót vào ly có đá.',N'Thêm tắc sau khi trà bớt nóng.',NULL),
('dau_hu',1,N'Làm vàng đậu hũ',N'Thấm khô đậu hũ. Áp chảo đến khi các cạnh vàng, rồi cho nấm rơm đã rửa sạch vào.',NULL,NULL),
('dau_hu',2,N'Kho với nấm',N'Thêm nước tương, đường và 100 ml nước. Kho lửa nhỏ đến khi nấm chín, nước sánh. Rắc hành lá.',NULL,720),
('che_dau',1,N'Ninh đậu xanh',N'Rửa đậu, đun với khoảng 600 ml nước đến khi đậu mềm. Khuấy nhẹ để đáy nồi không dính.',N'Hạ lửa ngay khi nước bắt đầu sôi.',1800),
('che_dau',2,N'Hoàn thiện chè',N'Thêm đường, khuấy tan. Múc ra chén và rưới nước cốt dừa trước khi ăn.',NULL,NULL),
('thit_kho',1,N'Chuẩn bị thịt và trứng',N'Luộc chín trứng rồi bóc vỏ. Trộn thịt ba chỉ với tỏi, đường và nước mắm.',NULL,600),
('thit_kho',2,N'Kho thịt',N'Đảo thịt đến khi săn, cho nước dừa rồi kho liu riu đến khi thịt chín mềm.',N'Hớt bọt và tránh để nước cạn.',1800),
('thit_kho',3,N'Kho cùng trứng',N'Cho trứng vào nồi, kho thêm cho thấm. Nếm lại và dọn với cơm.',NULL,600);

INSERT INTO Recipe_Steps(recipe_id,step_number,step_title,instruction,tip_text,timer_seconds)
SELECT m.id,s.seq,s.title,s.guide,s.tip,s.seconds FROM @steps s JOIN @matches m ON m.code=s.code
WHERE NOT EXISTS (SELECT 1 FROM Recipe_Steps v WHERE v.recipe_id=m.id AND v.step_number=s.seq);

IF NOT EXISTS (SELECT 1 FROM Community_Posts WHERE user_id=@author AND title=N'Bí quyết nấu canh chua đậm đà')
BEGIN
    INSERT INTO Community_Posts(user_id,post_type,title,content,related_recipe_id)
    SELECT @author,'TIPS',N'Bí quyết nấu canh chua đậm đà',
        N'Thêm nước me từng chút sau khi cá đã chín, nếm lại rồi mới nêm nước mắm. Tắt bếp khi cho rau thơm để giữ mùi.',
        m.id FROM @matches m WHERE m.code='canh_chua';
    INSERT INTO Post_Media(post_id,media_type,media_url)
    VALUES (CONVERT(BIGINT,SCOPE_IDENTITY()),'IMAGE','asset:canh_chua');
END;

COMMIT TRANSACTION;
GO
