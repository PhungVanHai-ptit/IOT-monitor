package com.ptit.iotmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceCommandRequest {
    private Integer deviceId;
    private String action; // 'ON', 'OFF', 'CHECK'
    private Integer userId;
}
