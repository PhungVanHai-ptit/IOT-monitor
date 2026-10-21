// ============================================================
// DASHBOARD.JS — Xử lý màn hình Dashboard
// - Sensor Cards (Ánh sáng, Nhiệt độ, Độ ẩm)
// - Sensor Chart (Chart.js Line Chart - Thời gian thực)
// - Device Control (Bật/Tắt thiết bị thực tế qua MQTT & ESP32)
// - Real-time Sync: Tự động đồng bộ dữ liệu cảm biến từ Backend
// ============================================================

// --- Biến lưu trạng thái ---
let myChart = null;           // Chart.js instance
let chartHistory = [];        // Mảng điểm dữ liệu lịch sử cho chart
const MAX_CHART_POINTS = 20;  // Số điểm tối đa hiển thị trên chart

// Sao chép dữ liệu devices vào biến cục bộ để thay đổi status
let dashboardDevices = devices.map(function (d) {
    return { id: d.id, name: d.name, icon: d.icon, status: d.status };
});

// --- Tự khởi tạo khi trang index.html load xong ---
document.addEventListener("DOMContentLoaded", function () {
    initDashboard();
});

// --- Khởi tạo Dashboard ---
async function initDashboard() {
    // 1. Render điều khiển thiết bị
    renderDeviceControl();

    // 2. Lấy 20 điểm dữ liệu lịch sử thực tế từ CSDL để vẽ biểu đồ
    await loadChartHistoryFromBackend();

    // 3. Gọi API lấy danh sách thiết bị thực tế từ Backend
    await loadDevicesFromBackend();

    // 4. Gọi API lấy dữ liệu cảm biến mới nhất từ Backend
    await loadLatestSensorFromBackend();
}

async function loadChartHistoryFromBackend() {
    try {
        const res = await fetch("/api/sensors/chart?limit=20");
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data) && data.length > 0) {
                chartHistory = data;
            } else {
                chartHistory = [];
            }
        }
    } catch (err) {
        console.warn("Could not load chart history from backend:", err);
        chartHistory = [];
    }
    // Vẽ biểu đồ với dữ liệu nạp từ Database
    renderChart();
}

async function loadDevicesFromBackend() {
    try {
        const res = await fetch("/api/devices");
        if (res.ok) {
            const data = await res.json();
            if (Array.isArray(data) && data.length > 0) {
                dashboardDevices = data.map(function (d) {
                    return { id: d.id, name: d.name, icon: d.icon || "light", status: d.status || "OFF" };
                });
                renderDeviceControl();
            }
        }
    } catch (err) {
        console.warn("Could not load devices from backend, using default:", err);
    }
}

async function loadLatestSensorFromBackend() {
    try {
        const res = await fetch("/api/sensors/latest");
        if (res.ok) {
            const data = await res.json();
            updateSensorCard("light", data.light, data.lastUpdated);
            updateSensorCard("temperature", data.temperature, data.lastUpdated);
            updateSensorCard("humidity", data.humidity, data.lastUpdated);
        }
    } catch (err) {
        console.warn("Could not load latest sensor data, using default:", err);
        updateSensorCard("light", currentSensor.light, currentSensor.lastUpdated);
        updateSensorCard("temperature", currentSensor.temperature, currentSensor.lastUpdated);
        updateSensorCard("humidity", currentSensor.humidity, currentSensor.lastUpdated);
    }
}

// ============================================================
// SENSOR CARDS
// ============================================================

// Cập nhật một sensor card theo type (light / temperature / humidity)
function updateSensorCard(type, value, lastUpdated) {
    // Lấy các phần tử HTML theo ID
    const numEl = document.getElementById("card-" + type + "-value");
    const timeEl = document.getElementById("card-" + type + "-updated");

    const displayVal = (value !== null && value !== undefined) ? value : "--";

    // Kiểm tra giá trị có thay đổi không để tạo flash animation
    if (numEl) {
        const oldValue = numEl.textContent;
        numEl.textContent = displayVal;
        // Nếu giá trị thay đổi, thêm class flash (hiệu ứng nhấp nháy)
        if (oldValue !== String(displayVal) && displayVal !== "--") {
            numEl.classList.remove("flash");
            // Force reflow để reset animation
            void numEl.offsetWidth;
            numEl.classList.add("flash");
        }
    }

    if (timeEl) {
        timeEl.textContent = "Last updated: " + (lastUpdated || "--:--:--");
    }
}

