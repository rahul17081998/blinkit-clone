package com.blinkit.delivery.service;

import com.blinkit.delivery.entity.DeliveryPartner;
import com.blinkit.delivery.entity.DeliveryTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled jobs that keep the delivery lifecycle reliable:
 *
 *  1. retryUnassignedTasks    — every 30 s: try to assign a real partner to UNASSIGNED tasks
 *  2. releaseCooldownPartners — every 60 s: mark partners available after cooldown expires
 *  3. autoCompleteStale       — every 5 m:  auto-mark DELIVERED for tasks past their ETA
 *  4. simulateDeliveryProgress — every 30 s: advance task statuses for demo/simulation
 *     UNASSIGNED→ASSIGNED (after 30 s), ASSIGNED→PICKED_UP (after 60 s),
 *     PICKED_UP→OUT_FOR_DELIVERY (after 60 s), OUT_FOR_DELIVERY→DELIVERED (after 60 s)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledDeliveryJobService {

    private final DeliveryTaskService    taskService;
    private final DeliveryPartnerService partnerService;

    // ── 1. Retry assignment for UNASSIGNED tasks ──────────────────

    @Scheduled(fixedDelay = 30_000)   // every 30 seconds
    public void retryUnassignedTasks() {
        List<DeliveryTask> unassigned = taskService.getUnassignedTasks();
        if (unassigned.isEmpty()) return;

        log.debug("Scheduler: {} UNASSIGNED task(s) found — attempting assignment", unassigned.size());
        for (DeliveryTask task : unassigned) {
            taskService.tryAutoAssign(task);
        }
    }

    // ── 2. Release partners whose cooldown has expired ────────────

    @Scheduled(fixedDelay = 60_000)   // every 60 seconds
    public void releaseCooldownPartners() {
        List<DeliveryPartner> expired = partnerService.findPartnersWithExpiredCooldown();
        if (expired.isEmpty()) return;

        log.info("Scheduler: releasing {} partner(s) from cooldown", expired.size());
        for (DeliveryPartner partner : expired) {
            partner.setIsAvailable(true);
            partner.setCooldownUntil(null);
            partnerService.savePartner(partner);
            log.info("Partner {} is now available again after cooldown", partner.getPartnerId());
        }
    }

    // ── 3. Auto-complete tasks that are past their delivery ETA ───

    @Scheduled(fixedDelay = 300_000)  // every 5 minutes
    public void autoCompleteStale() {
        List<DeliveryTask> stale = taskService.getStaleInProgressTasks();
        if (stale.isEmpty()) return;

        log.warn("Scheduler: {} stale in-progress task(s) past ETA — auto-completing", stale.size());
        for (DeliveryTask task : stale) {
            taskService.autoCompleteTask(task);
        }
    }

    // ── 4. Simulation: advance delivery task statuses automatically ──

    @Scheduled(fixedDelay = 30_000)   // every 30 seconds
    public void simulateDeliveryProgress() {
        Instant now = Instant.now();

        // UNASSIGNED → ASSIGNED  (task created > 30 s ago and still unassigned)
        List<DeliveryTask> unassigned = taskService.getTasksByStatusUpdatedBefore(
                "UNASSIGNED", now.minusSeconds(30));
        if (!unassigned.isEmpty()) {
            DeliveryPartner simPartner = partnerService.findOrCreateSimulationPartner();
            for (DeliveryTask t : unassigned) {
                taskService.simulateAssignTask(t, simPartner.getPartnerId());
            }
            log.info("Simulation: assigned {} UNASSIGNED task(s) to partner {}",
                    unassigned.size(), simPartner.getPartnerId());
        }

        // ASSIGNED → PICKED_UP  (assigned > 60 s ago)
        List<DeliveryTask> assigned = taskService.getTasksByStatusUpdatedBefore(
                "ASSIGNED", now.minusSeconds(60));
        for (DeliveryTask t : assigned) {
            taskService.simulateAdvanceTask(t, "PICKED_UP");
        }
        if (!assigned.isEmpty()) log.info("Simulation: {} ASSIGNED → PICKED_UP", assigned.size());

        // PICKED_UP → OUT_FOR_DELIVERY  (picked up > 60 s ago)
        List<DeliveryTask> pickedUp = taskService.getTasksByStatusUpdatedBefore(
                "PICKED_UP", now.minusSeconds(60));
        for (DeliveryTask t : pickedUp) {
            taskService.simulateAdvanceTask(t, "OUT_FOR_DELIVERY");
        }
        if (!pickedUp.isEmpty()) log.info("Simulation: {} PICKED_UP → OUT_FOR_DELIVERY", pickedUp.size());

        // OUT_FOR_DELIVERY → DELIVERED  (out > 60 s ago)
        List<DeliveryTask> outForDelivery = taskService.getTasksByStatusUpdatedBefore(
                "OUT_FOR_DELIVERY", now.minusSeconds(60));
        for (DeliveryTask t : outForDelivery) {
            taskService.simulateAdvanceTask(t, "DELIVERED");
        }
        if (!outForDelivery.isEmpty()) log.info("Simulation: {} OUT_FOR_DELIVERY → DELIVERED", outForDelivery.size());
    }
}
