# Bếp Nhà

Ứng dụng Android tiếng Việt để khám phá món ăn, nấu theo từng bước, lập danh sách đi chợ và trao đổi công thức.

| Thư mục | Thành phần |
| --- | --- |
| `frontend/` | Kotlin, XML, Material Design, Navigation Component, RecyclerView, Retrofit và Glide |
| `backend/` | Java 21, Spring Boot, REST API, Spring Data JPA, Spring JDBC, JWT và SMTP |
| `database/` | Script khởi tạo 18 bảng trên SQL Server và script nạp dữ liệu mẫu |

## 1. Chuẩn bị

- **Backend** dùng JDK 21 và Maven Wrapper trong `backend/`.
- **Android** dùng JDK 17, Android SDK Platform 35, Build-Tools 35 và Platform-Tools. Cài các gói Android trong **Tools → SDK Manager**.
- **Database** dùng SQL Server. Trên macOS có thể dùng container SQL Server đang chạy và VS Code để kết nối. Trên Windows có thể chạy SQL Server dưới dạng dịch vụ Windows và dùng SSMS để kết nối.

Thứ tự chạy là **SQL Server → Spring Boot → Android**. Chọn một cách chạy cho backend ở mục 3 và một cách chạy cho Android ở mục 4.

## 2. Tạo CookingAppDB và nạp dữ liệu mẫu

### macOS với Docker Desktop và VS Code

