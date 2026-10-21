package com.ptit.iotmonitor.service;

import com.ptit.iotmonitor.dto.PageResponse;
import com.ptit.iotmonitor.dto.SensorDataDTO;
import com.ptit.iotmonitor.dto.SensorLatestResponse;
import com.ptit.iotmonitor.entity.Sensor;
import com.ptit.iotmonitor.entity.SensorData;
import com.ptit.iotmonitor.repository.SensorDataRepository;
import com.ptit.iotmonitor.repository.SensorRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorDataService {

    private final SensorRepository sensorRepository;
    private final SensorDataRepository sensorDataRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");



    public SensorLatestResponse getLatestSensorData() {
        Float temp = null;
        Float hum = null;
        Float light = null;
        LocalDateTime latestTime = null;

        // 1. Cảm biến Ánh sáng (Sensor ID: 1)
        Optional<SensorData> lightOpt = sensorDataRepository.findTopBySensor_IdOrderByTimestampDesc(1);
        if (lightOpt.isPresent()) {
            light = lightOpt.get().getValue();
            latestTime = lightOpt.get().getTimestamp();
        }

        // 2. Cảm biến Nhiệt độ (Sensor ID: 2)
        Optional<SensorData> tempOpt = sensorDataRepository.findTopBySensor_IdOrderByTimestampDesc(2);
        if (tempOpt.isPresent()) {
            temp = tempOpt.get().getValue();
            if (latestTime == null || tempOpt.get().getTimestamp().isAfter(latestTime)) {
                latestTime = tempOpt.get().getTimestamp();
            }
        }

        // 3. Cảm biến Độ ẩm (Sensor ID: 3)
        Optional<SensorData> humOpt = sensorDataRepository.findTopBySensor_IdOrderByTimestampDesc(3);
        if (humOpt.isPresent()) {
            hum = humOpt.get().getValue();
            if (latestTime == null || humOpt.get().getTimestamp().isAfter(latestTime)) {
                latestTime = humOpt.get().getTimestamp();
            }
        }

        return SensorLatestResponse.builder()
                .temperature(temp)
                .humidity(hum)
                .light(light)
                .lastUpdated(latestTime != null ? latestTime.format(TIME_FORMATTER) : "--:--:--")
                .build();
    }

    public List<com.ptit.iotmonitor.dto.ChartPointDTO> getChartHistory(int limit) {
        // Quét đúng 60 bản ghi (20 điểm đo x 3 loại cảm biến)
        Pageable pageable = PageRequest.of(0, limit * 3);
        List<SensorData> list = sensorDataRepository.findLatestRecentData(pageable);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        // list đã được ORDER BY timestamp DESC (mới nhất -> cũ nhất)
        // Nhờ Time-Window Locking khi nhận MQTT, cả 3 sensor cùng chu kỳ có chung 1 timestamp
        Map<String, com.ptit.iotmonitor.dto.ChartPointDTO> map = new LinkedHashMap<>();

        for (SensorData sd : list) {
            String timeKey = sd.getTimestamp().format(TIME_FORMATTER);
            com.ptit.iotmonitor.dto.ChartPointDTO point = map.computeIfAbsent(timeKey, k ->
                    com.ptit.iotmonitor.dto.ChartPointDTO.builder().time(k).build()
            );

            int sensorId = sd.getSensor() != null ? sd.getSensor().getId() : 0;
            if (sensorId == 1 && point.getLight() == null) {
                point.setLight(sd.getValue());
            } else if (sensorId == 2 && point.getTemperature() == null) {
                point.setTemperature(sd.getValue());
            } else if (sensorId == 3 && point.getHumidity() == null) {
                point.setHumidity(sd.getValue());
            }
        }

        // Đảo ngược lại (cũ -> mới) để biểu đồ vẽ từ trái sang phải
        List<com.ptit.iotmonitor.dto.ChartPointDTO> points = new ArrayList<>(map.values());
        Collections.reverse(points);

        // Kế thừa các giá trị gần nhất nếu có gói tin nào bị rớt mạng
        Float lastLight = null, lastTemp = null, lastHum = null;
        for (com.ptit.iotmonitor.dto.ChartPointDTO pt : points) {
            if (pt.getLight() != null) lastLight = pt.getLight(); else pt.setLight(lastLight);
            if (pt.getTemperature() != null) lastTemp = pt.getTemperature(); else pt.setTemperature(lastTemp);
            if (pt.getHumidity() != null) lastHum = pt.getHumidity(); else pt.setHumidity(lastHum);
        }

        if (points.size() > limit) {
            points = points.subList(points.size() - limit, points.size());
        }

        return points;
    }

    public PageResponse<SensorDataDTO> getSensorDataPage(int page, int size, String target, String search, String sortStr) {
        int pageIndex = Math.max(0, page - 1);
        Sort sort = parseSort(sortStr);
        Pageable pageable = PageRequest.of(pageIndex, size, sort);

        Specification<SensorData> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.trim().isEmpty()) {
                String keyword = search.trim();
                List<Predicate> searchPredicates = new ArrayList<>();

                // 1. Tiêu chí theo loại cảm biến cụ thể: 1 (Light), 2 (Temperature), 3 (Humidity)
                int targetSensorId = switch (target.toLowerCase()) {
                    case "light", "anh_sang" -> 1;
                    case "temperature", "nhiet_do" -> 2;
                    case "humidity", "do_am" -> 3;
                    default -> 0;
                };

                if (targetSensorId > 0) {
                    try {
                        float floatVal = Float.parseFloat(keyword.replace(",", "."));
                        searchPredicates.add(cb.and(
                                cb.equal(root.get("sensor").get("id"), targetSensorId),
                                cb.equal(root.get("value"), floatVal)
                        ));
                    } catch (NumberFormatException ignored) {
                        searchPredicates.add(cb.disjunction());
                    }
                } else if ("time".equalsIgnoreCase(target)) {
                    // 2. Tiêu chí lọc theo thời gian
                    try {
                        searchPredicates.add(cb.like(
                                cb.function("DATE_FORMAT", String.class, root.get("timestamp"), cb.literal("%Y-%m-%d %H:%i:%s")),
                                "%" + keyword + "%"
                        ));
                    } catch (Exception ignored) {}
                } else {
                    // 3. Tiêu chí 'all' (Tất cả tiêu chí)
                    try {
                        float floatVal = Float.parseFloat(keyword.replace(",", "."));
                        searchPredicates.add(cb.equal(root.get("value"), floatVal));
                    } catch (NumberFormatException ignored) {}

                    searchPredicates.add(cb.like(cb.lower(root.get("sensor").get("name")), "%" + keyword.toLowerCase() + "%"));
                    try {
                        searchPredicates.add(cb.like(
                                cb.function("DATE_FORMAT", String.class, root.get("timestamp"), cb.literal("%Y-%m-%d %H:%i:%s")),
                                "%" + keyword + "%"
                        ));
                    } catch (Exception ignored) {}
                }

                if (!searchPredicates.isEmpty()) {
                    predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<SensorData> resultPage = sensorDataRepository.findAll(spec, pageable);

        List<SensorDataDTO> dtoList = resultPage.getContent().stream().map(sd -> {
            String displayType = mapTypeToDisplay(sd.getSensor() != null ? sd.getSensor().getType() : "");
            return SensorDataDTO.builder()
                    .id(sd.getId())
                    .type(displayType)
                    .sensorName(sd.getSensor() != null ? sd.getSensor().getName() : "")
                    .value(sd.getValue())
                    .unit(sd.getSensor() != null ? sd.getSensor().getUnit() : "")
                    .time(sd.getTimestamp().format(FULL_FORMATTER))
                    .build();
        }).toList();

        return PageResponse.<SensorDataDTO>builder()
                .content(dtoList)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();
    }

    public SensorData saveSensorReading(Integer sensorId, Float value, LocalDateTime timestamp) {
        Optional<Sensor> sensorOpt = sensorRepository.findById(sensorId);
        if (sensorOpt.isEmpty()) {
            log.warn("Sensor id {} not found when saving reading", sensorId);
            return null;
        }

        SensorData data = SensorData.builder()
                .sensor(sensorOpt.get())
                .value(value)
                .timestamp(timestamp != null ? timestamp : LocalDateTime.now())
                .build();

        SensorData saved = sensorDataRepository.save(data);
        log.info("Saved sensor reading: sensorId={}, value={}, id={}, time={}", sensorId, value, saved.getId(), data.getTimestamp());
        return saved;
    }

    public SensorData saveSensorReading(Integer sensorId, Float value) {
        return saveSensorReading(sensorId, value, null);
    }

    public SensorData saveSensorReadingByType(String type, Float value) {
        if (type == null) return null;
        int sensorId = switch (type.toLowerCase()) {
            case "anh_sang", "light" -> 1;
            case "nhiet_do", "temperature", "heat" -> 2;
            case "do_am", "humidity", "humid" -> 3;
            default -> 0;
        };
        if (sensorId > 0) {
            return saveSensorReading(sensorId, value);
        }
        log.warn("Sensor type {} not recognized when saving reading", type);
        return null;
    }

    private Sort parseSort(String sortStr) {
        if (sortStr == null) return Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "id"));
        return switch (sortStr) {
            case "time-asc" -> Sort.by(Sort.Direction.ASC, "timestamp").and(Sort.by(Sort.Direction.ASC, "id"));
            case "value-desc" -> Sort.by(Sort.Direction.DESC, "value").and(Sort.by(Sort.Direction.DESC, "id"));
            case "value-asc" -> Sort.by(Sort.Direction.ASC, "value").and(Sort.by(Sort.Direction.DESC, "id"));
            default -> Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "id"));
        };
    }

    private String mapTypeToDisplay(String type) {
        if (type == null) return "Unknown";
        return switch (type.toLowerCase()) {
            case "nhiet_do", "temperature" -> "Temperature";
            case "do_am", "humidity" -> "Humidity";
            case "anh_sang", "light" -> "Light";
            default -> type;
        };
    }
}
