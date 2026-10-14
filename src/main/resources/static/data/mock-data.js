// ============================================================
// MOCK DATA — dữ liệu mẫu để frontend hoạt động
// Sau này sẽ thay bằng dữ liệu thật từ MQTT / Backend
// ============================================================

// --- Dữ liệu cảm biến thời gian thực (dashboard cards) ---
// Đây là giá trị hiện tại của 3 loại cảm biến
const currentSensor = {
    light: 523,
    temperature: 28.5,
    humidity: 67,
    lastUpdated: "21:37:00"
};

// --- Danh sách thiết bị ---
// status là trạng thái THỰC TẾ mà thiết bị phản hồi (ON / OFF)
const devices = [
    { id: 1, name: "Light 1", icon: "light", status: "ON" },
    { id: 2, name: "Light 2", icon: "light", status: "OFF" }
];

// --- Lịch sử điều khiển thiết bị ---
// sender: người gửi / thực thi lệnh
// action: lệnh người dùng GỬI ĐI
// status: trạng thái THỰC TẾ thiết bị PHẢN HỒI
const deviceHistory = [
    { id: 26, deviceName: "Light 1", sender: "Phùng Văn Hải", action: "ON", status: "PENDING", time: "2026-08-13 21:32:00" },
    { id: 1, deviceName: "Light 1", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 21:30:21" },
    { id: 2, deviceName: "Light 2", sender: "Phùng Văn Hải", action: "OFF", status: "OFF", time: "2026-08-13 21:29:10" },
    { id: 3, deviceName: "Light 1", sender: "Admin", action: "ON", status: "OFF", time: "2026-08-13 21:27:45" },
    { id: 4, deviceName: "Light 2", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 21:25:00" },
    { id: 5, deviceName: "Light 1", sender: "Admin", action: "OFF", status: "OFF", time: "2026-08-13 21:22:33" },
    { id: 6, deviceName: "Light 2", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 21:20:15" },
    { id: 7, deviceName: "Light 1", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 21:18:50" },
    { id: 8, deviceName: "Light 2", sender: "Admin", action: "OFF", status: "OFF", time: "2026-08-13 21:15:05" },
    { id: 9, deviceName: "Light 1", sender: "Phùng Văn Hải", action: "OFF", status: "OFF", time: "2026-08-13 21:12:30" },
    { id: 10, deviceName: "Light 2", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 21:10:00" },
    { id: 11, deviceName: "Light 1", sender: "Admin", action: "ON", status: "ON", time: "2026-08-13 21:07:44" },
    { id: 12, deviceName: "Light 2", sender: "Phùng Văn Hải", action: "OFF", status: "ON", time: "2026-08-13 21:05:20" },
    { id: 13, deviceName: "Light 1", sender: "Phùng Văn Hải", action: "OFF", status: "OFF", time: "2026-08-13 21:02:00" },
    { id: 14, deviceName: "Light 2", sender: "Admin", action: "ON", status: "OFF", time: "2026-08-13 20:59:45" },
    { id: 15, deviceName: "Light 1", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 20:57:10" },
    { id: 16, deviceName: "Light 2", sender: "Phùng Văn Hải", action: "OFF", status: "OFF", time: "2026-08-13 20:54:33" },
    { id: 17, deviceName: "Light 1", sender: "Admin", action: "OFF", status: "OFF", time: "2026-08-13 20:52:00" },
    { id: 18, deviceName: "Light 2", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 20:49:15" },
    { id: 19, deviceName: "Light 1", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 20:46:50" },
    { id: 20, deviceName: "Light 2", sender: "Admin", action: "OFF", status: "OFF", time: "2026-08-13 20:44:00" },
    { id: 21, deviceName: "Light 1", sender: "Phùng Văn Hải", action: "OFF", status: "OFF", time: "2026-08-13 20:41:22" },
    { id: 22, deviceName: "Light 2", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 20:38:00" },
    { id: 23, deviceName: "Light 1", sender: "Admin", action: "ON", status: "OFF", time: "2026-08-13 20:35:30" },
    { id: 24, deviceName: "Light 2", sender: "Phùng Văn Hải", action: "OFF", status: "OFF", time: "2026-08-13 20:32:10" },
    { id: 25, deviceName: "Light 1", sender: "Phùng Văn Hải", action: "ON", status: "ON", time: "2026-08-13 20:29:45" },
];

// --- Lịch sử dữ liệu cảm biến ---
// Tạo nhiều bản ghi để test search, filter, sort, pagination
const sensorData = generateSensorHistory();

function generateSensorHistory() {
    const data = [];
    let id = 1;

    // Tạo dữ liệu cho 3 ngày 
    const days = ["2026-09-20", "2026-09-21", "2026-09-22"];
    const types = [
        { type: "Temperature", unit: "°C", min: 25, max: 35, step: 0.1 },
        { type: "Humidity", unit: "%", min: 50, max: 90, step: 1 },
        { type: "Light", unit: "Lux", min: 100, max: 800, step: 1 }
    ];

    days.forEach(day => {
        // Mỗi ngày tạo bản ghi cho mỗi giờ
        for (let hour = 8; hour <= 21; hour++) {
            for (let min = 0; min < 60; min += 15) {
                const timeStr = `${day} ${String(hour).padStart(2, "0")}:${String(min).padStart(2, "0")}:${String(Math.floor(Math.random() * 60)).padStart(2, "0")}`;
                types.forEach(sensor => {
                    const range = sensor.max - sensor.min;
                    let value = sensor.min + Math.random() * range;
                    if (sensor.step === 0.1) {
                        value = Math.round(value * 10) / 10;
                    } else {
                        value = Math.round(value);
                    }
                    data.push({
                        id: id++,
                        type: sensor.type,
                        value: value,
                        unit: sensor.unit,
                        time: timeStr
                    });
                });
            }
        }
    });

    return data;
}

// --- Dữ liệu biểu đồ ---
// 20 điểm dữ liệu gần nhất để vẽ chart
const chartData = generateChartData();

function generateChartData() {
    const points = [];
    const now = new Date();
    for (let i = 19; i >= 0; i--) {
        const t = new Date(now.getTime() - i * 3000); // mỗi điểm cách 3 giây
        points.push({
            time: `${String(t.getHours()).padStart(2, "0")}:${String(t.getMinutes()).padStart(2, "0")}:${String(t.getSeconds()).padStart(2, "0")}`,
            temperature: Math.round((27 + Math.random() * 5) * 10) / 10,
            humidity: Math.round(60 + Math.random() * 20),
            light: Math.round(400 + Math.random() * 300)
        });
    }
    return points;
}



