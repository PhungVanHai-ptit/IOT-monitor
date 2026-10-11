// ============================================================
// SENSOR.JS — Xử lý màn hình Data Sensor
// - Search theo giá trị cảm biến
// - Search theo thời gian (linh hoạt: năm → tháng → ngày → giờ → phút → giây)
// - Filter theo loại cảm biến (All / Temperature / Light / Humidity)
// - Sort (thời gian mới nhất/cũ nhất, giá trị cao/thấp)
// - Pagination (phân trang)
// ============================================================

// --- Biến state cho trang Sensor ---
let sensorCurrentPage   = 1;
let sensorPageSize      = 10;
let sensorSearchGlobal  = ""; // Từ khoá tìm kiếm
let sensorSearchTarget  = "all"; // Tiêu chí tìm kiếm: 'all' | 'time' | 'temperature' | 'humidity' | 'light'
let sensorSort          = "time-desc";

// --- Tự khởi tạo khi trang sensor.html load xong ---
document.addEventListener("DOMContentLoaded", function () {
    initSensorPage();
});

// --- Khởi tạo trang Sensor ---
function initSensorPage() {
    sensorCurrentPage = 1;

    // Gắn sự kiện cho các input/select
    const globalInput  = document.getElementById("sensor-search-global");
    const targetSelect = document.getElementById("sensor-search-target");
    const sortSelect   = document.getElementById("sensor-sort");
    const perPageSel   = document.getElementById("sensor-per-page");
    const searchTip    = document.getElementById("search-tip");

    // Thay đổi tiêu chí tìm kiếm trong Dropdown
    if (targetSelect) {
        targetSelect.addEventListener("change", function () {
            sensorSearchTarget = targetSelect.value;

            // Cập nhật placeholder và chú thích tương ứng
            if (globalInput) {
                switch (sensorSearchTarget) {
                    case "time":
                        globalInput.placeholder = "Nhập thời gian cần tìm (Ví dụ: 2026-09-22, 14:30)...";
                        break;
                    case "temperature":
                        globalInput.placeholder = "Nhập nhiệt độ cần tìm (°C) (Ví dụ: 28.5, 30)...";
                        break;
                    case "humidity":
                        globalInput.placeholder = "Nhập độ ẩm cần tìm (%) (Ví dụ: 65, 70)...";
                        break;
                    case "light":
                        globalInput.placeholder = "Nhập ánh sáng cần tìm (Lux) (Ví dụ: 450, 500)...";
                        break;
                    default:
                        globalInput.placeholder = "Tìm kiếm theo giá trị hoặc thời gian...";
                        break;
                }
            }

            if (searchTip) {
                switch (sensorSearchTarget) {
                    case "time":
                        searchTip.innerHTML = `Đang tìm theo: <strong>Thời gian</strong> (Ví dụ: <code>2026-09-22</code> hoặc <code>14:30:00</code>).`;
                        break;
                    case "temperature":
                        searchTip.innerHTML = `Đang tìm theo: <strong>Nhiệt độ</strong> (Ví dụ: <code>28.5</code>, <code>30.2</code>).`;
                        break;
                    case "humidity":
                        searchTip.innerHTML = `Đang tìm theo: <strong>Độ ẩm</strong> (Ví dụ: <code>65</code>, <code>70</code>).`;
                        break;
                    case "light":
                        searchTip.innerHTML = `Đang tìm theo: <strong>Ánh sáng</strong> (Ví dụ: <code>450</code>, <code>520</code>).`;
                        break;
                    default:
                        searchTip.innerHTML = `Tìm kiếm linh hoạt theo <strong>Tất cả tiêu chí</strong>.`;
                        break;
                }
            }

            sensorCurrentPage = 1;
            renderSensorTable();
        });
    }

    // Hàm thực hiện tìm kiếm
    function performSensorSearch() {
        if (globalInput) {
            sensorSearchGlobal = globalInput.value.trim();
            sensorCurrentPage = 1;
            renderSensorTable();
        }
    }

    // Nhấn phím Enter ở ô search để tìm kiếm
    if (globalInput) {
        globalInput.addEventListener("keydown", function (e) {
            if (e.key === "Enter") {
                e.preventDefault();
                performSensorSearch();
            }
        });
    }

    // Nhấn nút Tìm kiếm để tìm kiếm
    const searchBtn = document.getElementById("btn-sensor-search");
    if (searchBtn) {
        searchBtn.addEventListener("click", function () {
            performSensorSearch();
        });
    }

    if (sortSelect) {
        sortSelect.addEventListener("change", function () {
            sensorSort = sortSelect.value;
            sensorCurrentPage = 1;
            renderSensorTable();
        });
    }

    if (perPageSel) {
        perPageSel.addEventListener("change", function () {
            sensorPageSize    = parseInt(perPageSel.value, 10);
            sensorCurrentPage = 1;
            renderSensorTable();
        });
    }

    // Render bảng lần đầu
    renderSensorTable();
}

