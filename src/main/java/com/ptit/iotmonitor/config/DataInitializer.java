package com.ptit.iotmonitor.config;

import com.ptit.iotmonitor.entity.*;
import com.ptit.iotmonitor.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Configuration
@ConditionalOnProperty(name = "app.data.init.enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final SensorRepository sensorRepository;
    private final SensorDataRepository sensorDataRepository;
    private final DeviceRepository deviceRepository;
    private final DeviceActionLogRepository deviceActionLogRepository;

    @Override
    public void run(String... args) {
        log.info("Checking and seeding initial data...");

        // 1. Seed User
        User admin = userRepository.findByUsername("admin").orElseGet(() ->
                userRepository.save(User.builder()
                        .username("admin")
                        .password("admin")
                        .fullName("Phùng Văn Hải")
                        .email("hai.pv@ptit.edu.vn")
                        .build())
        );

        // 2. Seed / Đồng bộ Sensors: id 1 = Ánh sáng, id 2 = Nhiệt độ, id 3 = Độ ẩm
        Sensor lightSensor = sensorRepository.findById(1).orElseGet(() ->
                Sensor.builder().type("anh_sang").unit("Lux").name("Cảm biến Ánh sáng").pin("GPIO34").build()
        );
        lightSensor.setType("anh_sang");
        lightSensor.setUnit("Lux");
        lightSensor.setName("Cảm biến Ánh sáng");
        lightSensor.setPin("GPIO34");
        lightSensor = sensorRepository.save(lightSensor);

        Sensor tempSensor = sensorRepository.findById(2).orElseGet(() ->
                Sensor.builder().type("nhiet_do").unit("°C").name("Cảm biến Nhiệt độ").pin("GPIO18").build()
        );
        tempSensor.setType("nhiet_do");
        tempSensor.setUnit("°C");
        tempSensor.setName("Cảm biến Nhiệt độ");
        tempSensor.setPin("GPIO18");
        tempSensor = sensorRepository.save(tempSensor);

        Sensor humSensor = sensorRepository.findById(3).orElseGet(() ->
                Sensor.builder().type("do_am").unit("%").name("Cảm biến Độ ẩm").pin("GPIO18").build()
        );
        humSensor.setType("do_am");
        humSensor.setUnit("%");
        humSensor.setName("Cảm biến Độ ẩm");
        humSensor.setPin("GPIO18");
        humSensor = sensorRepository.save(humSensor);

        // 3. Seed Devices (khớp đúng 2 thiết bị thực tế: pin 19 và pin 21 trên ESP32)
        List<Device> currentDevices = deviceRepository.findAll();
        if (currentDevices.size() > 2) {
            log.info("Cleaning up extra devices in DB: found {} devices, keeping first 2", currentDevices.size());
            for (int i = 2; i < currentDevices.size(); i++) {
                Device extra = currentDevices.get(i);
                try {
                    List<DeviceActionLog> logs = deviceActionLogRepository.findAll().stream()
                            .filter(l -> l.getDevice() != null && l.getDevice().getId().equals(extra.getId()))
                            .toList();
                    deviceActionLogRepository.deleteAll(logs);
                    deviceRepository.delete(extra);
                } catch (Exception ignored) {}
            }
        }

        Device light1 = deviceRepository.findById(1).orElseGet(() ->
                deviceRepository.save(Device.builder().name("Light 1").pin("GPIO19").build())
        );

        Device light2 = deviceRepository.findById(2).orElseGet(() ->
                deviceRepository.save(Device.builder().name("Light 2").pin("GPIO21").build())
        );

        // 4. Seed Sensor Data nếu bảng trống
        if (sensorDataRepository.count() == 0) {
            log.info("Seeding sample sensor data for past days...");
            List<SensorData> sampleData = new ArrayList<>();
            Random rand = new Random(42);
            LocalDateTime now = LocalDateTime.now();

            // Tạo dữ liệu cho 3 ngày gần nhất, mỗi 15 phút một điểm
            for (int i = 288; i >= 0; i--) {
                LocalDateTime time = now.minusMinutes(i * 15L);

                // Nhiệt độ: 25.0 -> 34.0
                float tempVal = Math.round((25.0f + rand.nextFloat() * 9.0f) * 10) / 10.0f;
                sampleData.add(SensorData.builder().sensor(tempSensor).value(tempVal).timestamp(time).build());

                // Độ ẩm: 50 -> 88
                float humVal = 50.0f + rand.nextInt(39);
                sampleData.add(SensorData.builder().sensor(humSensor).value(humVal).timestamp(time).build());

                // Ánh sáng: 150 -> 780
                float lightVal = 150.0f + rand.nextInt(630);
                sampleData.add(SensorData.builder().sensor(lightSensor).value(lightVal).timestamp(time).build());
            }

            sensorDataRepository.saveAll(sampleData);
            log.info("Successfully seeded {} sensor readings", sampleData.size());
        }

        // 5. Seed Device Action Logs nếu bảng trống
        if (deviceActionLogRepository.count() == 0) {
            log.info("Seeding sample device action logs...");
            List<DeviceActionLog> sampleLogs = new ArrayList<>();
            LocalDateTime baseTime = LocalDateTime.now().minusHours(2);

            String[][] mockLogs = {
                    {"Light 1", "ON", "ON"},
                    {"Light 2", "OFF", "OFF"},
                    {"Light 1", "ON", "OFF"},
                    {"Light 2", "ON", "ON"},
                    {"Light 1", "OFF", "OFF"},
                    {"Light 2", "ON", "ON"},
                    {"Light 1", "ON", "ON"},
                    {"Light 2", "OFF", "OFF"},
                    {"Light 1", "OFF", "OFF"},
                    {"Light 2", "ON", "ON"},
                    {"Light 1", "ON", "ON"},
                    {"Light 2", "OFF", "ON"},
                    {"Light 1", "OFF", "OFF"},
                    {"Light 2", "ON", "OFF"},
                    {"Light 1", "ON", "ON"}
            };

            for (int i = 0; i < mockLogs.length; i++) {
                String devName = mockLogs[i][0];
                String action = mockLogs[i][1];
                String status = mockLogs[i][2];
                Device dev = devName.equals("Light 1") ? light1 : light2;
                LocalDateTime logTime = baseTime.plusMinutes(i * 7L);

                sampleLogs.add(DeviceActionLog.builder()
                        .device(dev)
                        .user(admin)
                        .action(action)
                        .status(status)
                        .timestamp(logTime)
                        .build());
            }

            deviceActionLogRepository.saveAll(sampleLogs);
            log.info("Successfully seeded {} device action logs", sampleLogs.size());
        }

        log.info("Initial data seeding completed successfully.");
    }
}
