# IoT Monitor System

Hệ thống giám sát môi trường và điều khiển thiết bị thông minh sử dụng vi điều khiển **ESP32**, giao thức **MQTT**, backend **Spring Boot** và giao diện **Web Dashboard**.

---

## 1. Giới thiệu tổng quan

Dự án là giải pháp IoT toàn diện phục vụ việc thu thập, lưu trữ, hiển thị dữ liệu môi trường thời gian thực và điều khiển các thiết bị ngoại vi từ xa:
- **Thu thập dữ liệu môi trường**: Đo nhiệt độ, độ ẩm (cảm biến DHT11) và cường độ ánh sáng (cảm biến LDR).
- **Giao thức truyền thông**: Sử dụng MQTT Broker (kết nối nhanh, nhẹ, hỗ trợ cơ chế QoS và Retain).
- **Backend**: Spring Boot xử lý dữ liệu từ MQTT, lưu trữ vào MySQL và cung cấp RESTful API cho frontend.
- **Frontend Web Dashboard**: Giao diện trực quan, hiển thị widget trạng thái thời gian thực, biểu đồ trực quan (Chart.js), bộ lọc tìm kiếm và bảng điều khiển bật/tắt thiết bị.

---

## 2. Kiến trúc hệ thống

```text
[ Cảm biến & Thiết bị ] 
    DHT11, LDR, LED/Relay
              │
              ▼ (GPIO / ADC)
       [ ESP32 NodeMCU ]
              │
              ▼ (MQTT / WiFi)
       [ MQTT Broker ] (Mosquitto)
              │
              ▼ (MQTT Client / Paho)
    [ Spring Boot Backend ] ◄────► [ MySQL Database ]
              │
              ▼ (RESTful API / JSON)
    [ Web Dashboard Frontend ] (HTML/CSS/JS/Chart.js)
```

---

## 3. Yêu cầu môi trường (Prerequisites)

Trước khi cài đặt, máy tính cần cài sẵn các công cụ sau:
- **Java Development Kit (JDK)**: Phiên bản 21 trở lên.
- **MySQL Server**: Phiên bản 8.0 trở lên.
- **MQTT Broker**: Eclipse Mosquitto (hoặc EMQX / HiveMQ).
- **Maven**: 3.8+ (đã tích hợp sẵn `mvnw` trong dự án).
- **Arduino IDE**: (Để nạp code cho ESP32) cùng với các thư viện:
  - `DHT sensor library`
  - `PubSubClient`

---

## 4. Hướng dẫn cài đặt & Khởi chạy

### Bước 1: Clone dự án
```bash
git clone https://github.com/PhungVanHai-ptit/IOT-monitor.git
cd IOT-monitor
```

### Bước 2: Thiết lập cơ sở dữ liệu MySQL
1. Mở MySQL Workbench hoặc terminal MySQL và tạo database:
   ```sql
   CREATE DATABASE esp32_control CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. Cấu hình tài khoản và mật khẩu MySQL trong file:
   `src/main/resources/application.properties`
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/esp32_control?useSSL=false&serverTimezone=Asia/Ho_Chi_Minh&allowPublicKeyRetrieval=true&characterEncoding=UTF-8
   spring.datasource.username=root
   spring.datasource.password=YOUR_PASSWORD
   ```
   *(Hibernate sẽ tự động tạo bảng khi backend khởi chạy nhờ cấu hình `ddl-auto=update`)*.

### Bước 3: Khởi động MQTT Broker
Khởi động Mosquitto Broker trên máy (mặc định cổng 1885 theo cấu hình):
```bash
mosquitto -p 1885 -v
```
*(Hoặc điều chỉnh cổng và thông tin xác thực tương ứng trong `application.properties`)*.

### Bước 4: Chạy Backend Spring Boot
- **Sử dụng dòng lệnh (Terminal)**:
  ```bash
  # Windows
  ./mvnw.cmd spring-boot:run

  # Linux / macOS
  ./mvnw spring-boot:run
  ```
- **Hoặc chạy bằng IDE**: Mở thư mục dự án bằng IntelliJ IDEA / VS Code / Eclipse và chạy class `IotMonitorApplication.java`.

### Bước 5: Truy cập giao diện Web Dashboard
Mở trình duyệt web và truy cập:
```
http://localhost:8080
```
Các trang chức năng chính:
- **Tổng quan (Dashboard)**: `http://localhost:8080/index.html` (Xem dữ liệu thời gian thực, biểu đồ, điều khiển bật/tắt thiết bị).
- **Lịch sử cảm biến**: `http://localhost:8080/sensor.html` (Xem, tìm kiếm, lọc, phân trang dữ liệu cảm biến).
- **Lịch sử thiết bị**: `http://localhost:8080/device-history.html` (Theo dõi nhật ký bật/tắt thiết bị).
- **Hồ sơ cá nhân**: `http://localhost:8080/profile.html` (Thông tin sinh viên, link báo cáo & tài liệu).

### Bước 6: Cấu hình và nạp code cho ESP32 (Phần cứng)
1. Mở file [esp32-code.ino](esp32-code.ino) bằng Arduino IDE.
2. Cập nhật thông tin WiFi và IP máy chủ chạy MQTT Broker:
   ```cpp
   const char* ssid = "Tên_WiFi_Của_Bạn";
   const char* pwd  = "Mật_Khẩu_WiFi";
   const char* mqtt_server = "IP_MAY_TINH_CHAY_BROKER"; // Ví dụ: 192.168.1.15
   const int   mqtt_port   = 1885;
   ```
3. Chọn board **ESP32 Dev Module** và cổng COM tương ứng, sau đó nhấn **Upload**.

---

## 5. Cấu trúc thư mục

```text
IOT-monitor/
├── src/
│   ├── main/
│   │   ├── java/com/ptit/iotmonitor/
│   │   │   ├── controller/      # REST API Controllers (Sensor, Device, User...)
│   │   │   ├── model/           # JPA Entities (SensorData, DeviceActionLog...)
│   │   │   ├── repository/      # Spring Data JPA Repositories
│   │   │   ├── service/         # Business Logic & MQTT Handlers
│   │   │   └── IotMonitorApplication.java
│   │   └── resources/
│   │       ├── application.properties
│   │       └── static/          # Giao diện Web (HTML, CSS, JS, Assets)
├── esp32-code.ino               # Mã nguồn firmware nạp cho ESP32
├── pom.xml                      # Cấu hình thư viện Maven
└── README.md                    # Hướng dẫn dự án
```

---

## 6. Danh sách API chính

| Phương thức | Endpoint | Mô tả |
| :--- | :--- | :--- |
| `GET` | `/api/sensors/latest` | Lấy giá trị cảm biến mới nhất |
| `GET` | `/api/sensors/chart` | Lấy dữ liệu cảm biến phục vụ vẽ biểu đồ |
| `GET` | `/api/sensors/history` | Lấy danh sách lịch sử đo (hỗ trợ phân trang, lọc) |
| `GET` | `/api/devices` | Lấy danh sách trạng thái các thiết bị |
| `POST` | `/api/devices/{id}/action` | Gửi lệnh bật/tắt thiết bị (kèm body `{ "action": "ON"/"OFF" }`) |
| `GET` | `/api/devices/history` | Lấy lịch sử thao tác bật/tắt thiết bị |

---

## Tác giả
- **Sinh viên**: Phùng Văn Hải
- **Học viện Công nghệ Bưu chính Viễn thông (PTIT)**
