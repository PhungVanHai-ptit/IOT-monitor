package com.ptit.iotmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorDataDTO {
    private Long id;
    private String type;        // 'Temperature', 'Humidity', 'Light'
    private String sensorName;  // e.g. 'Cảm biến Nhiệt độ'
    private Float value;
    private String unit;        // '°C', '%', 'Lux'
    private String time;        // 'yyyy-MM-dd HH:mm:ss'
}
