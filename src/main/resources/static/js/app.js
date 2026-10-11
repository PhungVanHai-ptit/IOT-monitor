// ============================================================
// APP.JS — Logic chung dùng chung cho tất cả 4 trang HTML
// - Đồng hồ header
// - Mobile sidebar toggle
// - Toast notifications
// ============================================================

// Khi DOM đã sẵn sàng
document.addEventListener("DOMContentLoaded", function () {
    renderHeader();
    renderSidebar();
    initClock();
    initMobileSidebar();
});

// --- RENDER COMPONENT: HEADER ---
function renderHeader() {
    const header = document.getElementById("app-header") || document.querySelector(".app-header");
    if (!header) return;

    header.innerHTML = `
        <button id="hamburger-btn" class="hamburger-btn" aria-label="Toggle sidebar">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
                <line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/>
            </svg>
        </button>
        <div class="header-brand">
            <div class="header-logo">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                    <rect x="9" y="9" width="6" height="6" rx="1"/><rect x="2" y="2" width="20" height="20" rx="3"/>
                    <line x1="9" y1="2" x2="9" y2="5"/><line x1="15" y1="2" x2="15" y2="5"/>
                    <line x1="9" y1="19" x2="9" y2="22"/><line x1="15" y1="19" x2="15" y2="22"/>
                    <line x1="2" y1="9" x2="5" y2="9"/><line x1="2" y1="15" x2="5" y2="15"/>
                    <line x1="19" y1="9" x2="22" y2="9"/><line x1="19" y1="15" x2="22" y2="15"/>
                </svg>
            </div>
            <div>
                <div class="header-title">IoT Monitor</div>
                <div class="header-subtitle">Hệ thống Điều khiển Thông minh</div>
            </div>
        </div>
        <div class="header-right">
            <span id="header-clock" class="header-time">--:--:--</span>
            <div class="mqtt-status" id="header-mqtt-status">
                <span class="status-dot amber"></span>
                Đang kiểm tra MQTT...
            </div>
        </div>
    `;

    checkMqttStatus();
    setInterval(checkMqttStatus, 3000);
}

async function checkMqttStatus() {
    const el = document.getElementById("header-mqtt-status");
    if (!el) return;

    try {
        const res = await fetch("/api/mqtt/status");
        if (res.ok) {
            const data = await res.json();
            if (!data.brokerConnected) {
                el.innerHTML = `<span class="status-dot red"></span> Mất kết nối MQTT`;
            } else if (data.hardwareOnline) {
                el.innerHTML = `<span class="status-dot green pulse"></span> Mạch ESP32 Online`;
            } else {
                el.innerHTML = `<span class="status-dot amber"></span> MQTT OK (ESP32 Offline)`;
            }
        } else {
            el.innerHTML = `<span class="status-dot red"></span> Lỗi API MQTT`;
        }
    } catch (e) {
        el.innerHTML = `<span class="status-dot red"></span> Backend Offline`;
    }
}

// --- RENDER COMPONENT: SIDEBAR ---
function renderSidebar() {
    const sidebar = document.getElementById("app-sidebar") || document.querySelector(".app-sidebar");
    if (!sidebar) return;

    const path = window.location.pathname.toLowerCase();
    const isSensor  = path.includes("sensor.html");
    const isHistory = path.includes("device-history.html");
    const isProfile = path.includes("profile.html");
    const isDashboard = !isSensor && !isHistory && !isProfile;

    sidebar.innerHTML = `
        <nav class="sidebar-nav">
            <a class="nav-item ${isDashboard ? 'active' : ''}" href="index.html">
                <span class="nav-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/>
                        <rect x="3" y="14" width="7" height="7" rx="1"/><rect x="14" y="14" width="7" height="7" rx="1"/>
                    </svg>
                </span>
                <span>Dashboard</span>
                ${isDashboard ? '<span class="nav-dot"></span>' : ''}
            </a>
            <a class="nav-item ${isSensor ? 'active' : ''}" href="sensor.html">
                <span class="nav-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M22 12h-4l-3 9L9 3l-3 9H2"/>
                    </svg>
                </span>
                <span>Dữ liệu Cảm biến</span>
                ${isSensor ? '<span class="nav-dot"></span>' : ''}
            </a>
            <a class="nav-item ${isHistory ? 'active' : ''}" href="device-history.html">
                <span class="nav-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="12" r="10"/><polyline points="12,6 12,12 16,14"/>
                    </svg>
                </span>
                <span>Lịch sử Thiết bị</span>
                ${isHistory ? '<span class="nav-dot"></span>' : ''}
            </a>
            <a class="nav-item ${isProfile ? 'active' : ''}" href="profile.html">
                <span class="nav-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/>
                    </svg>
                </span>
                <span>Hồ sơ Cá nhân</span>
                ${isProfile ? '<span class="nav-dot"></span>' : ''}
            </a>
            <a class="nav-item" href="#" onclick="logoutUser(); return false;" style="margin-top: auto; border-top: 1px dashed var(--border); padding-top: 14px; color: var(--danger);">
                <span class="nav-icon">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>
                    </svg>
                </span>
                <span>Đăng xuất</span>
            </a>
        </nav>
        <div class="sidebar-footer">
            <div class="connection-badge">
                <span class="status-dot green"></span>
                Hệ thống Trực tuyến
            </div>
        </div>
    `;
}

// --- Đồng hồ thời gian thực ở Header ---
function initClock() {
    function updateClock() {
        const now = new Date();
        const h = String(now.getHours()).padStart(2, "0");
        const m = String(now.getMinutes()).padStart(2, "0");
        const s = String(now.getSeconds()).padStart(2, "0");
        const el = document.getElementById("header-clock");
        if (el) el.textContent = h + ":" + m + ":" + s;
    }
    updateClock();
    setInterval(updateClock, 1000);
}

// --- Mobile Sidebar Toggle ---
function initMobileSidebar() {
    const hamburger = document.getElementById("hamburger-btn");
    const overlay   = document.getElementById("sidebar-overlay");
    const sidebar   = document.querySelector(".app-sidebar");

    if (hamburger && sidebar) {
        hamburger.addEventListener("click", function () {
            sidebar.classList.toggle("open");
            if (overlay) overlay.classList.toggle("visible");
        });
    }

    if (overlay && sidebar) {
        overlay.addEventListener("click", function () {
            sidebar.classList.remove("open");
            overlay.classList.remove("visible");
        });
    }
}

// ============================================================
// TOAST NOTIFICATIONS — dùng chung trên tất cả các trang
// ============================================================

function showToast(type, message) {
    const container = document.getElementById("toast-container");
    if (!container) return;

    const icons = { success: "✓", error: "✕", info: "ℹ" };

    const toast = document.createElement("div");
    toast.className = "toast " + type;
    toast.innerHTML = `<span>${icons[type] || "ℹ"}</span><span>${message}</span>`;
    container.appendChild(toast);

    // Tự xóa sau 3 giây
    setTimeout(function () {
        toast.classList.add("removing");
        setTimeout(function () {
            if (toast.parentNode) toast.parentNode.removeChild(toast);
        }, 300);
    }, 3000);
}
