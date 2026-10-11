package com.ptit.iotmonitor.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sensors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sensor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 50)
    private String type; // 'nhiet_do' | 'anh_sang' | 'do_am'

    @Column(length = 20)
    private String unit; // '°C', 'lux', '%'

    @Column(length = 100)
    private String name;

    @Column(length = 20)
    private String pin;
}
