package com.ptit.iotmonitor.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.concurrent.*;

@Service
@Slf4j
public class MqttService implements MqttCallbackExtended {

    @Value("${mqtt.broker.url:tcp://localhost:1885}")
    private String brokerUrl;

    @Value("${mqtt.client.id:iot-monitor-backend}")
    private String clientId;

    @Value("${mqtt.username:phungvanhai}")
    private String username;

    @Value("${mqtt.password:b23dccn281}")
    private String password;

    @Value("${mqtt.topic.sensor:data/sensor/+}")
    private String sensorTopicPattern;

    @Value("${mqtt.topic.device.status:device/+/status}")
    private String deviceStatusPattern;

    private final SensorDataService sensorDataService;
    private final DeviceActionLogService deviceActionLogService;

    private MqttClient mqttClient;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private final ConcurrentMap<Integer, CompletableFuture<String>> pendingDeviceResponses = new ConcurrentHashMap<>();
    private volatile long lastHardwareMessageTime = 0;
    private LocalDateTime currentBatchTime = null;
    private long lastBatchTimestampMillis = 0;

    /**
     * Khóa thời gian theo đợt đo (Time-Window Locking):
     * Các gói tin cảm biến đến cách nhau dưới 1000ms (1 giây) sẽ dùng chung 1 timestamp,
     * tránh bị lệch 1 giây khi gói tin vượt qua ranh giới giữa giây cũ và giây mới.
     */
    private synchronized LocalDateTime getBatchTimestamp() {
        long now = System.currentTimeMillis();
        if (currentBatchTime == null || (now - lastBatchTimestampMillis) > 1000) {
            lastBatchTimestampMillis = now;
            currentBatchTime = LocalDateTime.now().withNano(0);
        }
        return currentBatchTime;
    }

    public boolean isMqttConnected() {
        return mqttClient != null && mqttClient.isConnected();
    }

    public boolean isHardwareOnline() {
        return lastHardwareMessageTime > 0 && (System.currentTimeMillis() - lastHardwareMessageTime) < 10000;
    }

    public long getLastHardwareMessageTime() {
        return lastHardwareMessageTime;
    }

    public MqttService(
            SensorDataService sensorDataService,
            @Lazy DeviceActionLogService deviceActionLogService
    ) {
        this.sensorDataService = sensorDataService;
        this.deviceActionLogService = deviceActionLogService;
    }

    @PostConstruct
    public void init() {
        // Kết nối bất đồng bộ để không chặn Spring Boot khởi động nếu broker chưa bật
        Executors.newSingleThreadExecutor().submit(this::connect);
    }

