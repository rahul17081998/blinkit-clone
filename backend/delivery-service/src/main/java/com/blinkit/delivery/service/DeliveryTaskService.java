package com.blinkit.delivery.service;

import com.blinkit.delivery.dto.request.AssignPartnerRequest;
import com.blinkit.delivery.dto.request.UpdateTaskStatusRequest;
import com.blinkit.delivery.dto.response.DeliveryTaskResponse;
import com.blinkit.delivery.entity.DeliveryPartner;
import com.blinkit.delivery.entity.DeliveryTask;
import com.blinkit.delivery.event.DeliveryStatusUpdatedEvent;
import com.blinkit.delivery.kafka.DeliveryEventPublisher;
import com.blinkit.delivery.repository.DeliveryTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryTaskService {

    private final DeliveryTaskRepository taskRepository;
    private final DeliveryPartnerService partnerService;
    private final DeliveryEventPublisher eventPublisher;

    @Value("${delivery.store.name}")
    private String storeName;

    @Value("${delivery.store.address}")
    private String storeAddress;

    @Value("${delivery.store.lat}")
    private Double storeLat;

    @Value("${delivery.store.lng}")
    private Double storeLng;

    @Value("${delivery.cooldown-minutes:5}")
    private int cooldownMinutes;

    // ── Called from Kafka consumer ────────────────────────────────

    public void createTask(String orderId, String userId, String addressId) {
        if (taskRepository.findByOrderId(orderId).isPresent()) {
            log.warn("DeliveryTask already exists for orderId={} — skipping", orderId);
            return;
        }

        DeliveryTask task = DeliveryTask.builder()
                .taskId(UUID.randomUUID().toString())
                .orderId(orderId)
                .userId(userId)
                .addressId(addressId)
                .status("UNASSIGNED")
                .storeName(storeName)
                .storeAddress(storeAddress)
                .storeLat(storeLat)
                .storeLng(storeLng)
                .estimatedDeliveryAt(Instant.now().plusSeconds(30 * 60))
                .build();
        taskRepository.save(task);
        log.info("DeliveryTask created for orderId={}", orderId);

        // Immediately try to auto-assign an available partner
        tryAutoAssign(task);
    }

    public void cancelTask(String orderId) {
        taskRepository.findByOrderId(orderId).ifPresent(task -> {
            if ("PICKED_UP".equals(task.getStatus()) || "OUT_FOR_DELIVERY".equals(task.getStatus())) {
                log.warn("Cannot cancel task {} — already in transit ({})", task.getTaskId(), task.getStatus());
                return;
            }

            // Free up the partner if one was assigned
            if (task.getDeliveryPartnerId() != null) {
                try {
                    DeliveryPartner partner = partnerService.findByPartnerId(task.getDeliveryPartnerId());
                    partner.setIsAvailable(true);
                    partner.setCooldownUntil(null);
                    partnerService.savePartner(partner);
                } catch (Exception e) {
                    log.warn("Could not release partner on cancel: {}", e.getMessage());
                }
            }

            task.setStatus("CANCELLED");
            taskRepository.save(task);
            log.info("DeliveryTask cancelled for orderId={}", orderId);

            eventPublisher.publishStatusUpdated(DeliveryStatusUpdatedEvent.builder()
                    .taskId(task.getTaskId())
                    .orderId(orderId)
                    .deliveryPartnerId(task.getDeliveryPartnerId())
                    .deliveryStatus("CANCELLED")
                    .updatedAt(Instant.now())
                    .build());
        });
    }

    // ── Partner actions ───────────────────────────────────────────

    public List<DeliveryTaskResponse> getMyTasks(String partnerId) {
        return taskRepository.findByDeliveryPartnerId(partnerId)
                .stream()
                .map(DeliveryTaskResponse::from)
                .collect(Collectors.toList());
    }

    public DeliveryTaskResponse updateTaskStatus(String taskId, String partnerId, UpdateTaskStatusRequest req) {
        DeliveryTask task = findByTaskId(taskId);

        if (!partnerId.equals(task.getDeliveryPartnerId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This task is not assigned to you");
        }

        validateStatusTransition(task.getStatus(), req.getStatus());

        if ("FAILED".equals(req.getStatus()) && (req.getFailureReason() == null || req.getFailureReason().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "failureReason is required when status is FAILED");
        }

        task.setStatus(req.getStatus());

        if ("PICKED_UP".equals(req.getStatus())) {
            task.setActualPickupAt(Instant.now());

        } else if ("DELIVERED".equals(req.getStatus())) {
            task.setActualDeliveryAt(Instant.now());
            applyPostDeliveryToPartner(partnerId);

        } else if ("FAILED".equals(req.getStatus())) {
            task.setFailureReason(req.getFailureReason());
            // Free partner immediately on failure so they can take another task
            releasePartner(partnerId);
        }

        taskRepository.save(task);

        eventPublisher.publishStatusUpdated(DeliveryStatusUpdatedEvent.builder()
                .taskId(task.getTaskId())
                .orderId(task.getOrderId())
                .deliveryPartnerId(partnerId)
                .deliveryStatus(req.getStatus())
                .updatedAt(Instant.now())
                .build());

        return DeliveryTaskResponse.from(task);
    }

    // ── Customer tracking ─────────────────────────────────────────

    public DeliveryTaskResponse trackByOrderId(String orderId) {
        DeliveryTask task = taskRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Delivery task not found for order"));
        DeliveryTaskResponse resp = DeliveryTaskResponse.from(task);
        if (task.getDeliveryPartnerId() != null) {
            try {
                DeliveryPartner partner = partnerService.findByPartnerId(task.getDeliveryPartnerId());
                resp.setPartnerName(partner.getName());
                resp.setPartnerPhone(partner.getPhone());
                resp.setVehicleType(partner.getVehicleType());
                resp.setVehicleNumber(partner.getVehicleNumber());
            } catch (Exception e) {
                log.warn("Could not enrich partner details for task {}: {}", task.getTaskId(), e.getMessage());
            }
        }
        return resp;
    }

    // ── Admin ─────────────────────────────────────────────────────

    public Page<DeliveryTaskResponse> getAllTasks(String status, Pageable pageable) {
        if (status != null && !status.isBlank()) {
            return taskRepository.findByStatus(status.toUpperCase(), pageable)
                    .map(DeliveryTaskResponse::from);
        }
        return taskRepository.findAll(pageable).map(DeliveryTaskResponse::from);
    }

    public DeliveryTaskResponse getTaskById(String taskId) {
        return DeliveryTaskResponse.from(findByTaskId(taskId));
    }

    public Optional<DeliveryTaskResponse> getTaskByOrderId(String orderId) {
        return taskRepository.findByOrderId(orderId).map(DeliveryTaskResponse::from);
    }

    public DeliveryTaskResponse assignPartner(String taskId, AssignPartnerRequest req) {
        DeliveryTask task = findByTaskId(taskId);

        if ("DELIVERED".equals(task.getStatus()) || "CANCELLED".equals(task.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot assign partner to a " + task.getStatus() + " task");
        }

        DeliveryPartner partner = partnerService.findByPartnerId(req.getPartnerId());
        if (!partner.getIsActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Partner is not active");
        }

        // Free previous partner if re-assigning
        if (task.getDeliveryPartnerId() != null && !task.getDeliveryPartnerId().equals(req.getPartnerId())) {
            releasePartner(task.getDeliveryPartnerId());
        }

        partner.setIsAvailable(false);
        partnerService.savePartner(partner);

        task.setDeliveryPartnerId(req.getPartnerId());
        task.setStatus("ASSIGNED");
        taskRepository.save(task);

        eventPublisher.publishStatusUpdated(DeliveryStatusUpdatedEvent.builder()
                .taskId(task.getTaskId())
                .orderId(task.getOrderId())
                .deliveryPartnerId(req.getPartnerId())
                .deliveryStatus("ASSIGNED")
                .updatedAt(Instant.now())
                .build());

        log.info("Admin assigned task {} to partner {}", taskId, req.getPartnerId());
        return DeliveryTaskResponse.from(task);
    }

    // ── Called by ScheduledDeliveryJobService ─────────────────────

    /** Try to assign an available partner to an UNASSIGNED task. */
    public void tryAutoAssign(DeliveryTask task) {
        if (!"UNASSIGNED".equals(task.getStatus())) return;

        Optional<DeliveryPartner> available = partnerService.findAvailablePartner();
        if (available.isEmpty()) {
            log.debug("No available partner for task {} — will retry later", task.getTaskId());
            return;
        }

        DeliveryPartner partner = available.get();
        task.setDeliveryPartnerId(partner.getPartnerId());
        task.setStatus("ASSIGNED");
        taskRepository.save(task);

        eventPublisher.publishStatusUpdated(DeliveryStatusUpdatedEvent.builder()
                .taskId(task.getTaskId())
                .orderId(task.getOrderId())
                .deliveryPartnerId(partner.getPartnerId())
                .deliveryStatus("ASSIGNED")
                .updatedAt(Instant.now())
                .build());

        log.info("Auto-assigned partner {} to task {} (orderId={})",
                partner.getPartnerId(), task.getTaskId(), task.getOrderId());
    }

    /** Auto-complete a stale in-progress task (called by scheduler after 30 min). */
    public void autoCompleteTask(DeliveryTask task) {
        task.setStatus("DELIVERED");
        task.setActualDeliveryAt(Instant.now());
        taskRepository.save(task);

        if (task.getDeliveryPartnerId() != null) {
            applyPostDeliveryToPartner(task.getDeliveryPartnerId());
        }

        eventPublisher.publishStatusUpdated(DeliveryStatusUpdatedEvent.builder()
                .taskId(task.getTaskId())
                .orderId(task.getOrderId())
                .deliveryPartnerId(task.getDeliveryPartnerId())
                .deliveryStatus("DELIVERED")
                .updatedAt(Instant.now())
                .build());

        log.info("Auto-completed stale task {} for orderId={}", task.getTaskId(), task.getOrderId());
    }

    /** All UNASSIGNED tasks — for scheduler to retry assignment. */
    public List<DeliveryTask> getUnassignedTasks() {
        return taskRepository.findAllByStatus("UNASSIGNED");
    }

    /** All stale in-progress tasks past their estimated delivery time. */
    public List<DeliveryTask> getStaleInProgressTasks() {
        return taskRepository.findStaleInProgressTasks(Instant.now());
    }

    /** Tasks in a given status whose updatedAt is before the threshold — used by simulation. */
    public List<DeliveryTask> getTasksByStatusUpdatedBefore(String status, Instant before) {
        return taskRepository.findByStatusAndUpdatedAtBefore(status, before);
    }

    /** Simulation: force-assign a partner to an UNASSIGNED task without availability checks. */
    public void simulateAssignTask(DeliveryTask task, String partnerId) {
        task.setDeliveryPartnerId(partnerId);
        task.setStatus("ASSIGNED");
        taskRepository.save(task);

        eventPublisher.publishStatusUpdated(DeliveryStatusUpdatedEvent.builder()
                .taskId(task.getTaskId())
                .orderId(task.getOrderId())
                .deliveryPartnerId(partnerId)
                .deliveryStatus("ASSIGNED")
                .updatedAt(Instant.now())
                .build());

        log.info("Simulation: assigned partner {} to task {} (orderId={})",
                partnerId, task.getTaskId(), task.getOrderId());
    }

    /** Simulation: advance a task to the next status and publish Kafka event. */
    public void simulateAdvanceTask(DeliveryTask task, String newStatus) {
        task.setStatus(newStatus);

        if ("PICKED_UP".equals(newStatus)) {
            task.setActualPickupAt(Instant.now());
        } else if ("DELIVERED".equals(newStatus)) {
            task.setActualDeliveryAt(Instant.now());
            if (task.getDeliveryPartnerId() != null) {
                applyPostDeliveryToPartner(task.getDeliveryPartnerId());
            }
        }

        taskRepository.save(task);

        eventPublisher.publishStatusUpdated(DeliveryStatusUpdatedEvent.builder()
                .taskId(task.getTaskId())
                .orderId(task.getOrderId())
                .deliveryPartnerId(task.getDeliveryPartnerId())
                .deliveryStatus(newStatus)
                .updatedAt(Instant.now())
                .build());

        log.info("Simulation: task {} → {} (orderId={})", task.getTaskId(), newStatus, task.getOrderId());
    }

    // ── Private helpers ───────────────────────────────────────────

    private void applyPostDeliveryToPartner(String partnerId) {
        try {
            DeliveryPartner partner = partnerService.findByPartnerId(partnerId);
            partner.setTotalDeliveries(partner.getTotalDeliveries() + 1);
            partner.setIsAvailable(false);
            partner.setCooldownUntil(Instant.now().plusSeconds(cooldownMinutes * 60L));
            partnerService.savePartner(partner);
            log.info("Partner {} cooldown set for {} min after delivery", partnerId, cooldownMinutes);
        } catch (Exception e) {
            log.warn("Could not apply post-delivery state to partner {}: {}", partnerId, e.getMessage());
        }
    }

    private void releasePartner(String partnerId) {
        try {
            DeliveryPartner partner = partnerService.findByPartnerId(partnerId);
            partner.setIsAvailable(true);
            partner.setCooldownUntil(null);
            partnerService.savePartner(partner);
        } catch (Exception e) {
            log.warn("Could not release partner {}: {}", partnerId, e.getMessage());
        }
    }

    private DeliveryTask findByTaskId(String taskId) {
        return taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery task not found"));
    }

    private void validateStatusTransition(String current, String next) {
        boolean valid = switch (current) {
            case "ASSIGNED"         -> "PICKED_UP".equals(next) || "FAILED".equals(next);
            case "PICKED_UP"        -> "OUT_FOR_DELIVERY".equals(next) || "FAILED".equals(next);
            case "OUT_FOR_DELIVERY" -> "DELIVERED".equals(next) || "FAILED".equals(next);
            default -> false;
        };
        if (!valid) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid status transition: " + current + " → " + next);
        }
    }
}
