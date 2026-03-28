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
import java.util.Random;
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

    @Value("${delivery.cooldown-minutes}")
    private int cooldownMinutes;

    @Value("${delivery.estimated-delivery-minutes}")
    private int estimatedDeliveryMinutes;

    // Simulation: random delay ranges per transition (in seconds)
    @Value("${simulation.assigned-to-picked-up.min-sec}")
    private int assignedMinSec;
    @Value("${simulation.assigned-to-picked-up.max-sec}")
    private int assignedMaxSec;

    @Value("${simulation.picked-up-to-out-for-delivery.min-sec}")
    private int pickedUpMinSec;
    @Value("${simulation.picked-up-to-out-for-delivery.max-sec}")
    private int pickedUpMaxSec;

    @Value("${simulation.out-for-delivery-to-delivered.min-sec}")
    private int outForDeliveryMinSec;
    @Value("${simulation.out-for-delivery-to-delivered.max-sec}")
    private int outForDeliveryMaxSec;

    private static final Random RANDOM = new Random();

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
                .estimatedDeliveryAt(Instant.now().plusSeconds(estimatedDeliveryMinutes * 60L))
                .build();
        taskRepository.save(task);
        log.info("[E2E] orderId={} → DeliveryTask created taskId={} estimatedDeliveryAt={}",
                orderId, task.getTaskId(), task.getEstimatedDeliveryAt());

        // If any orders are already waiting in the queue, this order must join the back of
        // the queue immediately — do NOT attempt direct assignment (that would be unfair).
        boolean queueHasOrders = taskRepository.existsByStatus("QUEUED");
        if (queueHasOrders) {
            task.setStatus("QUEUED");
            taskRepository.save(task);
            long queueDepth = taskRepository.countByStatus("QUEUED");
            log.info("Queue non-empty — task {} for orderId={} moved to QUEUED (queue depth={})",
                    task.getTaskId(), orderId, queueDepth);
        } else {
            // Queue is empty — try direct assignment; moves to QUEUED internally if no partner free
            tryAutoAssign(task);
        }
    }

    public void cancelTask(String orderId) {
        taskRepository.findByOrderId(orderId).ifPresent(task -> {
            if ("PICKED_UP".equals(task.getStatus()) || "OUT_FOR_DELIVERY".equals(task.getStatus())) {
                log.warn("Cannot cancel task {} — already in transit ({})", task.getTaskId(), task.getStatus());
                return;
            }
            // QUEUED tasks have no partner assigned yet — just cancel, no partner to free
            if ("QUEUED".equals(task.getStatus())) {
                task.setStatus("CANCELLED");
                taskRepository.save(task);
                log.info("Cancelled QUEUED task for orderId={}", orderId);
                eventPublisher.publishStatusUpdated(DeliveryStatusUpdatedEvent.builder()
                        .taskId(task.getTaskId())
                        .orderId(orderId)
                        .deliveryPartnerId(null)
                        .deliveryStatus("CANCELLED")
                        .updatedAt(Instant.now())
                        .build());
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
            // Ensure simulation can advance this task — set timer so scheduler picks it up
            task.setNextStatusAdvanceAt(randomAdvanceTime(pickedUpMinSec, pickedUpMaxSec));

        } else if ("OUT_FOR_DELIVERY".equals(req.getStatus())) {
            task.setNextStatusAdvanceAt(randomAdvanceTime(outForDeliveryMinSec, outForDeliveryMaxSec));

        } else if ("DELIVERED".equals(req.getStatus())) {
            task.setActualDeliveryAt(Instant.now());
            task.setNextStatusAdvanceAt(null);
            applyPostDeliveryToPartner(partnerId);

        } else if ("FAILED".equals(req.getStatus())) {
            task.setFailureReason(req.getFailureReason());
            task.setNextStatusAdvanceAt(null);
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

        if ("QUEUED".equals(task.getStatus())) {
            resp.setQueuePosition(getQueuePosition(task.getTaskId()));
        }

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

    /**
     * Try to assign an available partner to an UNASSIGNED task.
     * If no partner is available, moves the task to QUEUED so the scheduler
     * can serve it in createdAt-order once a partner frees up.
     */
    public void tryAutoAssign(DeliveryTask task) {
        if (!"UNASSIGNED".equals(task.getStatus())) return;

        Optional<DeliveryPartner> available = partnerService.findAvailablePartner();
        if (available.isEmpty()) {
            // No partner free — move to QUEUED so the priority queue scheduler picks it up
            task.setStatus("QUEUED");
            taskRepository.save(task);
            log.info("No partner available — task {} for orderId={} moved to QUEUED",
                    task.getTaskId(), task.getOrderId());
            return;
        }

        assignPartnerToTask(task, available.get());
    }

    /**
     * Assign the oldest-queued task to the given partner.
     * Called by the scheduler after acquiring an available partner.
     */
    public boolean assignNextQueued(DeliveryPartner partner) {
        List<DeliveryTask> queue = taskRepository.findAllByStatusOrderByCreatedAtAsc("QUEUED");
        if (queue.isEmpty()) return false;

        DeliveryTask oldest = queue.get(0);
        assignPartnerToTask(oldest, partner);
        return true;
    }

    /** All QUEUED tasks sorted by createdAt ASC — used by scheduler. */
    public List<DeliveryTask> getQueuedTasksInOrder() {
        return taskRepository.findAllByStatusOrderByCreatedAtAsc("QUEUED");
    }

    /** Queue depth — how many orders are currently waiting for a partner. */
    public long getQueueDepth() {
        return taskRepository.countByStatus("QUEUED");
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

        log.info("[E2E] orderId={} → taskId={} AUTO-COMPLETED (stale past ETA)", task.getOrderId(), task.getTaskId());
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

    /** Tasks whose random nextStatusAdvanceAt timer has expired — used by simulation scheduler. */
    public List<DeliveryTask> getTasksReadyToAdvance(String status) {
        Instant now = Instant.now();
        List<DeliveryTask> ready = taskRepository.findReadyToAdvance(status, now);
        long total = taskRepository.countByStatus(status);
        if (total > 0) {
            log.debug("Simulation check: status={} ready={}/{}", status, ready.size(), total);
        }
        return ready;
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
            // Random delay before OUT_FOR_DELIVERY
            Instant pickedUpAdvance = randomAdvanceTime(pickedUpMinSec, pickedUpMaxSec);
            task.setNextStatusAdvanceAt(pickedUpAdvance);
            log.info("Simulation: task {} PICKED_UP — nextStatusAdvanceAt={} (now={}, range={}..{}s)",
                    task.getTaskId(), pickedUpAdvance, Instant.now(), pickedUpMinSec, pickedUpMaxSec);

        } else if ("OUT_FOR_DELIVERY".equals(newStatus)) {
            // Random delay before DELIVERED
            Instant outAdvance = randomAdvanceTime(outForDeliveryMinSec, outForDeliveryMaxSec);
            task.setNextStatusAdvanceAt(outAdvance);
            log.info("Simulation: task {} OUT_FOR_DELIVERY — nextStatusAdvanceAt={} (now={}, range={}..{}s)",
                    task.getTaskId(), outAdvance, Instant.now(), outForDeliveryMinSec, outForDeliveryMaxSec);

        } else if ("DELIVERED".equals(newStatus)) {
            task.setActualDeliveryAt(Instant.now());
            task.setNextStatusAdvanceAt(null);   // terminal — no further advance
            log.info("[E2E] orderId={} → taskId={} DELIVERED by partner={}",
                    task.getOrderId(), task.getTaskId(), task.getDeliveryPartnerId());
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

    private void assignPartnerToTask(DeliveryTask task, DeliveryPartner partner) {
        partner.setIsAvailable(false);
        partnerService.savePartner(partner);

        task.setDeliveryPartnerId(partner.getPartnerId());
        task.setStatus("ASSIGNED");
        // Random delay before partner picks up from store (ASSIGNED → PICKED_UP)
        Instant nextAdvance = randomAdvanceTime(assignedMinSec, assignedMaxSec);
        task.setNextStatusAdvanceAt(nextAdvance);
        taskRepository.save(task);

        eventPublisher.publishStatusUpdated(DeliveryStatusUpdatedEvent.builder()
                .taskId(task.getTaskId())
                .orderId(task.getOrderId())
                .deliveryPartnerId(partner.getPartnerId())
                .deliveryStatus("ASSIGNED")
                .updatedAt(Instant.now())
                .build());

        log.info("[E2E] orderId={} → taskId={} ASSIGNED to partner={} nextAdvanceAt={} (ASSIGNED→PICKED_UP in {}..{}s)",
                task.getOrderId(), task.getTaskId(), partner.getPartnerId(), nextAdvance, assignedMinSec, assignedMaxSec);
    }

    private void applyPostDeliveryToPartner(String partnerId) {
        try {
            DeliveryPartner partner = partnerService.findByPartnerId(partnerId);
            partner.setTotalDeliveries(partner.getTotalDeliveries() + 1);
            partner.setIsAvailable(false);
            Instant now = Instant.now();
            Instant cooldownUntil = now.plusSeconds(cooldownMinutes * 60L);
            partner.setCooldownUntil(cooldownUntil);
            partnerService.savePartner(partner);
            log.info("[E2E] Partner {} cooldown: cooldownMinutes={} now={} cooldownUntil={} (diff={}s)",
                    partnerId, cooldownMinutes, now, cooldownUntil,
                    cooldownUntil.getEpochSecond() - now.getEpochSecond());
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

    /** Returns 1-based position of this task in the QUEUED list (by createdAt). 0 if not queued. */
    public int getQueuePosition(String taskId) {
        List<DeliveryTask> queue = taskRepository.findAllByStatusOrderByCreatedAtAsc("QUEUED");
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getTaskId().equals(taskId)) return i + 1;
        }
        return 0;
    }

    /** Returns a random Instant between now+minSec and now+maxSec. */
    private Instant randomAdvanceTime(int minSec, int maxSec) {
        int delaySec = minSec + RANDOM.nextInt(maxSec - minSec + 1);
        return Instant.now().plusSeconds(delaySec);
    }
}