// ============================================================
// FILTER — Lọc dữ liệu cảm biến
// ============================================================

function getFilteredSensorData() {
    let data = sensorData.slice(); // sao chép mảng

    // 1. Lọc theo tiêu chí loại cảm biến nếu được chọn cụ thể
    if (sensorSearchTarget === "temperature") {
        data = data.filter(function (row) {
            return row.type.toLowerCase() === "temperature";
        });
    } else if (sensorSearchTarget === "humidity") {
        data = data.filter(function (row) {
            return row.type.toLowerCase() === "humidity";
        });
    } else if (sensorSearchTarget === "light") {
        data = data.filter(function (row) {
            return row.type.toLowerCase() === "light";
        });
    }

    // 2. Tìm kiếm theo từ khoá nhập
    if (sensorSearchGlobal !== "") {
        const query = sensorSearchGlobal;

        if (sensorSearchTarget === "time") {
            // Lọc theo thời gian
            data = data.filter(function (row) {
                return row.time.includes(query);
            });
        } else if (sensorSearchTarget === "temperature" || sensorSearchTarget === "humidity" || sensorSearchTarget === "light") {
            // Lọc CHỈ theo giá trị số của loại cảm biến đã chọn (không tìm theo thời gian)
            const valueQuery = query.replace(",", ".");
            data = data.filter(function (row) {
                return String(row.value).includes(valueQuery);
            });
        } else {
            // Tiêu chí 'all' (Tất cả): tìm theo thời gian hoặc giá trị
            const isTimeQuery = query.indexOf("-") !== -1 || query.indexOf(":") !== -1;
            if (isTimeQuery) {
                data = data.filter(function (row) {
                    return row.time.includes(query);
                });
            } else {
                const valueQuery = query.replace(",", ".");
                data = data.filter(function (row) {
                    return String(row.value).includes(valueQuery) || row.type.toLowerCase().includes(query.toLowerCase());
                });
            }
        }
    }

    // 3. Sort
    data = sortSensorData(data, sensorSort);

    return data;
}

// ============================================================
// SORT — Sắp xếp dữ liệu
// ============================================================

function sortSensorData(data, sortKey) {
    return data.sort(function (a, b) {
        switch (sortKey) {
            case "time-desc":
                // Mới nhất lên trên (so sánh chuỗi thời gian dạng ISO)
                return b.time.localeCompare(a.time);
            case "time-asc":
                // Cũ nhất lên trên
                return a.time.localeCompare(b.time);
            case "value-desc":
                // Giá trị cao xuống thấp
                return b.value - a.value;
            case "value-asc":
                // Giá trị thấp lên cao
                return a.value - b.value;
            default:
                return 0;
        }
    });
}

// ============================================================
// RENDER TABLE — Vẽ bảng dữ liệu cảm biến
// ============================================================

