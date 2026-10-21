// ============================================================
// DỮ LIỆU KHỞI TẠO BAN ĐẦU
// (Hệ thống kết nối 100% dữ liệu thực tế từ Spring Boot REST API & MySQL)
// ============================================================

// 1. Trạng thái khởi tạo thẻ cảm biến Dashboard
const currentSensor = {
    light: null,
    temperature: null,
    humidity: null,
    lastUpdated: "--:--:--"
};

// 2. Danh sách thiết bị mặc định
const devices = [
    { id: 1, name: "Light 1", icon: "light", status: "OFF" },
    { id: 2, name: "Light 2", icon: "light", status: "OFF" }
];

// 3. Khởi tạo mảng rỗng (Toàn bộ dữ liệu hiển thị nạp động từ Backend API)
const deviceHistory = [];
const sensorData    = [];
const chartData     = [];
