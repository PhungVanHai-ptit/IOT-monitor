// ============================================================
// DEVICE-HISTORY.JS — Xử lý màn hình Device History
// - Hiển thị lịch sử điều khiển thiết bị
// - Search theo tên thiết bị hoặc ID
// - Sort theo nhiều tiêu chí
// - Pagination
// ============================================================

// --- Biến state cho trang Device History ---
let historyCurrentPage = 1;
let historyPageSize    = 10;
let historySearch      = "";
let historySort        = "time-desc";
let historyDeviceFilter = "all";
let historyActionFilter = "all";
let historyStatusFilter = "all";

// --- Tự khởi tạo khi trang device-history.html load xong ---
document.addEventListener("DOMContentLoaded", function () {
    initDeviceHistoryPage();
});

// --- Khởi tạo trang Device History ---
function initDeviceHistoryPage() {
    historyCurrentPage = 1;

    // Gắn sự kiện cho input search & nút tìm kiếm
    const searchInput = document.getElementById("history-search");
    const searchBtn   = document.getElementById("btn-history-search");

    function executeHistorySearch() {
        if (searchInput) {
            historySearch = searchInput.value;
            historyCurrentPage = 1;
            renderHistoryTable();
        }
    }

    if (searchInput) {
        // Nhấn Enter để tìm kiếm
        searchInput.addEventListener("keydown", function (e) {
            if (e.key === "Enter") {
                e.preventDefault();
                executeHistorySearch();
            }
        });

        // Khi người dùng xóa trắng ô input -> tự động cập nhật lại bảng
        searchInput.addEventListener("input", function () {
            if (searchInput.value.trim() === "" && historySearch !== "") {
                historySearch = "";
                historyCurrentPage = 1;
                renderHistoryTable();
            }
        });
    }

    if (searchBtn) {
        searchBtn.addEventListener("click", function () {
            executeHistorySearch();
        });
    }

    // Gắn sự kiện cho dropdown lọc Thiết bị
    const deviceSelect = document.getElementById("history-device-filter");
    if (deviceSelect) {
        deviceSelect.addEventListener("change", function () {
            historyDeviceFilter = deviceSelect.value;
            historyCurrentPage = 1;
            renderHistoryTable();
        });
    }

    // Gắn sự kiện cho dropdown lọc Action (Lệnh gửi)
    const actionSelect = document.getElementById("history-action-filter");
    if (actionSelect) {
        actionSelect.addEventListener("change", function () {
            historyActionFilter = actionSelect.value;
            historyCurrentPage = 1;
            renderHistoryTable();
        });
    }

    // Gắn sự kiện cho dropdown lọc Trạng thái (Status phản hồi)
    const statusSelect = document.getElementById("history-status-filter");
    if (statusSelect) {
        statusSelect.addEventListener("change", function () {
            historyStatusFilter = statusSelect.value;
            historyCurrentPage = 1;
            renderHistoryTable();
        });
    }

    // Gắn sự kiện cho dropdown sort
    const sortSelect = document.getElementById("history-sort");
    if (sortSelect) {
        sortSelect.addEventListener("change", function () {
            historySort = sortSelect.value;
            historyCurrentPage = 1;
            renderHistoryTable();
        });
    }

    // Gắn sự kiện cho chọn số bản ghi mỗi trang
    const perPageSel = document.getElementById("history-per-page");
    if (perPageSel) {
        perPageSel.addEventListener("change", function () {
            historyPageSize    = parseInt(perPageSel.value, 10);
            historyCurrentPage = 1;
            renderHistoryTable();
        });
    }

    // Tải danh sách thiết bị thật từ cơ sở dữ liệu vào dropdown lọc
    loadDeviceFilterOptions();

    // Render bảng lần đầu
    renderHistoryTable();
}

// Tải danh sách thiết bị thực tế từ CSDL để hiển thị lên dropdown lọc
async function loadDeviceFilterOptions() {
    const deviceSelect = document.getElementById("history-device-filter");
    if (!deviceSelect) return;

    try {
        const res = await fetch("/api/devices");
        if (res.ok) {
            const devicesList = await res.json();
            if (Array.isArray(devicesList) && devicesList.length > 0) {
                const currentVal = historyDeviceFilter || "all";
                deviceSelect.innerHTML = `<option value="all">Tất cả thiết bị</option>`;
                devicesList.forEach(function (dev) {
                    const opt = document.createElement("option");
                    opt.value = dev.name;
                    opt.textContent = dev.name;
                    deviceSelect.appendChild(opt);
                });
                if (currentVal && currentVal !== "all") {
                    deviceSelect.value = currentVal;
                }
            }
        }
    } catch (err) {
        console.warn("Could not load devices from backend for filter:", err);
    }
}

// ============================================================
// FILTER & SORT
// ============================================================