async function renderSensorTable() {
    const tbody = document.getElementById("sensor-table-body");
    if (!tbody) return;

    let pagedData = [];
    let totalItems = 0;

    try {
        const queryParams = new URLSearchParams({
            page: sensorCurrentPage,
            size: sensorPageSize,
            target: sensorSearchTarget,
            search: sensorSearchGlobal,
            sort: sensorSort
        });

        const res = await fetch(`/api/sensors/data?${queryParams.toString()}`);
        if (res.ok) {
            const data = await res.json();
            pagedData = data.content || [];
            totalItems = data.totalElements || 0;
        } else {
            throw new Error("HTTP " + res.status);
        }
    } catch (err) {
        console.warn("Backend API not reachable, fallback to mock data:", err);
        // Fallback sang mock data client-side
        const filteredData = getFilteredSensorData();
        totalItems = filteredData.length;
        const startIndex = (sensorCurrentPage - 1) * sensorPageSize;
        const endIndex = startIndex + sensorPageSize;
        pagedData = filteredData.slice(startIndex, endIndex);
    }

    // Xóa nội dung bảng cũ
    tbody.innerHTML = "";

    if (pagedData.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" class="table-empty">Không tìm thấy dữ liệu cảm biến</td></tr>`;
    } else {
        pagedData.forEach(function (row) {
            const tr = document.createElement("tr");

            const badgeClass = getSensorTypeBadgeClass(row.type);
            const displayName = getSensorTypeDisplayName(row.type);

            tr.innerHTML = `
                <td class="id-col">${String(row.id).padStart(3, "0")}</td>
                <td><span class="badge ${badgeClass}">${displayName}</span></td>
                <td class="value-cell">${row.value}<span class="unit">${row.unit}</span></td>
                <td class="time-col">${row.time}</td>
            `;
            tbody.appendChild(tr);
        });
    }

    // Cập nhật thông tin pagination
    renderSensorPagination(totalItems);
}

// Trả về tên tiếng Việt của loại cảm biến
function getSensorTypeDisplayName(type) {
    switch (type.toLowerCase()) {
        case "temperature": return "Nhiệt độ";
        case "humidity":    return "Độ ẩm";
        case "light":       return "Ánh sáng";
        default:            return type;
    }
}

// Trả về CSS class của badge theo loại cảm biến
function getSensorTypeBadgeClass(type) {
    switch (type.toLowerCase()) {
        case "temperature": return "badge-temperature";
        case "humidity":    return "badge-humidity";
        case "light":       return "badge-light";
        default:            return "";
    }
}

// ============================================================
// PAGINATION — Phân trang
// ============================================================

function renderSensorPagination(totalItems) {
    const infoEl      = document.getElementById("sensor-pagination-info");
    const controlsEl  = document.getElementById("sensor-pagination-controls");
    if (!infoEl || !controlsEl) return;

    const totalPages = Math.ceil(totalItems / sensorPageSize) || 1;
    const start      = totalItems === 0 ? 0 : (sensorCurrentPage - 1) * sensorPageSize + 1;
    const end        = Math.min(sensorCurrentPage * sensorPageSize, totalItems);

    // Hiển thị "Hiển thị X–Y trong tổng Z bản ghi"
    infoEl.textContent = "Hiển thị " + start + "–" + end + " trong tổng " + totalItems + " bản ghi";

    // Tạo các nút trang
    controlsEl.innerHTML = "";

    // Nút Previous
    const prevBtn = createPageButton("‹", sensorCurrentPage <= 1, function () {
        sensorCurrentPage--;
        renderSensorTable();
    });
    controlsEl.appendChild(prevBtn);

    // Các số trang (tối đa 7 nút)
    const pageButtons = getPageNumbers(sensorCurrentPage, totalPages);
    pageButtons.forEach(function (pg) {
        if (pg === "...") {
            const dots = document.createElement("button");
            dots.className = "page-btn dots";
            dots.textContent = "...";
            controlsEl.appendChild(dots);
        } else {
            const btn = createPageButton(pg, false, function () {
                sensorCurrentPage = pg;
                renderSensorTable();
            });
            if (pg === sensorCurrentPage) btn.classList.add("active");
            controlsEl.appendChild(btn);
        }
    });

    // Nút Next
    const nextBtn = createPageButton("›", sensorCurrentPage >= totalPages, function () {
        sensorCurrentPage++;
        renderSensorTable();
    });
    controlsEl.appendChild(nextBtn);
}

// Tạo một nút trang
function createPageButton(label, isDisabled, onClick) {
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
        // Ít trang: hiển thị tất cả
        const pages = [];
        for (let i = 1; i <= total; i++) pages.push(i);
        return pages;
    }

    // Nhiều trang: hiển thị smart pagination
    const pages = [];

    if (current <= 4) {
        // Đầu trang
        for (let i = 1; i <= 5; i++) pages.push(i);
        pages.push("...");
        pages.push(total);
    } else if (current >= total - 3) {
        // Cuối trang
        pages.push(1);
        pages.push("...");
        for (let i = total - 4; i <= total; i++) pages.push(i);
    } else {
        // Giữa
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