1. Mở **Docker Desktop → Containers** và bật container SQL Server. Với ánh xạ cổng `1433:1433`, kết nối từ Mac qua `localhost`, cổng `1433`.
2. Trong VS Code, mở **SQL Server → Connections**, chọn profile đã kết nối tới instance, ví dụ **Docker**. Dùng tài khoản có quyền tạo database, chẳng hạn `sa` của container local.
3. Mở file SQL, bỏ vùng bôi đen và nhấn **⌘⇧E** hoặc bấm **Run Query** để chạy cả file theo thứ tự bên dưới. [Kết nối SQL Server bằng VS Code](https://learn.microsoft.com/en-us/sql/tools/visual-studio-code-extensions/mssql/mssql-database-connections)

Nếu dùng SQL Server trên máy khác, điền hostname và cổng của máy đó. Microsoft hỗ trợ container SQL Server trên host Linux x86-64. Nếu dùng Mac Apple Silicon và chưa có instance, có thể kết nối tới một SQL Server trên máy khác. [Nền tảng được hỗ trợ cho container SQL Server](https://learn.microsoft.com/en-us/sql/linux/sql-server-linux-docker-container-deployment)

### Windows với SQL Server Management Studio

1. Mở **SSMS → Connect → Database Engine** rồi chọn instance, ví dụ `localhost` hoặc `.\SQLEXPRESS`. Có thể dùng **Windows Authentication** để tạo database nếu tài khoản Windows có quyền tương ứng.
2. Chọn **File → Open → File**, mở file schema, chọn `master` trên thanh công cụ và nhấn **F5** hoặc **Execute**. Sau đó chạy file seed bằng cùng kết nối.
3. Bấm **Refresh** ở **Object Explorer → Databases** và mở **CookingAppDB → Tables**. [Kết nối và chạy truy vấn trong SSMS](https://learn.microsoft.com/en-us/sql/ssms/quickstarts/ssms-connect-query-sql-server)

Cấu hình backend bên dưới dùng **SQL Login**. Nếu instance đang dùng chế độ Windows Authentication, quản trị viên có thể bật **SQL Server and Windows Authentication mode**, khởi động lại dịch vụ và tạo SQL Login riêng cho backend. Trong **User Mapping**, cấp quyền đọc và ghi `CookingAppDB`, ví dụ `db_datareader` và `db_datawriter`. [Chế độ xác thực SQL Server](https://learn.microsoft.com/en-us/sql/database-engine/configure-windows/change-server-authentication-mode), [Tạo SQL Login trong SSMS](https://learn.microsoft.com/en-us/sql/relational-databases/security/authentication-access/create-a-login)

Spring Boot kết nối qua **TCP**. Nếu dùng `SQLEXPRESS`, kiểm tra cổng thực tế vì instance đặt tên thường dùng cổng động. Để đặt cổng cố định, mở **SQL Server Configuration Manager → SQL Server Network Configuration → Protocols for SQLEXPRESS**, bật **TCP/IP**, rồi cấu hình **TCP Port** trong tab **IP Addresses**. Nếu **Listen All** đang bật, đặt cổng trong **IPAll** và xóa giá trị **TCP Dynamic Ports**. Khởi động lại dịch vụ SQL Server sau khi đổi. Ví dụ, kiểm tra cổng local bằng lệnh `Test-NetConnection localhost -Port 1433`. [Cấu hình cổng TCP SQL Server](https://learn.microsoft.com/en-us/sql/database-engine/configure-windows/configure-a-server-to-listen-on-a-specific-tcp-port)

### Thứ tự chạy script

1. [cooking_app_schema.sql](database/cooking_app_schema.sql) tạo database và 18 bảng. Chạy cả file khi khởi tạo lần đầu.
2. [cooking_app_seed.sql](database/cooking_app_seed.sql) thêm 7 danh mục, 8 món và dữ liệu mẫu. Có thể chạy lại file seed.
3. Chạy truy vấn dưới đây để kiểm tra dữ liệu.

   ```sql
   USE CookingAppDB;
   SELECT COUNT(*) AS categories FROM dbo.Categories;
   SELECT COUNT(*) AS public_recipes
   FROM dbo.Recipes WHERE visibility = 'PUBLIC' AND status = 'PUBLISHED';
   ```

Nếu cần giữ một `CookingAppDB` đã dùng schema cũ, có thể đổi tên database trong **cả hai script**, ví dụ thành `CookingAppDev`, để tạo database thử nghiệm riêng. Dùng cùng tên mới trong `DB_URL`.

## 3. Chạy backend Spring Boot

Backend chạy class `com.cooking.app.CookingApplication` ở cổng mặc định `8080`. Dùng một cách chạy tại một thời điểm để tránh trùng cổng.

| Biến môi trường | Giá trị |
| --- | --- |
| `DB_URL` | URL JDBC với hostname, cổng TCP và tên database thực tế. |
| `DB_USERNAME` | SQL Login có quyền đọc, ghi database. Với Docker local có thể là `sa`. |
| `DB_PASSWORD` | Mật khẩu SQL Login. |
| `JWT_SECRET` | Base64 của ít nhất 32 byte ngẫu nhiên. Dùng lại cùng giá trị nếu cần giữ phiên đăng nhập qua lần khởi động. |

Ví dụ dùng SQL Server local ở cổng `1433`. Với máy chủ phát triển local dùng chứng chỉ tự ký, thêm `;trustServerCertificate=true` vào cuối `DB_URL` nếu driver báo lỗi xác minh chứng chỉ.

### Cách A. Terminal macOS hoặc PowerShell Windows

Có thể mở Terminal riêng hoặc Terminal tích hợp trong Android Studio, VS Code hay IntelliJ. Các lệnh sau bắt đầu từ **thư mục gốc repo**. Nếu đang ở `frontend/`, dùng `cd ../backend` trên macOS hoặc `Set-Location ..\backend` trên Windows thay cho dòng đầu. Nếu đang ở `backend/`, bỏ qua dòng đổi thư mục.

**macOS dùng zsh**

```zsh
cd backend
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export DB_URL='jdbc:sqlserver://localhost:1433;databaseName=CookingAppDB;encrypt=true'
export DB_USERNAME='ten_sql_login'
read -rs 'DB_PASSWORD?Mat khau SQL Server: '
```

Nhập mật khẩu và nhấn **Enter**. Terminal ẩn ký tự khi nhập. Sau đó chạy tiếp trong **cùng Terminal**.

```zsh
printf '\n'
export DB_PASSWORD
export JWT_SECRET="$(openssl rand -base64 32)"
./mvnw spring-boot:run
```

**Windows dùng PowerShell**

```powershell
Set-Location .\backend
# Nếu cần, bỏ dấu # ở dòng sau và điền đường dẫn cài JDK 21.
# $env:JAVA_HOME = 'C:\duong-dan\den\jdk-21'

$env:DB_URL = 'jdbc:sqlserver://localhost:1433;databaseName=CookingAppDB;encrypt=true'
$env:DB_USERNAME = 'ten_sql_login'
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', (Read-Host 'Mat khau SQL Server' -AsSecureString)).Password

$bytes = [byte[]]::new(32)
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)

.\mvnw.cmd spring-boot:run
```

Có thể kiểm tra JDK mà Maven đang dùng bằng `./mvnw -v` trên macOS hoặc `.\mvnw.cmd -v` trên Windows.

### Cách B. Run hoặc Debug trong VS Code

1. Mở **thư mục `backend/`** bằng **File → Open Folder**. Cài **Extension Pack for Java** và chọn **JDK 21** qua **Java: Configure Java Runtime**.
2. Tạo file `.env` ngay cạnh `pom.xml` và thay các giá trị mẫu. File này được bỏ qua theo quy tắc `.gitignore` của dự án.

   ```dotenv
   DB_URL=jdbc:sqlserver://localhost:1433;databaseName=CookingAppDB;encrypt=true
   DB_USERNAME=ten_sql_login
   DB_PASSWORD="mat_khau_sql"
   JWT_SECRET=chuoi_base64_da_tao
   ```

   Tạo khóa JWT bằng `openssl rand -base64 32` trên macOS. Trên Windows, chạy phần từ `$bytes` tới `$env:JWT_SECRET` ở Cách A rồi gõ `$env:JWT_SECRET` để xem giá trị ngay trong PowerShell và nhập vào file local.

3. Tạo `.vscode/launch.json` trong thư mục backend. Nếu file đã có, thêm đối tượng **Bếp Nhà - Backend** vào mảng `configurations`.

   ```json
   {
     "version": "0.2.0",
     "configurations": [
       {
         "type": "java",
         "request": "launch",
         "name": "Bếp Nhà - Backend",
         "mainClass": "com.cooking.app.CookingApplication",
         "cwd": "${workspaceFolder}",
         "envFile": "${workspaceFolder}/.env"
       }
     ]
   }
   ```

4. Trong **Run and Debug**, chọn **Bếp Nhà - Backend** rồi bấm **Start Debugging** hoặc **Run Without Debugging**. [Chạy và debug Java trong VS Code](https://code.visualstudio.com/docs/java/java-debugging)

### Cách C. Run hoặc Debug trong IntelliJ IDEA

1. Chọn **File → Open**, mở `backend/pom.xml` dưới dạng Maven project và chọn **JDK 21**.
2. Vào **Run → Edit Configurations → + → Application**. Đặt **Main class** là `com.cooking.app.CookingApplication`, chọn module backend và đặt **Working directory** là thư mục `backend/`.
3. Thêm `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` và `JWT_SECRET` trong **Environment variables**. Nếu IntelliJ hỗ trợ chọn file môi trường, có thể dùng file `.env` ở Cách B. Giữ cấu hình có mật khẩu trên máy cục bộ.
4. Bấm **Run** hoặc **Debug**. [Cấu hình chạy Java trong IntelliJ IDEA](https://www.jetbrains.com/help/idea/run-debug-configuration-java-application.html), [Cấu hình biến môi trường](https://www.jetbrains.com/help/idea/program-arguments-and-environment-variables.html)

### Kiểm tra backend

Khi log hiện `Started CookingApplication`, giữ backend chạy và mở `http://localhost:8080/api/health` trên máy tính. Trong Terminal khác có thể dùng `curl --fail http://localhost:8080/api/health` trên macOS hoặc `Invoke-RestMethod http://localhost:8080/api/health` trên Windows.

Phản hồi `{"status":"UP"}` xác nhận HTTP server đã lên. Đăng ký tài khoản và tải công thức trong app để kiểm tra cả kết nối ghi và đọc SQL Server.

### Bật gửi email đặt lại mật khẩu

Đặt biến SMTP **trước khi** chạy backend. Dùng thông tin từ nhà cung cấp SMTP và địa chỉ gửi được nhà cung cấp chấp nhận.

| Biến | Giá trị |
| --- | --- |
| `SMTP_HOST` | Máy chủ SMTP |
| `SMTP_PORT` | Mặc định `587` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | Tài khoản và mật khẩu SMTP |
| `SMTP_FROM` | Email gửi được cấp phép |
| `SMTP_AUTH`, `SMTP_STARTTLS` | Mặc định `true` |

Chọn **Quên mật khẩu** trong app, nhập email đã đăng ký, rồi dán mã nhận được từ thư để đổi mật khẩu. Mã hết hạn sau 15 phút và mất hiệu lực khi mật khẩu thay đổi. Email thử nghiệm cần là địa chỉ bạn có quyền truy cập. Backend báo máy chủ chưa cấu hình gửi thư nếu thiếu `SMTP_FROM`.

## 4. Chạy app Android trên macOS hoặc Windows

### Cách A. Android Studio

1. Chọn **File → Open**, mở thư mục `frontend/` và đợi **Gradle Sync**. App dùng Java toolchain **17**.
2. Vào **Tools → Device Manager** và bật emulator. Trên Mac Apple Silicon, chọn system image **ARM64** nếu tạo emulator mới.
3. Chọn cấu hình **app** và thiết bị, rồi bấm **Run**. Chọn **Debug** nếu cần đặt breakpoint trong Kotlin.

### Cách B. Dòng lệnh với Gradle Wrapper và adb

Bật một emulator trước. Mở Terminal ở thư mục gốc repo, hoặc bỏ dòng đổi thư mục nếu đã ở `frontend/`. Thay đường dẫn SDK nếu **SDK Manager → Android SDK Location** của bạn khác ví dụ.

**macOS dùng Terminal**

```zsh
cd frontend
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew :app:installDebug
"$ANDROID_HOME/platform-tools/adb" shell am start -n com.cooking.app/.MainActivity
```

**Windows dùng PowerShell**

```powershell
Set-Location .\frontend
# Nếu cần, bỏ dấu # ở dòng sau và điền đường dẫn cài JDK 17.
# $env:JAVA_HOME = 'C:\duong-dan\den\jdk-17'

$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
.\gradlew.bat :app:installDebug
& "$env:ANDROID_HOME\platform-tools\adb.exe" shell am start -n com.cooking.app/.MainActivity
```

Ví dụ trên giả định có một thiết bị đang kết nối. `installDebug` build và cài APK, sau đó `adb` mở app. [Build và cài app từ dòng lệnh](https://developer.android.com/build/building-cmdline), [Android Debug Bridge](https://developer.android.com/tools/adb)

### Kết nối và thử app

- Bản debug mặc định dùng `http://10.0.2.2:8080/` trên emulator của cả macOS và Windows. Đây là địa chỉ để emulator gọi Spring Boot đang chạy trên máy tính. [Mạng của Android Emulator](https://developer.android.com/studio/run/emulator-networking-address)
- Với điện thoại thật cùng mạng, đặt `BEP_NHA_API_URL=http://IP_MAY_TINH:8080/` trong `frontend/gradle.properties`, Sync và chạy lại. URL cần kết thúc bằng `/`. Bản release dùng `BEP_NHA_RELEASE_API_URL` với URL HTTPS.
- Trong app, chọn **Đăng ký**, tạo tài khoản Bếp Nhà, đăng nhập rồi mở **Khám phá** để xem món mẫu. SQL Login dùng để backend kết nối database.
- Khi thử hẹn giờ, bật quyền thông báo nếu Android hỏi. Trên Android 12 trở lên, chọn **Mở cài đặt báo giờ** để bật báo thức chính xác cho Bếp Nhà. Khi thiếu quyền này, chuông có thể đến muộn lúc app ở nền. Hẹn giờ phát thông báo cục bộ trên thiết bị và dùng chuông ngắn nếu quyền thông báo bị tắt.

## Kiểm tra luồng chính

- Đăng ký với email và số điện thoại mới. Thử trùng từng trường để kiểm tra thông báo. Đăng nhập bằng cả hai định danh.
- Dùng từ khóa `thit` hoặc chọn danh mục, thời gian, độ khó trong bộ lọc.
- Thêm món vào yêu thích. Mở biểu tượng bút, chỉnh sửa bản sao và kiểm tra lại công thức gốc. Bỏ yêu thích rồi lưu lại để mở lại bản sao đã sửa.
- Đổi khẩu phần trong chi tiết món và chọn **Thêm tất cả vào giỏ**. Thêm một nguyên liệu tùy chỉnh trong nhóm và đánh dấu đã mua.
- Mở **Bắt đầu nấu**, đặt hẹn giờ ngắn, đi tới bước cuối rồi chọn **Hoàn thành**. Mở **Yêu thích → Đã thực hiện** để đánh giá.
- Tạo bài viết mẹo hoặc hỏi đáp, đính kèm ảnh hoặc công thức công khai. Thử thích, bình luận và trả lời bình luận.
- Đổi mật khẩu từ trang cá nhân và thử đặt lại qua email khi đã có SMTP.

## Build và kiểm thử từ dòng lệnh

| Thư mục | macOS | Windows PowerShell |
| --- | --- | --- |
| `backend/` với JDK 21 | `./mvnw test` | `.\mvnw.cmd test` |
| `frontend/` với JDK 17 | `./gradlew :app:assembleDebug :app:lintDebug` | `.\gradlew.bat :app:assembleDebug :app:lintDebug` |

APK debug nằm ở `frontend/app/build/outputs/apk/debug/app-debug.apk`. Ảnh đại diện và ảnh bài viết do người dùng tải lên là JPEG/PNG và được lưu tại `backend/uploads/` khi chạy backend từ thư mục trên. Có thể cấu hình thư mục khác bằng `UPLOAD_DIR`. Ảnh món mẫu đi kèm app Android.

### Ảnh dữ liệu mẫu

Tám ảnh món ăn trong `frontend/app/src/main/assets/recipes/` được tạo bằng ImageGen tích hợp. Mỗi ảnh được trích từ một ô của ảnh ghép, theo thứ tự phở bò, cơm tấm, gỏi cuốn, canh chua, trà tắc, đậu hũ kho nấm, chè đậu xanh và thịt kho trứng.

<details>
<summary>Prompt tạo ảnh mẫu</summary>

Use case: photorealistic https-free image atlas for a Vietnamese cooking Android app, no text and no app window. Generate one wide 4 column by 2 row contact sheet of 8 Vietnamese food photos arranged in eight precisely aligned, equally sized rectangular tiles, edge-to-edge, no borders, no gutters, no overlapping dishes between tiles. Each tile has its own cohesive tabletop background with the subject centered, with enough outer margin for landscape card cropping. Exact ordered grid: top row left to right (1) steaming authentic Vietnamese phở bò with thin beef slices, rice noodles, cilantro and lime in ivory ceramic bowl (2) cơm tấm with grilled pork chop, broken rice, cucumber and pickled carrot (3) gỏi cuốn tôm, translucent Vietnamese fresh shrimp summer rolls with herbs, dipping sauce (4) Vietnamese canh chua cá lóc, sweet sour snakehead fish soup with tomato, pineapple and herbs. Bottom row left to right (5) Vietnamese iced trà tắc, iced black tea with kumquat slices in a clear tumbler (6) Vietnamese đậu hũ kho nấm, golden braised tofu cubes with straw mushrooms and scallions in ceramic plate (7) chè đậu xanh cốt dừa, warm yellow split mung bean dessert soup with a ribbon of white coconut milk (8) thịt kho trứng, Vietnamese caramelized braised pork belly with whole braised eggs in a brown glazed bowl. Editorial real food photography, appetizing, warm natural daylight, tonal variety, cooked food texture, close overhead or 45-degree angle, cream and dark wood surfaces, high culinary accuracy. Each tile is a separate full-sized scene. Exclude any written text, diagrams, captions, marks, branding, collage borders, or human figures.

</details>
