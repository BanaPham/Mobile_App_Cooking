# Ứng Dụng Nấu Ăn & Công Thức 

## 📖 Giới Thiệu Chung & Mục Đích Dự Án
Ứng dụng được xây dựng nhằm cung cấp cho người dùng hệ thống công thức nấu ăn phong phú, hướng dẫn chế biến chi tiết, tập trung vào **các món ăn Việt Nam phổ biến** (dành cho gia đình, học sinh, sinh viên) với giao diện hoàn toàn bằng tiếng Việt. 

**Các tính năng nổi bật cốt lõi:**
- **Khám phá & Tìm kiếm:** Xem món ăn nổi bật, mới nhất, yêu thích; tìm kiếm theo tên món, nguyên liệu hoặc lọc theo danh mục (Món sáng, Món chính, Món canh, Món ăn vặt, Đồ uống, Món chay).
- **Chi tiết công thức:** Hiển thị nguyên liệu, định lượng, thời gian nấu, khẩu phần, các bước thực hiện trực quan và thông tin dinh dưỡng (calo, protein, carbs, fat).
- **Danh sách đi chợ thông minh:** Cho phép thêm toàn bộ nguyên liệu của công thức vào danh sách mua sắm chỉ bằng một nút bấm, tự động tính toán theo khẩu phần, hỗ trợ đánh dấu nguyên liệu đã mua.
- **Cá nhân hóa & Cộng đồng:** Lưu công thức yêu thích, lưu lịch sử các món đã nấu, chia sẻ công thức/mẹo nấu ăn, đánh giá, bình luận và hỏi đáp (Q&A).

---

## 📁 Cấu Trúc Thư Mục Dự Án

Dự án được phân chia thành 3 thành phần chính: **Backend**, **Frontend** và **Database**.

### 1. ⚙️ Backend (Spring Boot - Java)
Thư mục `backend/` chứa mã nguồn phía máy chủ, chịu trách nhiệm xử lý logic nghiệp vụ, xác thực và kết nối cơ sở dữ liệu thông qua RESTful API.
- **`controller/`**: Chứa các lớp điều hướng API (`@RestController`), nhận yêu cầu từ ứng dụng di động và trả về dữ liệu (JSON).
- **`dto/`** *(Data Transfer Object)*: Chứa các lớp đối tượng truyền dữ liệu giữa client và server mà không làm lộ trực tiếp cấu trúc database entity.
- **`entity/`**: Chứa các lớp thực thể ánh xạ trực tiếp với các bảng trong cơ sở dữ liệu SQL Server (Sử dụng Spring Data JPA).
- **`repository/`**: Chứa các interface giao tiếp với cơ sở dữ liệu, thực hiện các thao tác CRUD cơ bản và truy vấn dữ liệu.
- **`security/`**: Cấu hình bảo mật, phân quyền người dùng và xác thực (JWT hoặc Spring Security).
- **`service/`**: Chứa các lớp xử lý logic nghiệp vụ chính của ứng dụng (ví dụ: tính toán định lượng nguyên liệu đi chợ, xử lý công thức).
- **`resources/application.yml`**: File cấu hình kết nối cơ sở dữ liệu SQL Server, cổng chạy ứng dụng và thông số Hibernate.

---

### 2. 📱 Frontend (Android - Kotlin & XML)
Thư mục `frontend/` chứa mã nguồn ứng dụng di động chạy trên hệ điều hành Android.
- **`activities/`**: Xử lý logic và hiển thị các màn hình chính của ứng dụng (Màn hình đăng nhập, Trang chủ, Chi tiết món ăn, Danh sách đi chợ...).
- **`adapters/`**: Chứa các adapter quản lý việc hiển thị danh sách dữ liệu (danh mục, danh sách món ăn, nguyên liệu) lên các thành phần cuộn mượt mà như `RecyclerView`.
- **`models/`**: Lưu trữ các lớp cấu trúc dữ liệu mô phỏng lại các đối tượng từ server gửi về.
- **`network/`**: Cấu hình các công cụ kết nối mạng (như thư viện `Retrofit`) để gọi API từ phía Backend Spring Boot.
- **`repositories/`**: Lớp trung gian chịu trách nhiệm lấy dữ liệu từ API hoặc local và cung cấp cho tầng giao diện.
- **`utils/`**: Chứa các hàm tiện ích dùng chung trong toàn ứng dụng (định dạng tiền tệ, xử lý ngày tháng, thông báo...).
- **`viewmodels/`**: Quản lý và duy trì dữ liệu giao diện theo mô hình kiến trúc MVVM, giúp dữ liệu không bị mất khi xoay màn hình.
- **`res/layout/`**: Thiết kế giao diện trực quan bằng các file XML kết hợp với Material Design.
- **`res/drawable/`**: Lưu trữ hình ảnh, biểu tượng (icon), vector assets và giao diện nền nút bấm.
- **`res/values/`**: Định nghĩa các giá trị tĩnh dùng chung như màu sắc (`colors.xml`), chuỗi văn bản tiếng Việt (`strings.xml`) và chủ đề giao diện (`themes.xml`).

---

### 3. 🗄️ Database (SQL Server)
Thư mục `database/` quản lý cấu trúc dữ liệu của toàn bộ hệ thống.
- **`cooking_app_schema.sql`**: File mã lệnh SQL Server hoàn chỉnh dùng để khởi tạo toàn bộ **18 thực thể** (Users, Categories, Recipes, Recipe_Ingredients, Shopping_Items, Community_Posts, v.v.), thiết lập hệ thống khóa chính, khóa ngoại (28 liên kết) và các ràng buộc toàn vẹn dữ liệu (`UNIQUE`, `CHECK`, `IDENTITY`) phục vụ xuyên suốt cho ứng dụng.