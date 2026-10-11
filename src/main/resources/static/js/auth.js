// ============================================================
// AUTH.JS — Logic Đăng nhập & Đăng ký (Kết nối Backend REST API)
// - Gửi request tới /api/auth/login và /api/auth/register
// - Lưu thông tin User vào localStorage
// - Bảo vệ (Auth Guard) các màn hình chính
// ============================================================

// Khởi tạo các sự kiện khi trang load xong
document.addEventListener("DOMContentLoaded", function () {
    const loginForm    = document.getElementById("login-form");
    const registerForm = document.getElementById("register-form");

    // --- LOGIC XỬ LÝ ĐĂNG NHẬP ---
    if (loginForm) {
        loginForm.addEventListener("submit", async function (e) {
            e.preventDefault();
            const usernameInput = document.getElementById("username").value.trim();
            const passwordInput = document.getElementById("password").value.trim();

            try {
                const response = await fetch("/api/auth/login", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ username: usernameInput, password: passwordInput })
                });

                const data = await response.json();

                if (response.ok && data.success) {
                    const loggedInUser = {
                        userId: data.userId,
                        fullname: data.fullname,
                        username: data.username,
                        email: data.email
                    };
                    localStorage.setItem("iot_logged_in_user", JSON.stringify(loggedInUser));

                    showToast("success", data.message || "Đăng nhập thành công!");
                    setTimeout(function () {
                        window.location.href = "index.html";
                    }, 800);
                } else {
                    showToast("error", data.message || "Sai Tên đăng nhập hoặc Mật khẩu!");
                }
            } catch (err) {
                console.warn("Backend API login failed, checking fallback:", err);
                // Fallback nếu chưa chạy backend
                if (usernameInput === "admin" && passwordInput === "admin") {
                    const loggedInUser = { userId: 1, fullname: "Phùng Văn Hải", username: "admin", email: "hai.pv@ptit.edu.vn" };
                    localStorage.setItem("iot_logged_in_user", JSON.stringify(loggedInUser));
                    showToast("success", "Đăng nhập thành công (offline)!");
                    setTimeout(function () {
                        window.location.href = "index.html";
                    }, 800);
                } else {
                    showToast("error", "Không thể kết nối tới server backend!");
                }
            }
        });
    }

    // --- LOGIC XỬ LÝ ĐĂNG KÝ ---
    if (registerForm) {
        registerForm.addEventListener("submit", async function (e) {
            e.preventDefault();
            const fullname = document.getElementById("fullname").value.trim();
            const username = document.getElementById("username").value.trim();
            const password = document.getElementById("password").value.trim();
            const confirmPassword = document.getElementById("confirm-password").value.trim();

            // Validate mật khẩu khớp nhau
            if (password !== confirmPassword) {
                showToast("error", "Mật khẩu xác nhận không khớp!");
                return;
            }

            try {
                const response = await fetch("/api/auth/register", {
                    method: "POST",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ fullname: fullname, username: username, password: password })
                });

                const data = await response.json();

                if (response.ok && data.success) {
                    showToast("success", data.message || "Đăng ký thành công!");
                    setTimeout(function () {
                        window.location.href = "login.html";
                    }, 1000);
                } else {
                    showToast("error", data.message || "Đăng ký thất bại!");
                }
            } catch (err) {
                console.error("Register error:", err);
                showToast("error", "Không thể kết nối tới máy chủ backend!");
            }
        });
    }
});

// --- AUTH GUARD (Bảo vệ các trang) ---
(function checkAuth() {
    const path = window.location.pathname;
    const isAuthPage = path.includes("login.html") || path.includes("register.html");
    const loggedInUser = localStorage.getItem("iot_logged_in_user");

    if (!isAuthPage) {
        if (!loggedInUser) {
            window.location.href = "login.html";
        }
    } else {
        if (loggedInUser) {
            window.location.href = "index.html";
        }
    }
})();

// --- LOGOUT FUNCTION (Hàm đăng xuất) ---
function logoutUser() {
    localStorage.removeItem("iot_logged_in_user");
    window.location.href = "login.html";
}
