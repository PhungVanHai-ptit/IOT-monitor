package com.ptit.iotmonitor.controller;

import com.ptit.iotmonitor.dto.PageResponse;
import com.ptit.iotmonitor.dto.SensorDataDTO;
import com.ptit.iotmonitor.dto.SensorLatestResponse;
import com.ptit.iotmonitor.service.SensorDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/sensors", "/api/sensor-data"})
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SensorDataController {

    private final SensorDataService sensorDataService;

    @GetMapping({"/data", ""})
    public ResponseEntity<PageResponse<SensorDataDTO>> getSensorData(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "all") String target,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(defaultValue = "time-desc") String sort
    ) {
        PageResponse<SensorDataDTO> response = sensorDataService.getSensorDataPage(page, size, target, search, sort);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/latest")
    public ResponseEntity<SensorLatestResponse> getLatestSensorData() {
        SensorLatestResponse latest = sensorDataService.getLatestSensorData();
        return ResponseEntity.ok(latest);
    }

    @GetMapping("/chart")
    public ResponseEntity<java.util.List<com.ptit.iotmonitor.dto.ChartPointDTO>> getChartHistory(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(sensorDataService.getChartHistory(limit));
    }
}
