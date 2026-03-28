package com.blinkit.delivery.service;

import com.blinkit.delivery.entity.DeliveryPartner;
import com.blinkit.delivery.entity.DeliveryTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Scheduled jobs that keep the delivery lifecycle reliable:
 *
 *  1. processDeliveryQueue    — every 30 s: drain the QUEUED priority queue (oldest createdAt first),
 *                               assigning one available partner per task until no partner or no queue
 *  2. releaseCooldownPartners — every 60 s: mark partners available after cooldown expires;
 *                               immediately triggers a queue drain after releasing
 *  3. autoCompleteStale       — every 5 m:  auto-mark DELIVERED for tasks past their ETA
 *  4. simulateDeliveryProgress — every 30 s: advance task statuses for demo/simulation
 *     QUEUED→ASSIGNED (after 30 s via queue drain), ASSIGNED→PICKED_UP (after 60 s),
 *     PICKED_UP→OUT_FOR_DELIVERY (after 60 s), OUT_FOR_DELIVERY→DELIVERED (after 60 s)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledDeliveryJobService {

    private final DeliveryTaskService    taskService;
    private final DeliveryPartnerService partnerService;

    @Value("${scheduling.retry-unassigned-delay-ms}")
    private long retryUnassignedDelayMs;

    @Value("${scheduling.release-cooldown-delay-ms}")
    private long releaseCooldownDelayMs;

    @Value("${scheduling.auto-complete-stale-delay-ms}")
    private long autoCompleteStaleDelayMs;

    @Value("${scheduling.simulate-progress-delay-ms}")
    private long simulateProgressDelayMs;

    // ── 1. Priority queue drain — assign oldest queued orders first ──

    @Scheduled(fixedDelayString = "${scheduling.retry-unassigned-delay-ms}")
    public void processDeliveryQueue() {
        List<DeliveryTask> queue = taskService.getQueuedTasksInOrder(); // sorted by createdAt ASC
        if (queue.isEmpty()) return;

        log.debug("Scheduler: {} task(s) in QUEUED state — draining priority queue", queue.size());
        int assigned = 0;
        for (DeliveryTask task : queue) {
            Optional<DeliveryPartner> partner = partnerService.findAvailablePartner();
            if (partner.isEmpty()) {
                log.debug("Scheduler: no available partner — {} task(s) remain queued", queue.size() - assigned);
                break; // No partners left, stop — remaining tasks stay queued in order
            }
            taskService.assignNextQueued(partner.get());
            assigned++;
        }
        if (assigned > 0) log.info("Scheduler: assigned {} queued task(s) to partners", assigned);
    }

    // ── 2. Release partners whose cooldown has expired ────────────

    @Scheduled(fixedDelayString = "${scheduling.release-cooldown-delay-ms}")
    public void releaseCooldownPartners() {
        List<DeliveryPartner> expired = partnerService.findPartnersWithExpiredCooldown();
        if (expired.isEmpty()) return;

        log.info("Scheduler: releasing {} partner(s) from cooldown", expired.size());
        for (DeliveryPartner partner : expired) {
            partner.setIsAvailable(true);
            partner.setCooldownUntil(null);
            partnerService.savePartner(partner);
            log.info("Partner {} is now available — checking queue", partner.getPartnerId());
        }

        // Partners just freed up — immediately try to drain the queue
        // so waiting orders don't have to wait for the next 30s scheduler tick
        long queueDepth = taskService.getQueueDepth();
        if (queueDepth > 0) {
            log.info("Scheduler: {} order(s) in queue — draining after cooldown release", queueDepth);
            processDeliveryQueue();
        }
    }

    // ── 3. Auto-complete tasks that are past their delivery ETA ───

    @Scheduled(fixedDelayString = "${scheduling.auto-complete-stale-delay-ms}")
    public void autoCompleteStale() {
        List<DeliveryTask> stale = taskService.getStaleInProgressTasks();
        if (stale.isEmpty()) return;

        log.warn("Scheduler: {} stale in-progress task(s) past ETA — auto-completing", stale.size());
        for (DeliveryTask task : stale) {
            taskService.autoCompleteTask(task);
        }
    }

    // ── 4. Simulation: advance delivery task statuses automatically ──

    @Scheduled(fixedDelayString = "${scheduling.simulate-progress-delay-ms}")
    public void simulateDeliveryProgress() {

        // QUEUED → ASSIGNED  (simulation: drain priority queue; processDeliveryQueue handles ordering)
        // The real queue drain happens in processDeliveryQueue() — simulation just piggybacks on it.
        long queueDepth = taskService.getQueueDepth();
        if (queueDepth > 0) {
            log.info("Simulation: {} task(s) in queue — triggering queue drain", queueDepth);
            processDeliveryQueue();
        }

        // ASSIGNED → PICKED_UP  (random delay set per task in nextStatusAdvanceAt)
        List<DeliveryTask> assignedTasks = taskService.getTasksReadyToAdvance("ASSIGNED");
        for (DeliveryTask t : assignedTasks) {
            taskService.simulateAdvanceTask(t, "PICKED_UP");
        }
        if (!assignedTasks.isEmpty()) log.info("Simulation: {} ASSIGNED → PICKED_UP", assignedTasks.size());

        // PICKED_UP → OUT_FOR_DELIVERY  (random delay set per task in nextStatusAdvanceAt)
        List<DeliveryTask> pickedUp = taskService.getTasksReadyToAdvance("PICKED_UP");
        for (DeliveryTask t : pickedUp) {
            taskService.simulateAdvanceTask(t, "OUT_FOR_DELIVERY");
        }
        if (!pickedUp.isEmpty()) log.info("Simulation: {} PICKED_UP → OUT_FOR_DELIVERY", pickedUp.size());

        // OUT_FOR_DELIVERY → DELIVERED  (random delay set per task in nextStatusAdvanceAt)
        List<DeliveryTask> outForDelivery = taskService.getTasksReadyToAdvance("OUT_FOR_DELIVERY");
        for (DeliveryTask t : outForDelivery) {
            taskService.simulateAdvanceTask(t, "DELIVERED");
        }
        if (!outForDelivery.isEmpty()) log.info("Simulation: {} OUT_FOR_DELIVERY → DELIVERED", outForDelivery.size());
    }
}
