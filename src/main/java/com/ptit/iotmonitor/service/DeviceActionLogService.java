package com.ptit.iotmonitor.service;

import com.ptit.iotmonitor.dto.DeviceActionLogDTO;
import com.ptit.iotmonitor.dto.PageResponse;
import com.ptit.iotmonitor.entity.Device;
import com.ptit.iotmonitor.entity.DeviceActionLog;
import com.ptit.iotmonitor.entity.User;
import com.ptit.iotmonitor.repository.DeviceActionLogRepository;
import com.ptit.iotmonitor.repository.DeviceRepository;
import com.ptit.iotmonitor.repository.UserRepository;
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
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceActionLogService {

    private final DeviceActionLogRepository deviceActionLogRepository;
    private final DeviceRepository deviceRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter FULL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public DeviceActionLog recordAction(Integer deviceId, Integer userId, String action, String status) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thiết bị id=" + deviceId));

        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByUsername("admin")
                    .orElseGet(() -> userRepository.findAll().stream().findFirst().orElse(null));
        }

        DeviceActionLog logEntry = DeviceActionLog.builder()
                .device(device)
                .user(user)
                .action(action)
                .status(status)
                .timestamp(LocalDateTime.now())
                .build();

        DeviceActionLog saved = deviceActionLogRepository.save(logEntry);
        log.info("Saved device action log: id={}, device={}, action={}, status={}",
                saved.getId(), device.getName(), action, status);
        return saved;
    }

    public void updateLogStatus(Long logId, String newStatus) {
        if (logId == null) return;
        deviceActionLogRepository.findById(logId).ifPresent(logEntry -> {
            logEntry.setStatus(newStatus);
            deviceActionLogRepository.save(logEntry);
            log.info("Updated device action log id={} to status={}", logId, newStatus);
        });
    }

    public PageResponse<DeviceActionLogDTO> getHistoryPage(
            int page,
            int size,
            String search,
            String deviceFilter,
            String actionFilter,
            String statusFilter,
            String sortStr
    ) {
        int pageIndex = Math.max(0, page - 1);
        Sort sort = parseSort(sortStr);
        Pageable pageable = PageRequest.of(pageIndex, size, sort);

        Specification<DeviceActionLog> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. Device filter (lọc theo Tên thiết bị hoặc ID thiết bị trong DB)
            if (deviceFilter != null && !deviceFilter.isBlank() && !"all".equalsIgnoreCase(deviceFilter)) {
                String devFilterTrim = deviceFilter.trim();
                List<Predicate> devPredicates = new ArrayList<>();
                devPredicates.add(cb.equal(cb.lower(root.get("device").get("name")), devFilterTrim.toLowerCase()));
                try {
                    Integer devId = Integer.parseInt(devFilterTrim);
                    devPredicates.add(cb.equal(root.get("device").get("id"), devId));
                } catch (NumberFormatException ignored) {
                }
                predicates.add(cb.or(devPredicates.toArray(new Predicate[0])));
            }

            // 2. Action filter (ON / OFF / CHECK)
            if (actionFilter != null && !actionFilter.isBlank() && !"all".equalsIgnoreCase(actionFilter)) {
                predicates.add(cb.equal(cb.upper(root.get("action")), actionFilter.toUpperCase()));
            }

            // 3. Status filter (SUCCESS / FAILED / PENDING)
            if (statusFilter != null && !statusFilter.isBlank() && !"all".equalsIgnoreCase(statusFilter)) {
                String filterUpper = statusFilter.toUpperCase();
                if ("SUCCESS".equals(filterUpper)) {
                    // Thành công: status = 'SUCCESS' HOẶC (status = action VÀ status != 'PENDING')
                    predicates.add(cb.or(
                            cb.equal(cb.upper(root.get("status")), "SUCCESS"),
                            cb.and(
                                    cb.equal(cb.upper(root.get("status")), cb.upper(root.get("action"))),
                                    cb.notEqual(cb.upper(root.get("status")), "PENDING")
                            )
                    ));
                } else if ("FAILED".equals(filterUpper)) {
                    // Thất bại: status = 'FAILED' HOẶC (status != action VÀ status != 'PENDING')
                    predicates.add(cb.or(
                            cb.equal(cb.upper(root.get("status")), "FAILED"),
                            cb.and(
                                    cb.notEqual(cb.upper(root.get("status")), cb.upper(root.get("action"))),
                                    cb.notEqual(cb.upper(root.get("status")), "PENDING")
                            )
                    ));
                } else if ("PENDING".equals(filterUpper)) {
                    predicates.add(cb.equal(cb.upper(root.get("status")), "PENDING"));
                } else {
                    predicates.add(cb.equal(cb.upper(root.get("status")), filterUpper));
                }
            }

            // 4. Search keyword (Device Name, Sender Name, Time)
            if (search != null && !search.trim().isEmpty()) {
                String keyword = search.trim().toLowerCase();
                List<Predicate> searchPredicates = new ArrayList<>();

                searchPredicates.add(cb.like(cb.lower(root.get("device").get("name")), "%" + keyword + "%"));
                searchPredicates.add(cb.like(cb.lower(root.get("user").get("fullName")), "%" + keyword + "%"));

                try {
                    searchPredicates.add(cb.like(
                            cb.function("DATE_FORMAT", String.class, root.get("timestamp"), cb.literal("%Y-%m-%d %H:%i:%s")),
                            "%" + keyword + "%"
                    ));
                } catch (Exception ignored) {
                }

                predicates.add(cb.or(searchPredicates.toArray(new Predicate[0])));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<DeviceActionLog> resultPage = deviceActionLogRepository.findAll(spec, pageable);

        List<DeviceActionLogDTO> dtoList = resultPage.getContent().stream().map(logItem ->
                DeviceActionLogDTO.builder()
                        .id(logItem.getId())
                        .deviceName(logItem.getDevice() != null ? logItem.getDevice().getName() : "Unknown")
                        .sender(logItem.getUser() != null ? logItem.getUser().getFullName() : "Admin")
                        .action(logItem.getAction())
                        .status(logItem.getStatus())
                        .time(logItem.getTimestamp().format(FULL_FORMATTER))
                        .build()
        ).toList();

        return PageResponse.<DeviceActionLogDTO>builder()
                .content(dtoList)
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .build();
    }

    private Sort parseSort(String sortStr) {
        if ("time-asc".equalsIgnoreCase(sortStr)) {
            return Sort.by(Sort.Direction.ASC, "timestamp").and(Sort.by(Sort.Direction.ASC, "id"));
        }
        return Sort.by(Sort.Direction.DESC, "timestamp").and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
