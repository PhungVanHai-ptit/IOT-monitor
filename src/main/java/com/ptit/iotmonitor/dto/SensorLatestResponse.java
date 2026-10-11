package com.ptit.iotmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SensorLatestResponse {
    private Float light;
    private Float temperature;
    private Float humidity;
    private String lastUpdated; // 'HH:mm:ss'
}
