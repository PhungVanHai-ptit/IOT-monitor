package com.ptit.iotmonitor.service;

import com.ptit.iotmonitor.dto.DeviceDTO;
import com.ptit.iotmonitor.entity.Device;
import com.ptit.iotmonitor.entity.DeviceActionLog;
import com.ptit.iotmonitor.repository.DeviceActionLogRepository;
import com.ptit.iotmonitor.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final DeviceActionLogRepository deviceActionLogRepository;



    public List<DeviceDTO> getAllDevices() {
        List<Device> devices = deviceRepository.findAll();
        // Lấy tất cả thiết bị được lưu trong cơ sở dữ liệu
        return devices.stream().map(device -> {
            // Lấy trạng thái mới nhất từ device_action_logs
            Optional<DeviceActionLog> latestLog = deviceActionLogRepository.findTopByDeviceOrderByTimestampDesc(device);
            String status = latestLog.map(DeviceActionLog::getStatus).orElse("OFF");

            return DeviceDTO.builder()
                    .id(device.getId())
                    .name(device.getName())
                    .pin(device.getPin())
                    .icon("light")
                    .status(status)
                    .build();
        }).toList();
    }

    public Optional<Device> getDeviceById(Integer id) {
        return deviceRepository.findById(id);
    }
}
