package com.ptit.iotmonitor.controller;

import com.ptit.iotmonitor.dto.DeviceActionLogDTO;
import com.ptit.iotmonitor.dto.DeviceCommandRequest;
import com.ptit.iotmonitor.dto.DeviceDTO;
import com.ptit.iotmonitor.dto.PageResponse;
import com.ptit.iotmonitor.entity.DeviceActionLog;
import com.ptit.iotmonitor.service.DeviceActionLogService;
import com.ptit.iotmonitor.service.DeviceService;
import com.ptit.iotmonitor.service.MqttService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Slf4j
public class DeviceController {

    private final DeviceService deviceService;
    private final DeviceActionLogService deviceActionLogService;
    private final MqttService mqttService;

    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/devices")
    public ResponseEntity<List<DeviceDTO>> getDevices() {
        return ResponseEntity.ok(deviceService.getAllDevices());
    }

    @GetMapping("/mqtt/status")
    public ResponseEntity<java.util.Map<String, Object>> getMqttStatus() {
        java.util.Map<String, Object> status = new java.util.HashMap<>();
        status.put("brokerConnected", mqttService.isMqttConnected());
        status.put("hardwareOnline", mqttService.isHardwareOnline());
        status.put("lastHardwareMessageTime", mqttService.getLastHardwareMessageTime());
        return ResponseEntity.ok(status);
    }

    @PostMapping("/devices/{id}/action")
    public ResponseEntity<?> sendCommand(
            @PathVariable("id") Integer id,
            @RequestBody DeviceCommandRequest request
    ) {
        Integer deviceId = id;

        log.info("Received device command: deviceId={}, action={}, userId={}",
                deviceId, request.getAction(), request.getUserId());

        // 1. Kiểm tra kết nối tới MQTT Broker
        if (!mqttService.isMqttConnected()) {
            log.warn("Lệnh bị từ chối: Chưa kết nối tới MQTT Broker!");
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("message", "Chưa kết nối tới MQTT Broker! Vui lòng bật Mosquitto / MQTT Broker."));
        }

        String action = request.getAction() != null ? request.getAction().toUpperCase() : "ON";

        // 2. Gửi lệnh qua MQTT tới ESP32 và chờ phản hồi (timeout 3 giây)
        String responseStatus = mqttService.sendCommandAndWaitForResponse(deviceId, action, 3);

        if (responseStatus == null) {
            log.warn("Gửi lệnh thất bại: Mất kết nối MQTT Broker khi gửi tới device {}", deviceId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                    .body(java.util.Map.of("message", "Không thể gửi lệnh qua MQTT Broker (mất kết nối)!"));
        }

        if ("TIMEOUT".equals(responseStatus)) {
            log.warn("ESP32 không phản hồi cho device {} sau 3 giây", deviceId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT)
                    .body(java.util.Map.of("message", "Mạch ESP32 không phản hồi! Vui lòng kiểm tra nguồn và kết nối ESP32."));
        }

        if ("ERROR".equals(responseStatus)) {
            log.error("Lỗi khi chờ phản hồi từ thiết bị {}", deviceId);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("message", "Lỗi nội bộ khi gửi lệnh tới thiết bị!"));
        }

        // 3. Thiết bị đã phản hồi thành công trạng thái thực tế ("ON" hoặc "OFF")
        DeviceActionLog logEntry = deviceActionLogService.recordAction(
                deviceId,
                request.getUserId(),
                action,
                responseStatus
        );

        DeviceActionLogDTO dto = DeviceActionLogDTO.builder()
                .id(logEntry.getId())
                .deviceName(logEntry.getDevice().getName())
                .sender(logEntry.getUser() != null ? logEntry.getUser().getFullName() : "Admin")
                .action(logEntry.getAction())
                .status(logEntry.getStatus())
                .time(logEntry.getTimestamp().format(FULL_FORMATTER))
                .build();

        return ResponseEntity.ok(dto);
    }

    @GetMapping({"/devices/logs", "/device-history"})
    public ResponseEntity<PageResponse<DeviceActionLogDTO>> getDeviceHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "all") String deviceFilter,
            @RequestParam(defaultValue = "all") String actionFilter,
            @RequestParam(defaultValue = "all") String statusFilter,
            @RequestParam(defaultValue = "time-desc") String sort
    ) {
        PageResponse<DeviceActionLogDTO> response = deviceActionLogService.getHistoryPage(
                page, size, search, deviceFilter, actionFilter, statusFilter, sort
        );
        return ResponseEntity.ok(response);
    }
}
