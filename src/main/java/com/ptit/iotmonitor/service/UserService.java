package com.ptit.iotmonitor.service;

import com.ptit.iotmonitor.dto.LoginRequest;
import com.ptit.iotmonitor.dto.LoginResponse;
import com.ptit.iotmonitor.dto.RegisterRequest;
import com.ptit.iotmonitor.entity.User;
import com.ptit.iotmonitor.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @PostConstruct
    public void initDefaultUser() {
        // Tạo sẵn tài khoản mặc định nếu chưa tồn tại
        if (!userRepository.existsByUsername("admin")) {
            User admin = User.builder()
                    .username("admin")
                    .password("admin")
                    .fullName("Phùng Văn Hải")
                    .email("hai.pv@ptit.edu.vn")
                    .build();
            userRepository.save(admin);
            log.info("Initialized default admin user: admin / admin");
        }
    }

    public LoginResponse login(LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            return LoginResponse.builder()
                    .success(false)
                    .message("Vui lòng điền đầy đủ tên đăng nhập và mật khẩu")
                    .build();
        }

        Optional<User> userOpt = userRepository.findByUsernameAndPassword(
                request.getUsername().trim(),
                request.getPassword().trim()
        );

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            return LoginResponse.builder()
                    .success(true)
                    .userId(user.getId())
                    .username(user.getUsername())
                    .fullname(user.getFullName())
                    .email(user.getEmail())
                    .message("Đăng nhập thành công!")
                    .build();
        }

        return LoginResponse.builder()
                .success(false)
                .message("Sai Tên đăng nhập hoặc Mật khẩu!")
                .build();
    }

    public LoginResponse register(RegisterRequest request) {
        if (request.getUsername() == null || request.getPassword() == null || request.getFullname() == null) {
            return LoginResponse.builder()
                    .success(false)
                    .message("Vui lòng điền đầy đủ thông tin")
                    .build();
        }

        String username = request.getUsername().trim();
        if (userRepository.existsByUsername(username)) {
            return LoginResponse.builder()
                    .success(false)
                    .message("Tên tài khoản này đã tồn tại!")
                    .build();
        }

        String email = request.getEmail();
        if (email == null || email.isBlank()) {
            email = username + "@ptit.edu.vn";
        }

        User newUser = User.builder()
                .username(username)
                .password(request.getPassword().trim())
                .fullName(request.getFullname().trim())
                .email(email.trim())
                .build();

        userRepository.save(newUser);

        return LoginResponse.builder()
                .success(true)
                .userId(newUser.getId())
                .username(newUser.getUsername())
                .fullname(newUser.getFullName())
                .email(newUser.getEmail())
                .message("Đăng ký thành công!")
                .build();
    }
}
