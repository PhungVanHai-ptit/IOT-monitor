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

            // 1. Device filter
            if (deviceFilter != null && !deviceFilter.isBlank() && !"all".equalsIgnoreCase(deviceFilter)) {
                predicates.add(cb.equal(cb.lower(root.get("device").get("name")), deviceFilter.toLowerCase()));
            }

            // 2. Action filter (ON / OFF / CHECK)
            if (actionFilter != null && !actionFilter.isBlank() && !"all".equalsIgnoreCase(actionFilter)) {
                predicates.add(cb.equal(cb.upper(root.get("action")), actionFilter.toUpperCase()));
            }

            // 3. Status filter (ON / OFF / mismatch)
            if (statusFilter != null && !statusFilter.isBlank() && !"all".equalsIgnoreCase(statusFilter)) {
                if ("mismatch".equalsIgnoreCase(statusFilter)) {
                    predicates.add(cb.notEqual(root.get("action"), root.get("status")));
                } else {
                    predicates.add(cb.equal(cb.upper(root.get("status")), statusFilter.toUpperCase()));
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
        if (sortStr == null) return Sort.by(Sort.Direction.DESC, "timestamp");
        return switch (sortStr) {
            case "time-asc" -> Sort.by(Sort.Direction.ASC, "timestamp");
            case "name-asc" -> Sort.by(Sort.Direction.ASC, "device.name");
            case "name-desc" -> Sort.by(Sort.Direction.DESC, "device.name");
            case "action-on" -> Sort.by(Sort.Direction.DESC, "action");
            case "action-off" -> Sort.by(Sort.Direction.ASC, "action");
            case "status-on" -> Sort.by(Sort.Direction.DESC, "status");
            case "status-off" -> Sort.by(Sort.Direction.ASC, "status");
            default -> Sort.by(Sort.Direction.DESC, "timestamp");
        };
    }
}
