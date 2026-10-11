package com.ptit.iotmonitor.repository;

import com.ptit.iotmonitor.entity.Sensor;
import com.ptit.iotmonitor.entity.SensorData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long>, JpaSpecificationExecutor<SensorData> {

    Optional<SensorData> findTopBySensorOrderByTimestampDesc(Sensor sensor);

    Optional<SensorData> findTopBySensor_IdOrderByTimestampDesc(Integer sensorId);

    // Lấy N bản ghi mới nhất của sensor để vẽ biểu đồ
    List<SensorData> findTop20BySensorOrderByTimestampDesc(Sensor sensor);

    @Query("SELECT sd FROM SensorData sd JOIN FETCH sd.sensor ORDER BY sd.timestamp DESC")
    List<SensorData> findLatestRecentData(Pageable pageable);
}