    public synchronized void connect() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                return;
            }

            log.info("Connecting to MQTT broker at: {}", brokerUrl);
            mqttClient = new MqttClient(brokerUrl, clientId + "-" + System.currentTimeMillis(), new MemoryPersistence());
            mqttClient.setCallback(this);

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);
            options.setConnectionTimeout(10);
            options.setKeepAliveInterval(60);

            if (username != null && !username.isBlank()) {
                options.setUserName(username);
                options.setPassword(password.toCharArray());
            }

            mqttClient.connect(options);
            log.info("MQTT connected successfully to {}", brokerUrl);
            // subscribeTopics() được tự động gọi trong callback connectComplete()
        } catch (MqttException e) {
            log.warn("Could not connect to MQTT broker ({}: {}). Will retry in 10 seconds...", brokerUrl, e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        reconnectExecutor.schedule(this::connect, 10, TimeUnit.SECONDS);
    }

    private void subscribeTopics() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.subscribe(sensorTopicPattern, 1);
                mqttClient.subscribe(deviceStatusPattern, 1);
                log.info("Subscribed to topics: {} and {}", sensorTopicPattern, deviceStatusPattern);
            }
        } catch (MqttException e) {
            log.error("Failed to subscribe MQTT topics: {}", e.getMessage());
        }
    }

    /**
     * Gửi lệnh điều khiển tới thiết bị: topic device/{id}/action, payload: ON / OFF
     */
    public boolean publishDeviceCommand(Integer deviceId, String action) {
        String topic = "device/" + deviceId + "/action";
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                MqttMessage message = new MqttMessage(action.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                mqttClient.publish(topic, message);
                log.info("Published MQTT command: topic={}, payload={}", topic, action);
                return true;
            } else {
                log.warn("MQTT Client not connected. Cannot publish to topic: {}", topic);
                return false;
            }
        } catch (MqttException e) {
            log.error("Failed to publish MQTT message to topic {}: {}", topic, e.getMessage());
            return false;
        }
    }

    /**
     * Gửi lệnh tới ESP32 qua MQTT và đợi ESP32 phản hồi trạng thái thực tế
     * @return trạng thái "ON"/"OFF" nếu thành công, "TIMEOUT" nếu ESP32 không phản hồi, null nếu chưa kết nối MQTT
     */
    public String sendCommandAndWaitForResponse(Integer deviceId, String action, long timeoutSeconds) {
        if (mqttClient == null || !mqttClient.isConnected()) {
            log.warn("Cannot send command to device {}: MQTT Broker not connected", deviceId);
            return null;
        }

        CompletableFuture<String> future = new CompletableFuture<>();
        pendingDeviceResponses.put(deviceId, future);

        boolean published = publishDeviceCommand(deviceId, action);
        if (!published) {
            pendingDeviceResponses.remove(deviceId);
            return null;
        }

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.warn("ESP32 device {} did not respond with status within {}s", deviceId, timeoutSeconds);
            return "TIMEOUT";
        } catch (Exception e) {
            log.error("Error waiting for response from device {}: {}", deviceId, e.getMessage());
            return "ERROR";
        } finally {
            pendingDeviceResponses.remove(deviceId);
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("MQTT Connection established (reconnect={}) to {}", reconnect, serverURI);
        subscribeTopics();
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", (cause != null ? cause.getMessage() : "Unknown"));
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8).trim();
        log.info("MQTT Message arrived: topic=[{}], payload=[{}]", topic, payload);
        lastHardwareMessageTime = System.currentTimeMillis();

        try {
            // 1. Topic sensor: data/sensor/{id} (1: Ánh sáng, 2: Nhiệt độ, 3: Độ ẩm)
            if (topic.startsWith("data/sensor/")) {
                int sensorId = Integer.parseInt(topic.substring("data/sensor/".length()).trim());
                float value = Float.parseFloat(payload.replace(",", "."));
                float finalValue = (sensorId == 1) ? convertRawLightToLux(value) : value;
                LocalDateTime batchTime = getBatchTimestamp();
                sensorDataService.saveSensorReading(sensorId, finalValue, batchTime);
            }
            // 2. Topic device status: device/{id}/status
            else if (topic.startsWith("device/") && topic.endsWith("/status")) {
                String[] parts = topic.split("/");
                if (parts.length == 3) {
                    int deviceId = Integer.parseInt(parts[1]);
                    String status = payload.toUpperCase(); // 'ON' / 'OFF'
                    log.info("Received device status update: deviceId={}, status={}", deviceId, status);

                    CompletableFuture<String> pending = pendingDeviceResponses.get(deviceId);
                    if (pending != null && !pending.isDone()) {
                        pending.complete(status);
                    } else if (!message.isRetained()) {
                        // Chỉ ghi log CHECK nếu là trạng thái thực tế do mạch gửi lên, bỏ qua tin retained cũ từ Broker
                        deviceActionLogService.recordAction(deviceId, null, "CHECK", status);
                    } else {
                        log.info("Ignored retained MQTT status message on startup: deviceId={}, status={}", deviceId, status);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing MQTT message on topic {}: {}", topic, e.getMessage());
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Message delivery completed
    }

    /**
     * Quy đổi giá trị đọc thô từ cảm biến quang trở (ADC 12-bit 0 - 4095 của ESP32) sang đơn vị Lux.
     * Mạch quang trở thực tế mắc dạng:
     * - Trời tối (che tay): điện trở LDR tăng cao -> ADC tăng vọt (~4000).
     * - Trời sáng (chiếu đèn/mở tay): điện trở LDR giảm -> ADC giảm (~0).
     * Do đó công thức tính chuẩn là:
     * Lux = ((4095 - adc) / 4095) * 1000
     * -> Khi che tối: ADC tăng -> Lux giảm về gần 0 Lux.
     * -> Khi có ánh sáng: ADC giảm -> Lux tăng lên cao.
     */
    public static float convertRawLightToLux(float rawAdc) {
        if (rawAdc <= 0) return 1000.0f;
        float adc = Math.min(rawAdc, 4095.0f);
        float lux = ((4095.0f - adc) / 4095.0f) * 1000.0f;
        return Math.max(0.0f, Math.round(lux * 10.0f) / 10.0f);
    }

    @PreDestroy
    public void destroy() {
        try {
            reconnectExecutor.shutdownNow();
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
        } catch (MqttException ignored) {
        }
    }
}