// ============================================================
// SENSOR CHART (Chart.js)
// ============================================================

function renderChart() {
    const canvas = document.getElementById("sensor-chart");
    if (!canvas) return;

    // Nếu chart đã tồn tại, huỷ trước khi tạo mới
    if (myChart) {
        myChart.destroy();
        myChart = null;
    }

    const labels = chartHistory.map(function (p) { return p.time; });
    const tempData = chartHistory.map(function (p) { return p.temperature; });
    const humData = chartHistory.map(function (p) { return p.humidity; });
    const lightData = chartHistory.map(function (p) { return p.light; });

    myChart = new Chart(canvas, {
        type: "line",
        data: {
            labels: labels,
            datasets: [
                {
                    label: "Nhiệt độ (\u00b0C)",
                    data: tempData,
                    borderColor: "#0058be",
                    backgroundColor: "rgba(0,88,190,0.06)",
                    borderWidth: 2,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    tension: 0.4,
                    spanGaps: true,
                    yAxisID: "y"
                },
                {
                    label: "Độ ẩm (%)",
                    data: humData,
                    borderColor: "#0891b2",
                    backgroundColor: "rgba(8,145,178,0.06)",
                    borderWidth: 2,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    tension: 0.4,
                    spanGaps: true,
                    yAxisID: "y"
                },
                {
                    label: "Ánh sáng (Lux)",
                    data: lightData,
                    borderColor: "#d97706",
                    backgroundColor: "rgba(217,119,6,0.06)",
                    borderWidth: 2,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    tension: 0.4,
                    spanGaps: true,
                    yAxisID: "y1"
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: "index",
                intersect: false
            },
            plugins: {
                legend: {
                    position: "bottom",
                    labels: {
                        color: "#727785",
                        font: { size: 12, family: "'Inter', sans-serif" },
                        boxWidth: 160,
                        boxHeight: 20,
                        padding: 16,
                        usePointStyle: true,
                        pointStyle: "line"
                    }
                },
                tooltip: {
                    // Light theme tooltip
                    backgroundColor: "#ffffff",
                    borderColor: "#e2e8f0",
                    borderWidth: 1,
                    titleColor: "#727785",
                    bodyColor: "#191b23",
                    titleFont: { family: "'JetBrains Mono', monospace", size: 11 },
                    bodyFont: { family: "'JetBrains Mono', monospace", size: 12 },
                    padding: 10,
                    callbacks: {
                        label: function (context) {
                            const lbl = context.dataset.label;
                            if (lbl.indexOf("Nhiệt độ") !== -1) {
                                return " Nhiệt độ: " + context.parsed.y + "°C";
                            } else if (lbl.indexOf("Độ ẩm") !== -1) {
                                return " Độ ẩm: " + context.parsed.y + "%";
                            } else if (lbl.indexOf("Ánh sáng") !== -1) {
                                return " Ánh sáng: " + context.parsed.y + " Lux";
                            }
                            return " " + lbl + ": " + context.parsed.y;
                        }
                    }
                }
            },
            scales: {
                x: {
                    ticks: {
                        color: "#727785",
                        font: { family: "'JetBrains Mono', monospace", size: 10 },
                        maxTicksLimit: 8,
                        maxRotation: 0
                    },
                    grid: { display: false }
                },
                y: {
                    position: "left",
                    ticks: {
                        color: "#727785",
                        font: { family: "'JetBrains Mono', monospace", size: 10 }
                    },
                    grid: { color: "#e2e8f0" }
                },
                y1: {
                    position: "right",
                    ticks: {
                        color: "#d97706",
                        font: { family: "'JetBrains Mono', monospace", size: 10 }
                    },
                    grid: { display: false }
                }
            }
        }
    });
}

// ============================================================
// REAL-TIME SYNC (setInterval)
// Tự động đồng bộ dữ liệu cảm biến & trạng thái mạch ESP32 mỗi 2.5s
// ============================================================

let realtimeInterval = null;
let lastDataReceivedTime = Date.now();

function startRealtimeSync() {
    // Tránh tạo nhiều interval khi load lại
    if (realtimeInterval) return;

    lastDataReceivedTime = Date.now();

    // Khởi tạo interval đồng bộ dữ liệu (mỗi 2.5s)
    realtimeInterval = setInterval(async function () {
        let newLight, newTemp, newHum, timeStr;
        let isHardwareOnline = false;

        try {
            // 1. Kiểm tra trạng thái kết nối MQTT & mạch ESP32
            const statusRes = await fetch("/api/mqtt/status");
            if (statusRes.ok) {
                const statusData = await statusRes.json();
                isHardwareOnline = !!statusData.hardwareOnline;
            }

            // 2. Lấy dữ liệu cảm biến mới nhất từ Backend (ESP32 gửi lên DB)
            const res = await fetch("/api/sensors/latest");
            if (res.ok) {
                const data = await res.json();
                newLight = data.light;
                newTemp = data.temperature;
                newHum = data.humidity;
                timeStr = data.lastUpdated;
            } else {
                throw new Error("API not ok");
            }
        } catch (e) {
            // Backend offline
            isHardwareOnline = false;
        }

        // Cập nhật cảnh báo nếu mạch ESP32 offline
        const warningBanner = document.getElementById("connection-warning");
        if (warningBanner) {
            if (!isHardwareOnline) {
                warningBanner.style.display = "block";
                const bannerText = warningBanner.querySelector("span");
                if (bannerText) {
                    bannerText.innerHTML = "<strong>Cảnh báo:</strong> Mạch ESP32 đang Offline (Chưa gửi dữ liệu lên MQTT)! Dữ liệu bên dưới là dữ liệu lưu trong DB trước đó.";
                }
            } else {
                warningBanner.style.display = "none";
                lastDataReceivedTime = Date.now();
            }
        }

        if (newLight !== undefined) {
            // Cập nhật giá trị hiển thị trên các thẻ cảm biến
            updateSensorCard("light", newLight, timeStr);
            updateSensorCard("temperature", newTemp, timeStr);
            updateSensorCard("humidity", newHum, timeStr);

            // Thêm điểm mới vào biểu đồ nếu mạch online và thời gian khác điểm cuối
            if (isHardwareOnline) {
                const lastPoint = chartHistory[chartHistory.length - 1];
                if (!lastPoint || lastPoint.time !== timeStr) {
                    chartHistory.push({ time: timeStr, temperature: newTemp, humidity: newHum, light: newLight });
                    if (chartHistory.length > MAX_CHART_POINTS) {
                        chartHistory.shift();
                    }

                    if (myChart) {
                        myChart.data.labels = chartHistory.map(function (p) { return p.time; });
                        myChart.data.datasets[0].data = chartHistory.map(function (p) { return p.temperature; });
                        myChart.data.datasets[1].data = chartHistory.map(function (p) { return p.humidity; });
                        myChart.data.datasets[2].data = chartHistory.map(function (p) { return p.light; });
                        myChart.update("none");
                    }
                }
            }
        }

        // 3. Đồng bộ trạng thái thiết bị thời gian thực (check trùng thông minh)
        await syncDevicesRealtime();
    }, 2000);
}

// Đồng bộ trạng thái thiết bị thực tế định kỳ (kèm kiểm tra trùng để chống nháy DOM và bảo vệ loading)
async function syncDevicesRealtime() {
    // 1. Nếu có thiết bị nào đang trong quá trình gửi lệnh (SENDING / Loading) thì bỏ qua tick này
    const isAnySending = dashboardDevices.some(function (d) { return d.status === "SENDING"; });
    if (isAnySending) return;

    try {
        const res = await fetch("/api/devices");
        if (!res.ok) return;
        const newDevices = await res.json();
        if (!Array.isArray(newDevices)) return;

        let hasChanged = false;

        // 2. So sánh trạng thái từng thiết bị giữa DB và UI hiện tại
        newDevices.forEach(function (newDev) {
            const currentDev = dashboardDevices.find(function (d) { return d.id === newDev.id; });
            if (currentDev) {
                if (currentDev.status !== newDev.status) {
                    currentDev.status = newDev.status || "OFF";
                    hasChanged = true;
                }
                if (currentDev.name !== newDev.name) {
                    currentDev.name = newDev.name;
                    hasChanged = true;
                }
            } else {
                dashboardDevices.push({
                    id: newDev.id,
                    name: newDev.name,
                    icon: newDev.icon || "light",
                    status: newDev.status || "OFF"
                });
                hasChanged = true;
            }
        });

        if (dashboardDevices.length !== newDevices.length) {
            dashboardDevices = newDevices.map(function (d) {
                return { id: d.id, name: d.name, icon: d.icon || "light", status: d.status || "OFF" };
            });
            hasChanged = true;
        }

        // 3. CHỈ VẼ LẠI GIAO DIỆN KHI THỰC SỰ CÓ THAY ĐỔI TRẠNG THÁI
        if (hasChanged) {
            renderDeviceControl();
        }
    } catch (err) {
        // Bỏ qua lỗi mạng im lặng
    }
}

// Bắt đầu đồng bộ dữ liệu thời gian thực khi tải trang xong
window.addEventListener("load", function () {
    startRealtimeSync();
});

// ============================================================
// DEVICE CONTROL
// ============================================================

// Render danh sách thiết bị vào UI
function renderDeviceControl() {
    const container = document.getElementById("device-list");
    if (!container) return;

    container.innerHTML = "";

    // Đếm số thiết bị đang ON để hiển thị
    const onCount = dashboardDevices.filter(function (d) { return d.status === "ON"; }).length;
    const countEl = document.getElementById("device-active-count");
    if (countEl) countEl.textContent = onCount + "/" + dashboardDevices.length + " đang bật";

    // Tạo HTML cho từng thiết bị
    dashboardDevices.forEach(function (device) {
        const item = createDeviceItem(device);
        container.appendChild(item);
    });
}

// SVG icons cho từng loại thiết bị (icon đơn sắc)
const DEVICE_SVG_ICONS = {
    light: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <line x1="9" y1="18" x2="15" y2="18"/><line x1="10" y1="22" x2="14" y2="22"/>
        <path d="M15.09 14c.18-.98.65-1.74 1.41-2.5A4.65 4.65 0 0 0 18 8 6 6 0 0 0 6 8c0 1 .23 2.23 1.5 3.5A4.61 4.61 0 0 1 8.91 14"/>
    </svg>`,
    fan: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M12 12c2-2.96 0-7-1-8 0 3.038-1.773 4.741-3 6 1.226 1.26 2.826 2 4 2z"/>
        <path d="M12 12c-2.96-2 0-7 1-8 0 3.038 1.773 4.741 3 6-1.226 1.26-2.826 2-4 2z"/>
        <path d="M12 12c2.96 2 0 7-1 8 0-3.038-1.773-4.741-3-6 1.226-1.26 2.826-2 4-2z"/>
        <path d="M12 12c-2 2.96 0 7 1 8 0-3.038 1.773-4.741 3-6-1.226-1.26-2.826-2-4-2z"/>
        <circle cx="12" cy="12" r="2"/>
    </svg>`
};

// Tạo một device item element
function createDeviceItem(device) {
    const isOn = device.status === "ON";
    const isSending = device.status === "SENDING";
    const loadingAction = device.loadingAction; // 'ON' hoặc 'OFF'

    // Lấy SVG icon theo loại thiết bị
    const svgIcon = DEVICE_SVG_ICONS[device.icon] || DEVICE_SVG_ICONS.light;

    const div = document.createElement("div");
    div.className = "device-item";
    div.id = "device-item-" + device.id;

    // Xác định class và text cho status
    let statusClass = "off";
    let statusText = "TẮT";
    if (isOn) { statusClass = "on"; statusText = "BẬT"; }
    if (isSending) { statusClass = "sending"; statusText = "Đang xử lý..."; }

    const isOnLoading = isSending && loadingAction === "ON";
    const isOffLoading = isSending && loadingAction === "OFF";

    const onBtnContent = isOnLoading ? `<span class="btn-spinner"></span>Đang bật...` : "ON";
    const offBtnContent = isOffLoading ? `<span class="btn-spinner"></span>Đang tắt...` : "OFF";

    div.innerHTML = `
        <div class="device-icon ${isOn ? 'on' : ''}">${svgIcon}</div>
        <div class="device-info">
            <div class="device-name">${device.name}</div>
            <span class="device-status-label ${statusClass}" id="status-label-${device.id}">
                ${isSending ? '<span class="status-dot amber pulse"></span>' : ''}
                ${statusText}
            </span>
        </div>
        <div class="device-buttons">
            <button
                class="btn-on ${isOn ? 'active-state' : ''} ${isOnLoading ? 'is-loading' : ''}"
                id="btn-on-${device.id}"
                onclick="sendDeviceCommand(${device.id}, 'ON')"
                ${isSending || isOn ? "disabled" : ""}
            >${onBtnContent}</button>
            <button
                class="btn-off ${!isOn && !isSending ? 'active-state' : ''} ${isOffLoading ? 'is-loading' : ''}"
                id="btn-off-${device.id}"
                onclick="sendDeviceCommand(${device.id}, 'OFF')"
                ${isSending || !isOn ? "disabled" : ""}
            >${offBtnContent}</button>
        </div>
    `;

    return div;
}

// ============================================================
// DEVICE COMMAND FLOW
// ============================================================

async function sendDeviceCommand(deviceId, action) {
    // 1. Tìm thiết bị trong mảng
    const device = dashboardDevices.find(function (d) { return d.id === deviceId; });
    if (!device) return;

    // 2. Đặt trạng thái SENDING và ghi nhận nút đang loading
    const prevStatus = device.status;
    device.status = "SENDING";
    device.loadingAction = action;
    renderDeviceControl();
    showToast("info", "Đang gửi lệnh " + action + " tới " + device.name + "...");

    // Thêm delay nửa giây (500ms) tạo hiệu ứng phản hồi mượt mà
    await new Promise(function (resolve) { setTimeout(resolve, 500); });

    // Lấy user hiện tại từ localStorage
    let currentUser = null;
    try {
        currentUser = JSON.parse(localStorage.getItem("iot_logged_in_user"));
    } catch (e) { }

    try {
        const response = await fetch(`/api/devices/${deviceId}/action`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                action: action,
                userId: currentUser ? currentUser.userId : 1
            })
        });

        if (response.ok) {
            const data = await response.json();
            device.status = (data.status === "OFF") ? "OFF" : (data.status === "ON" ? "ON" : action);
            showToast("success", device.name + " đã " + (device.status === "ON" ? "BẬT" : "TẮT") + " thành công!");

            // Thêm vào device history cục bộ
            addToDeviceHistory(device, action, "SUCCESS");
        } else {
            let errorMsg = "Lỗi khi điều khiển thiết bị (Mã lỗi " + response.status + ")";
            try {
                const errData = await response.json();
                if (errData && errData.message) {
                    errorMsg = errData.message;
                }
            } catch (e) { }
            addToDeviceHistory(device, action, "FAILED");
            throw new Error(errorMsg);
        }
    } catch (err) {
        console.error("Device command failed:", err);
        // Khôi phục trạng thái ban đầu của thiết bị
        device.status = prevStatus;
        // Báo lỗi rõ ràng cho người dùng, tuyệt đối không báo thành công giả
        showToast("error", err.message || "Không thể điều khiển thiết bị! Vui lòng kiểm tra MQTT và ESP32.");
    } finally {
        delete device.loadingAction;
        // Cập nhật lại UI
        renderDeviceControl();
    }
}

// Thêm bản ghi vào device history
function addToDeviceHistory(device, action, status) {
    const now = new Date();
    const timeStr = now.getFullYear() + "-" +
        String(now.getMonth() + 1).padStart(2, "0") + "-" +
        String(now.getDate()).padStart(2, "0") + " " +
        String(now.getHours()).padStart(2, "0") + ":" +
        String(now.getMinutes()).padStart(2, "0") + ":" +
        String(now.getSeconds()).padStart(2, "0");

    const newEntry = {
        id: deviceHistory.length + 1,
        deviceName: device.name,
        action: action,
        status: status,
        time: timeStr
    };

    // Thêm vào đầu mảng để entry mới nhất hiện ở trên cùng
    deviceHistory.unshift(newEntry);
}
