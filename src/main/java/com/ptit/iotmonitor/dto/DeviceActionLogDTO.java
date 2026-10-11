package com.ptit.iotmonitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceActionLogDTO {
    private Long id;
    private String deviceName;
    private String sender;
    private String action; // 'ON', 'OFF', 'CHECK'
    private String status; // 'ON', 'OFF'
    private String time;   // 'yyyy-MM-dd HH:mm:ss'
}