function getFilteredHistoryData() {
    // Lấy dữ liệu từ mảng deviceHistory (định nghĩa trong mock-data.js)
    let data = deviceHistory.slice();

    // 1. Tìm kiếm theo Thời gian (Time)
    if (historySearch.trim() !== "") {
        const query = historySearch.trim().toLowerCase();
        data = data.filter(function (row) {
            return row.time.toLowerCase().includes(query);
        });
    }

    // 2. Lọc theo Thiết bị (Device)
    if (historyDeviceFilter !== "all") {
        const queryDevice = historyDeviceFilter.toLowerCase();
        data = data.filter(function (row) {
            return (row.deviceName && row.deviceName.toLowerCase().includes(queryDevice)) ||
                   (String(row.id) === historyDeviceFilter);
        });
    }

    // 3. Lọc theo Action (Lệnh gửi)
    if (historyActionFilter !== "all") {
        data = data.filter(function (row) {
            return row.action === historyActionFilter;
        });
    }

    // 4. Lọc theo Trạng thái (Status phản hồi: SUCCESS / FAILED / PENDING)
    if (historyStatusFilter !== "all") {
        const filterUpper = historyStatusFilter.toUpperCase();
        data = data.filter(function (row) {
            const rowStatus = (row.status || "").toUpperCase();
            const rowAction = (row.action || "").toUpperCase();

            if (filterUpper === "PENDING") {
                return rowStatus === "PENDING";
            } else if (filterUpper === "SUCCESS") {
                return rowStatus === "SUCCESS" || (rowStatus === rowAction && rowStatus !== "PENDING");
            } else if (filterUpper === "FAILED") {
                return rowStatus === "FAILED" || (rowStatus !== rowAction && rowStatus !== "PENDING");
            }
            return rowStatus === filterUpper;
        });
    }

    // 5. Sắp xếp
    data = sortHistoryData(data, historySort);

    return data;
}

function sortHistoryData(data, sortKey) {
    return data.sort(function (a, b) {
        switch (sortKey) {
            case "time-desc":
                return b.time.localeCompare(a.time) || (b.id - a.id);
            case "time-asc":
                return a.time.localeCompare(b.time) || (a.id - b.id);
            case "name-asc":
                return a.deviceName.localeCompare(b.deviceName) || (b.id - a.id);
            case "name-desc":
                return b.deviceName.localeCompare(a.deviceName) || (b.id - a.id);
            case "action-on":
                return ((b.action === "ON" ? 1 : 0) - (a.action === "ON" ? 1 : 0)) || (b.id - a.id);
            case "action-off":
                return ((a.action === "ON" ? 1 : 0) - (b.action === "ON" ? 1 : 0)) || (b.id - a.id);
            case "status-success":
            case "status-on":
                return ((b.status === "SUCCESS" || b.status === b.action ? 1 : 0) - (a.status === "SUCCESS" || a.status === a.action ? 1 : 0)) || (b.id - a.id);
            case "status-failed":
            case "status-off":
                return ((b.status === "FAILED" || (b.status !== b.action && b.status !== "PENDING") ? 1 : 0) -
                       (a.status === "FAILED" || (a.status !== a.action && a.status !== "PENDING") ? 1 : 0)) || (b.id - a.id);
            default:
                return (b.id - a.id);
        }
    });
}

// ============================================================
// RENDER TABLE — Vẽ bảng lịch sử thiết bị
// ============================================================

async function renderHistoryTable() {
    const tbody = document.getElementById("history-table-body");
    if (!tbody) return;

    let pagedData = [];
    let totalItems = 0;

    try {
        const queryParams = new URLSearchParams({
            page: historyCurrentPage,
            size: historyPageSize,
            search: historySearch,
            deviceFilter: historyDeviceFilter,
            actionFilter: historyActionFilter,
            statusFilter: historyStatusFilter,
            sort: historySort
        });

        const res = await fetch(`/api/devices/logs?${queryParams.toString()}`);
        if (res.ok) {
            const data = await res.json();
            pagedData = data.content || [];
            totalItems = data.totalElements || 0;
        } else {
            throw new Error("HTTP " + res.status);
        }
    } catch (err) {
        console.warn("Backend API not reachable, fallback to mock history data:", err);
        const filteredData = getFilteredHistoryData();
        totalItems = filteredData.length;
        const startIndex = (historyCurrentPage - 1) * historyPageSize;
        const endIndex = startIndex + historyPageSize;
        pagedData = filteredData.slice(startIndex, endIndex);
    }

    // Xóa bảng cũ
    tbody.innerHTML = "";

    if (pagedData.length === 0) {
        tbody.innerHTML = `<tr><td colspan="6" class="table-empty">Không tìm thấy lịch sử thiết bị</td></tr>`;
    } else {
        pagedData.forEach(function (row) {
            const tr = document.createElement("tr");

            // Xác định badge cho action
            const actionBadge = getActionBadge(row.action);

            // Xác định badge cho status
            const statusBadge = getStatusBadge(row.action, row.status);

            tr.innerHTML = `
                <td class="id-col">${String(row.id).padStart(3, "0")}</td>
                <td style="font-weight:600; color: var(--text-primary)">${row.deviceName}</td>
                <td style="color: var(--text-secondary)">${row.sender || "Admin"}</td>
                <td>${actionBadge}</td>
                <td>${statusBadge}</td>
                <td class="time-col">${row.time}</td>
            `;
            tbody.appendChild(tr);
        });
    }

    // Cập nhật pagination
    renderHistoryPagination(totalItems);
}

