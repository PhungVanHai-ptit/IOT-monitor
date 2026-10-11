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

        // 1. Cảm biến Ánh sáng (id: 1, type: anh_sang)
        Sensor lightSensor = findSensorByType("anh_sang", "light");
        Optional<SensorData> lightOpt = lightSensor != null
                ? sensorDataRepository.findTopBySensorOrderByTimestampDesc(lightSensor)
                : sensorDataRepository.findTopBySensor_IdOrderByTimestampDesc(1);
        if (lightOpt.isPresent()) {
            light = lightOpt.get().getValue();
            latestTime = lightOpt.get().getTimestamp();
        }

        // 2. Cảm biến Nhiệt độ (id: 2, type: nhiet_do)
        Sensor tempSensor = findSensorByType("nhiet_do", "temperature", "heat");
        Optional<SensorData> tempOpt = tempSensor != null
                ? sensorDataRepository.findTopBySensorOrderByTimestampDesc(tempSensor)
                : sensorDataRepository.findTopBySensor_IdOrderByTimestampDesc(2);
        if (tempOpt.isPresent()) {
            temp = tempOpt.get().getValue();
            if (latestTime == null || tempOpt.get().getTimestamp().isAfter(latestTime)) {
                latestTime = tempOpt.get().getTimestamp();
            }
        }

        // 3. Cảm biến Độ ẩm (id: 3, type: do_am)
        Sensor humSensor = findSensorByType("do_am", "humidity", "humid");
        Optional<SensorData> humOpt = humSensor != null
                ? sensorDataRepository.findTopBySensorOrderByTimestampDesc(humSensor)
                : sensorDataRepository.findTopBySensor_IdOrderByTimestampDesc(3);
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
        int fetchSize = Math.max(limit * 4, 80);
        Pageable pageable = PageRequest.of(0, fetchSize);
        List<SensorData> list = sensorDataRepository.findLatestRecentData(pageable);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }

        // Sắp xếp tăng dần theo thời gian (cũ -> mới) để biểu đồ vẽ từ trái sang phải
        List<SensorData> sortedList = new ArrayList<>(list);
        sortedList.sort(Comparator.comparing(SensorData::getTimestamp));

        List<com.ptit.iotmonitor.dto.ChartPointDTO> points = new ArrayList<>();
        com.ptit.iotmonitor.dto.ChartPointDTO currentPoint = null;
        LocalDateTime currentBaseTime = null;

        Float lastTemp = null;
        Float lastHum = null;
        Float lastLight = null;

        for (SensorData sd : sortedList) {
            LocalDateTime ts = sd.getTimestamp();
            if (currentBaseTime == null || java.time.Duration.between(currentBaseTime, ts).abs().getSeconds() > 2) {
                currentBaseTime = ts;
                currentPoint = com.ptit.iotmonitor.dto.ChartPointDTO.builder()
                        .time(currentBaseTime.format(TIME_FORMATTER))
                        .temperature(lastTemp)
                        .humidity(lastHum)
                        .light(lastLight)
                        .build();
                points.add(currentPoint);
            }

            String type = sd.getSensor() != null ? sd.getSensor().getType().toLowerCase() : "";
            int id = sd.getSensor() != null ? sd.getSensor().getId() : 0;

            if (id == 1 || type.contains("anh_sang") || type.contains("light")) {
                currentPoint.setLight(sd.getValue());
                lastLight = sd.getValue();
            } else if (id == 2 || type.contains("nhiet_do") || type.contains("temp") || type.contains("heat")) {
                currentPoint.setTemperature(sd.getValue());
                lastTemp = sd.getValue();
            } else if (id == 3 || type.contains("do_am") || type.contains("humid")) {
                currentPoint.setHumidity(sd.getValue());
                lastHum = sd.getValue();
            }
        }

        // Giữ lại tối đa `limit` điểm đo gần nhất
        if (points.size() > limit) {
            points = points.subList(points.size() - limit, points.size());
        }

        return points;
    }

    public Sensor findSensorByType(String... types) {
        for (String t : types) {
            Optional<Sensor> s = sensorRepository.findByType(t);
            if (s.isPresent()) return s.get();
        }
        return null;
    }

    public PageResponse<SensorDataDTO> getSensorDataPage(int page, int size, String target, String search, String sortStr) {
        // Pageable (0-indexed)
        int pageIndex = Math.max(0, page - 1);
        Sort sort = parseSort(sortStr);
        Pageable pageable = PageRequest.of(pageIndex, size, sort);

        Specification<SensorData> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Target filter: temperature, humidity, light
            // Chỉ lọc theo loại cảm biến khi target là loại cụ thể (không lọc khi target là 'all' hoặc 'time')
            if (target != null && !target.isBlank() && !"all".equalsIgnoreCase(target) && !"time".equalsIgnoreCase(target)) {
                String targetType = mapTargetToSensorType(target);
                if (targetType != null) {
                    predicates.add(cb.or(
                            cb.equal(cb.lower(root.get("sensor").get("type")), targetType.toLowerCase()),
                            cb.equal(cb.lower(root.get("sensor").get("type")), target.toLowerCase())
                    ));
                }
            }

            // 2. Search keyword filter
            if (search != null && !search.trim().isEmpty()) {
                String keyword = search.trim();
                String valueKeyword = keyword.replace(",", ".");
                List<Predicate> searchPredicates = new ArrayList<>();

                // 2.1. Nếu chọn tiêu chí 'time' -> CHỈ tìm kiếm theo chuỗi thời gian
                if ("time".equalsIgnoreCase(target)) {
                    try {
                        searchPredicates.add(cb.like(
                                cb.function("DATE_FORMAT", String.class, root.get("timestamp"), cb.literal("%Y-%m-%d %H:%i:%s")),
                                "%" + keyword + "%"
                        ));
                    } catch (Exception e) {
                        log.warn("Cannot apply DATE_FORMAT function on timestamp: {}", e.getMessage());
                    }
                }
                // 2.2. Nếu chọn loại cảm biến cụ thể ('temperature', 'humidity', 'light') -> CHỈ tìm kiếm theo giá trị đo số
                else if ("temperature".equalsIgnoreCase(target) || "humidity".equalsIgnoreCase(target) || "light".equalsIgnoreCase(target)) {
                    try {
                        float floatVal = Float.parseFloat(valueKeyword);
                        searchPredicates.add(cb.equal(root.get("value"), floatVal));
                    } catch (NumberFormatException ignored) {
                        // Nhập chuỗi ngày hoặc chữ không phải số -> trả về rỗng
                        searchPredicates.add(cb.disjunction());
                    }
                }
                // 2.3. Nếu chọn 'all' (Tất cả tiêu chí) -> tìm đa năng: theo giá trị, tên cảm biến hoặc thời gian
                else {
                    try {
                        float floatVal = Float.parseFloat(valueKeyword);
                        searchPredicates.add(cb.equal(root.get("value"), floatVal));
                    } catch (NumberFormatException ignored) {}

                    searchPredicates.add(cb.like(cb.lower(root.get("sensor").get("name")), "%" + keyword.toLowerCase() + "%"));

                    try {
                        searchPredicates.add(cb.like(
                                cb.function("DATE_FORMAT", String.class, root.get("timestamp"), cb.literal("%Y-%m-%d %H:%i:%s")),
                                "%" + keyword + "%"
                        ));
                    } catch (Exception e) {
                        log.warn("Cannot apply DATE_FORMAT function on timestamp: {}", e.getMessage());
                    }
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

    public SensorData saveSensorReading(Integer sensorId, Float value) {
        Optional<Sensor> sensorOpt = sensorRepository.findById(sensorId);
        if (sensorOpt.isEmpty()) {
            log.warn("Sensor id {} not found when saving reading", sensorId);
            return null;
        }

        SensorData data = SensorData.builder()
                .sensor(sensorOpt.get())
                .value(value)
                .timestamp(LocalDateTime.now())
                .build();

        SensorData saved = sensorDataRepository.save(data);
        log.info("Saved sensor reading: sensorId={}, value={}, id={}", sensorId, value, saved.getId());
        return saved;
    }

    public SensorData saveSensorReadingByType(String type, Float value) {
        Sensor sensor = findSensorByType(type);
        if (sensor == null) {
            log.warn("Sensor type {} not found when saving reading", type);
            return null;
        }
        SensorData data = SensorData.builder()
                .sensor(sensor)
                .value(value)
                .timestamp(LocalDateTime.now())
                .build();
        SensorData saved = sensorDataRepository.save(data);
        log.info("Saved sensor reading by type: type={}, value={}, id={}", type, value, saved.getId());
        return saved;
    }

    private Sort parseSort(String sortStr) {
        if (sortStr == null) return Sort.by(Sort.Direction.DESC, "timestamp");
        return switch (sortStr) {
            case "time-asc" -> Sort.by(Sort.Direction.ASC, "timestamp");
            case "value-desc" -> Sort.by(Sort.Direction.DESC, "value");
            case "value-asc" -> Sort.by(Sort.Direction.ASC, "value");
            default -> Sort.by(Sort.Direction.DESC, "timestamp");
        };
    }

    private String mapTargetToSensorType(String target) {
        return switch (target.toLowerCase()) {
            case "temperature" -> "nhiet_do";
            case "humidity" -> "do_am";
            case "light" -> "anh_sang";
            default -> target;
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
