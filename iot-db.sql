-- =========================================
-- CSDL: He thong Web dieu khien mach ESP32
-- 3 cam bien (nhiet do, anh sang, do am) + 3 thiet bi
-- =========================================

CREATE DATABASE esp32_control;
USE esp32_control;

CREATE TABLE users (
    id          INT AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL,
    password    VARCHAR(255) NOT NULL,   -- luu dang hash, khong luu plain text
    full_name   VARCHAR(50)  NOT NULL,
    email       VARCHAR(50)  NOT NULL
);

CREATE TABLE sensors (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    type    VARCHAR(50)  NOT NULL,   -- 'nhiet_do' | 'anh_sang' | 'do_am'
    unit    VARCHAR(20),             -- vd: 'C', 'lux', '%'
    name    VARCHAR(100),
    pin     VARCHAR(20)              -- chan ket noi tren ESP32
);

CREATE TABLE sensor_data (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    sensor_id   INT NOT NULL,
    value       FLOAT NOT NULL,
    timestamp   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sensor_data_sensor
        FOREIGN KEY (sensor_id) REFERENCES sensors(id),
    INDEX idx_sensor_data_sensor_time (sensor_id, timestamp),
    INDEX idx_sensor_data_value (value)
);

CREATE TABLE devices (
    id      INT AUTO_INCREMENT PRIMARY KEY,
    name    VARCHAR(100) NOT NULL,
    pin     VARCHAR(20)            -- chan dieu khien tren ESP32
);

CREATE TABLE device_action_logs (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id   INT NOT NULL,
    user_id     INT NOT NULL,      -- nguoi thuc hien thao tac
    action      ENUM('ON', 'OFF', 'CHECK') NOT NULL,
    status      ENUM('ON', 'OFF') NOT NULL,
    timestamp   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_device_logs_device
        FOREIGN KEY (device_id) REFERENCES devices(id),
    CONSTRAINT fk_device_logs_user
        FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_device_logs_device_time (device_id, timestamp),
    INDEX idx_device_logs_user (user_id)
);