// Badge cho Action (lệnh gửi đi)
function getActionBadge(action) {
    const cls = action === "ON" ? "badge-on" : "badge-off";
    return `<span class="badge ${cls}">${action}</span>`;
}

// Badge cho Status (thiết bị phản hồi: SUCCESS, FAILED, PENDING)
function getStatusBadge(action, status) {
    const s = (status || "").toUpperCase();
    const a = (action || "").toUpperCase();

    // 1. Trạng thái PENDING (đang chờ)
    if (s === "PENDING") {
        return `<span class="badge badge-pending"><span class="status-dot amber pulse"></span>PENDING</span>`;
    }

    // 2. Trạng thái SUCCESS: BE trả về "SUCCESS", hoặc BE trả về "ON"/"OFF" khớp với action
    if (s === "SUCCESS" || (s === a && s !== "PENDING")) {
        return `<span class="badge badge-success"><span class="status-dot green"></span>SUCCESS</span>`;
    }

    // 3. Trạng thái FAILED: BE trả về "FAILED", hoặc BE trả về trạng thái không khớp action
    return `<span class="badge badge-failed"><span class="status-dot red"></span>FAILED</span>`;
}

// ============================================================
// PAGINATION — Tương tự sensor.js
// ============================================================

function renderHistoryPagination(totalItems) {
    const infoEl     = document.getElementById("history-pagination-info");
    const controlsEl = document.getElementById("history-pagination-controls");
    if (!infoEl || !controlsEl) return;

    const totalPages = Math.ceil(totalItems / historyPageSize) || 1;
    const start      = totalItems === 0 ? 0 : (historyCurrentPage - 1) * historyPageSize + 1;
    const end        = Math.min(historyCurrentPage * historyPageSize, totalItems);

    infoEl.textContent = "Hiển thị " + start + "–" + end + " trong tổng " + totalItems + " bản ghi";

    controlsEl.innerHTML = "";

    // Nút Previous
    const prevBtn = createHistoryPageButton("‹", historyCurrentPage <= 1, function () {
        historyCurrentPage--;
        renderHistoryTable();
    });
    controlsEl.appendChild(prevBtn);

    // Số trang
    const pageNumbers = getPageNumbers(historyCurrentPage, totalPages);
    pageNumbers.forEach(function (pg) {
        if (pg === "...") {
            const dots = document.createElement("button");
            dots.className = "page-btn dots";
            dots.textContent = "...";
            controlsEl.appendChild(dots);
        } else {
            const btn = createHistoryPageButton(pg, false, function () {
                historyCurrentPage = pg;
                renderHistoryTable();
            });
            if (pg === historyCurrentPage) btn.classList.add("active");
            controlsEl.appendChild(btn);
        }
    });

    // Nút Next
    const nextBtn = createHistoryPageButton("›", historyCurrentPage >= totalPages, function () {
        historyCurrentPage++;
        renderHistoryTable();
    });
    controlsEl.appendChild(nextBtn);
}

function createHistoryPageButton(label, isDisabled, onClick) {
    const btn = document.createElement("button");
    btn.className = "page-btn";
    btn.textContent = label;
    btn.disabled = isDisabled;
    if (!isDisabled) btn.addEventListener("click", onClick);
    return btn;
}

// Tính danh sách số trang cần hiển thị (với dấu "..." khi có nhiều trang)
function getPageNumbers(current, total) {
    if (total <= 7) {
        const pages = [];
        for (let i = 1; i <= total; i++) pages.push(i);
        return pages;
    }

    const pages = [];
    if (current <= 4) {
        for (let i = 1; i <= 5; i++) pages.push(i);
        pages.push("...");
        pages.push(total);
    } else if (current >= total - 3) {
        pages.push(1);
        pages.push("...");
        for (let i = total - 4; i <= total; i++) pages.push(i);
    } else {
        pages.push(1);
        pages.push("...");
        pages.push(current - 1);
        pages.push(current);
        pages.push(current + 1);
        pages.push("...");
        pages.push(total);
    }
    return pages;
}
