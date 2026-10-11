package com.ptit.iotmonitor.repository;

import com.ptit.iotmonitor.entity.Device;
import com.ptit.iotmonitor.entity.DeviceActionLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DeviceActionLogRepository extends JpaRepository<DeviceActionLog, Long>, JpaSpecificationExecutor<DeviceActionLog> {

    Optional<DeviceActionLog> findTopByDeviceOrderByTimestampDesc(Device device);

    Optional<DeviceActionLog> findTopByDevice_IdOrderByTimestampDesc(Integer deviceId);
}
