package com.blinkit.delivery.service;

import com.blinkit.delivery.dto.request.AssignPartnerRequest;
import com.blinkit.delivery.dto.request.UpdateTaskStatusRequest;
import com.blinkit.delivery.dto.response.DeliveryTaskResponse;
import com.blinkit.delivery.entity.DeliveryPartner;
import com.blinkit.delivery.entity.DeliveryTask;
import com.blinkit.delivery.kafka.DeliveryEventPublisher;
import com.blinkit.delivery.repository.DeliveryTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryTaskServiceTest {

    @Mock DeliveryTaskRepository taskRepository;
    @Mock DeliveryPartnerService partnerService;
    @Mock DeliveryEventPublisher eventPublisher;
    @InjectMocks DeliveryTaskService taskService;

    private static final String TASK_ID    = "task-uuid-001";
    private static final String ORDER_ID   = "order-uuid-001";
    private static final String USER_ID    = "user-uuid-001";
    private static final String ADDRESS_ID = "addr-uuid-001";
    private static final String PARTNER_ID = "partner-uuid-001";

    private DeliveryTask unassignedTask;
    private DeliveryTask assignedTask;
    private DeliveryPartner activePartner;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(taskService, "storeName",       "Blinkit Dark Store - Bangalore");
        ReflectionTestUtils.setField(taskService, "storeAddress",    "Koramangala, Bangalore");
        ReflectionTestUtils.setField(taskService, "storeLat",        12.9352);
        ReflectionTestUtils.setField(taskService, "storeLng",        77.6245);
        ReflectionTestUtils.setField(taskService, "cooldownMinutes", 5);

        unassignedTask = DeliveryTask.builder()
                .taskId(TASK_ID).orderId(ORDER_ID).userId(USER_ID).addressId(ADDRESS_ID)
                .status("UNASSIGNED").storeName("Blinkit Dark Store - Bangalore")
                .storeAddress("Koramangala, Bangalore").storeLat(12.9352).storeLng(77.6245)
                .estimatedDeliveryAt(Instant.now().plusSeconds(1800))
                .build();

        assignedTask = DeliveryTask.builder()
                .taskId(TASK_ID).orderId(ORDER_ID).userId(USER_ID).addressId(ADDRESS_ID)
                .deliveryPartnerId(PARTNER_ID).status("ASSIGNED")
                .build();

        activePartner = DeliveryPartner.builder()
                .partnerId(PARTNER_ID).name("Ravi Kumar")
                .isActive(true).isAvailable(true).totalDeliveries(0)
                .build();
    }

    // ── createTask ────────────────────────────────────────────────

    @Test
    @DisplayName("createTask — creates task and auto-assigns when queue empty and partner available")
    void createTask_withAvailablePartner() {
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(taskRepository.save(any(DeliveryTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.existsByStatus("QUEUED")).thenReturn(false); // queue is empty
        when(partnerService.findAvailablePartner()).thenReturn(Optional.of(activePartner));

        taskService.createTask(ORDER_ID, USER_ID, ADDRESS_ID);

        ArgumentCaptor<DeliveryTask> captor = ArgumentCaptor.forClass(DeliveryTask.class);
        verify(taskRepository, times(2)).save(captor.capture()); // once UNASSIGNED, once ASSIGNED
        DeliveryTask saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo("ASSIGNED");
        assertThat(saved.getDeliveryPartnerId()).isEqualTo(PARTNER_ID);
        verify(eventPublisher).publishStatusUpdated(argThat(e -> "ASSIGNED".equals(e.getDeliveryStatus())));
    }

    @Test
    @DisplayName("createTask — moves to QUEUED when queue empty but no partner available")
    void createTask_noPartnerAvailable() {
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(taskRepository.save(any(DeliveryTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.existsByStatus("QUEUED")).thenReturn(false); // queue is empty
        when(partnerService.findAvailablePartner()).thenReturn(Optional.empty());

        taskService.createTask(ORDER_ID, USER_ID, ADDRESS_ID);

        ArgumentCaptor<DeliveryTask> captor = ArgumentCaptor.forClass(DeliveryTask.class);
        verify(taskRepository, times(2)).save(captor.capture()); // once UNASSIGNED, once QUEUED
        assertThat(captor.getValue().getStatus()).isEqualTo("QUEUED");
        verify(eventPublisher, never()).publishStatusUpdated(any());
    }

    @Test
    @DisplayName("createTask — goes directly to QUEUED when other orders already waiting")
    void createTask_queueNonEmpty_skipsAssignment() {
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());
        when(taskRepository.save(any(DeliveryTask.class))).thenAnswer(inv -> inv.getArgument(0));
        when(taskRepository.existsByStatus("QUEUED")).thenReturn(true); // queue has orders
        when(taskRepository.countByStatus("QUEUED")).thenReturn(2L);

        taskService.createTask(ORDER_ID, USER_ID, ADDRESS_ID);

        ArgumentCaptor<DeliveryTask> captor = ArgumentCaptor.forClass(DeliveryTask.class);
        verify(taskRepository, times(2)).save(captor.capture()); // once UNASSIGNED, once QUEUED
        assertThat(captor.getValue().getStatus()).isEqualTo("QUEUED");
        // Must NOT try to find a partner — existing queued orders have priority
        verify(partnerService, never()).findAvailablePartner();
        verify(eventPublisher, never()).publishStatusUpdated(any());
    }

    @Test
    @DisplayName("createTask — idempotent: skips when task already exists")
    void createTask_alreadyExists() {
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(unassignedTask));

        taskService.createTask(ORDER_ID, USER_ID, ADDRESS_ID);

        verify(taskRepository, never()).save(any());
        verify(partnerService, never()).findAvailablePartner();
    }

    // ── cancelTask ────────────────────────────────────────────────

    @Test
    @DisplayName("cancelTask — cancels UNASSIGNED task and publishes event")
    void cancelTask_unassigned() {
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(unassignedTask));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskService.cancelTask(ORDER_ID);

        assertThat(unassignedTask.getStatus()).isEqualTo("CANCELLED");
        verify(eventPublisher).publishStatusUpdated(argThat(e -> "CANCELLED".equals(e.getDeliveryStatus())));
    }

    @Test
    @DisplayName("cancelTask — frees partner when ASSIGNED task is cancelled")
    void cancelTask_releasesPartner() {
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(assignedTask));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(partnerService.findByPartnerId(PARTNER_ID)).thenReturn(activePartner);

        taskService.cancelTask(ORDER_ID);

        assertThat(assignedTask.getStatus()).isEqualTo("CANCELLED");
        verify(partnerService).savePartner(argThat(p -> p.getIsAvailable() && p.getCooldownUntil() == null));
    }

    @Test
    @DisplayName("cancelTask — skips cancel when task is PICKED_UP")
    void cancelTask_blockedWhenPickedUp() {
        unassignedTask.setStatus("PICKED_UP");
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(unassignedTask));

        taskService.cancelTask(ORDER_ID);

        verify(taskRepository, never()).save(any());
        verify(eventPublisher, never()).publishStatusUpdated(any());
    }

    @Test
    @DisplayName("cancelTask — no-op when task not found")
    void cancelTask_notFound() {
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        taskService.cancelTask(ORDER_ID);

        verify(taskRepository, never()).save(any());
    }

    // ── tryAutoAssign ─────────────────────────────────────────────

    @Test
    @DisplayName("tryAutoAssign — assigns partner and publishes ASSIGNED event")
    void tryAutoAssign_success() {
        when(partnerService.findAvailablePartner()).thenReturn(Optional.of(activePartner));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        taskService.tryAutoAssign(unassignedTask);

        assertThat(unassignedTask.getStatus()).isEqualTo("ASSIGNED");
        assertThat(unassignedTask.getDeliveryPartnerId()).isEqualTo(PARTNER_ID);
        verify(eventPublisher).publishStatusUpdated(argThat(e -> "ASSIGNED".equals(e.getDeliveryStatus())));
    }

    @Test
    @DisplayName("tryAutoAssign — no-op when task is not UNASSIGNED")
    void tryAutoAssign_skipsNonUnassigned() {
        taskService.tryAutoAssign(assignedTask);

        verify(partnerService, never()).findAvailablePartner();
        verify(taskRepository, never()).save(any());
    }

    // ── assignPartner (admin) ─────────────────────────────────────

    @Test
    @DisplayName("assignPartner — assigns active partner and marks unavailable")
    void assignPartner_success() {
        AssignPartnerRequest req = new AssignPartnerRequest();
        req.setPartnerId(PARTNER_ID);

        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(unassignedTask));
        when(partnerService.findByPartnerId(PARTNER_ID)).thenReturn(activePartner);
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeliveryTaskResponse result = taskService.assignPartner(TASK_ID, req);

        assertThat(result.getStatus()).isEqualTo("ASSIGNED");
        verify(partnerService).savePartner(argThat(p -> !p.getIsAvailable()));
        verify(eventPublisher).publishStatusUpdated(argThat(e -> "ASSIGNED".equals(e.getDeliveryStatus())));
    }

    @Test
    @DisplayName("assignPartner — throws 400 when partner is inactive")
    void assignPartner_inactivePartner() {
        activePartner.setIsActive(false);
        AssignPartnerRequest req = new AssignPartnerRequest();
        req.setPartnerId(PARTNER_ID);

        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(unassignedTask));
        when(partnerService.findByPartnerId(PARTNER_ID)).thenReturn(activePartner);

        assertThatThrownBy(() -> taskService.assignPartner(TASK_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("assignPartner — throws 400 when task is CANCELLED")
    void assignPartner_cancelledTask() {
        unassignedTask.setStatus("CANCELLED");
        AssignPartnerRequest req = new AssignPartnerRequest();
        req.setPartnerId(PARTNER_ID);

        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(unassignedTask));

        assertThatThrownBy(() -> taskService.assignPartner(TASK_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── updateTaskStatus ──────────────────────────────────────────

    @Test
    @DisplayName("updateTaskStatus — ASSIGNED → PICKED_UP sets actualPickupAt")
    void updateStatus_toPickedUp() {
        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest();
        req.setStatus("PICKED_UP");

        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(assignedTask));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeliveryTaskResponse result = taskService.updateTaskStatus(TASK_ID, PARTNER_ID, req);

        assertThat(result.getStatus()).isEqualTo("PICKED_UP");
        assertThat(assignedTask.getActualPickupAt()).isNotNull();
        verify(eventPublisher).publishStatusUpdated(argThat(e -> "PICKED_UP".equals(e.getDeliveryStatus())));
    }

    @Test
    @DisplayName("updateTaskStatus — DELIVERED sets cooldown on partner")
    void updateStatus_delivered_setsCooldown() {
        assignedTask.setStatus("OUT_FOR_DELIVERY");
        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest();
        req.setStatus("DELIVERED");

        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(assignedTask));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(partnerService.findByPartnerId(PARTNER_ID)).thenReturn(activePartner);

        DeliveryTaskResponse result = taskService.updateTaskStatus(TASK_ID, PARTNER_ID, req);

        assertThat(result.getStatus()).isEqualTo("DELIVERED");
        assertThat(assignedTask.getActualDeliveryAt()).isNotNull();
        verify(partnerService).savePartner(argThat(p ->
                !p.getIsAvailable() && p.getCooldownUntil() != null && p.getTotalDeliveries() == 1));
    }

    @Test
    @DisplayName("updateTaskStatus — FAILED releases partner immediately")
    void updateStatus_failed_releasesPartner() {
        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest();
        req.setStatus("FAILED");
        req.setFailureReason("Customer not home");

        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(assignedTask));
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(partnerService.findByPartnerId(PARTNER_ID)).thenReturn(activePartner);

        DeliveryTaskResponse result = taskService.updateTaskStatus(TASK_ID, PARTNER_ID, req);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        verify(partnerService).savePartner(argThat(p -> p.getIsAvailable() && p.getCooldownUntil() == null));
    }

    @Test
    @DisplayName("updateTaskStatus — throws 400 when FAILED without failureReason")
    void updateStatus_failed_noReason() {
        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest();
        req.setStatus("FAILED");

        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(assignedTask));

        assertThatThrownBy(() -> taskService.updateTaskStatus(TASK_ID, PARTNER_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    @DisplayName("updateTaskStatus — throws 403 when wrong partner")
    void updateStatus_wrongPartner() {
        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest();
        req.setStatus("PICKED_UP");

        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(assignedTask));

        assertThatThrownBy(() -> taskService.updateTaskStatus(TASK_ID, "wrong-partner", req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    @DisplayName("updateTaskStatus — throws 400 for invalid transition ASSIGNED → DELIVERED")
    void updateStatus_invalidTransition() {
        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest();
        req.setStatus("DELIVERED");

        when(taskRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(assignedTask));

        assertThatThrownBy(() -> taskService.updateTaskStatus(TASK_ID, PARTNER_ID, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    // ── autoCompleteTask ──────────────────────────────────────────

    @Test
    @DisplayName("autoCompleteTask — marks DELIVERED, sets delivery time, publishes event")
    void autoCompleteTask_success() {
        when(taskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(partnerService.findByPartnerId(PARTNER_ID)).thenReturn(activePartner);

        taskService.autoCompleteTask(assignedTask);

        assertThat(assignedTask.getStatus()).isEqualTo("DELIVERED");
        assertThat(assignedTask.getActualDeliveryAt()).isNotNull();
        verify(eventPublisher).publishStatusUpdated(argThat(e -> "DELIVERED".equals(e.getDeliveryStatus())));
        verify(partnerService).savePartner(argThat(p -> p.getCooldownUntil() != null));
    }

    // ── trackByOrderId ────────────────────────────────────────────

    @Test
    @DisplayName("trackByOrderId — returns task when found")
    void trackByOrderId_found() {
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(unassignedTask));

        DeliveryTaskResponse result = taskService.trackByOrderId(ORDER_ID);

        assertThat(result.getOrderId()).isEqualTo(ORDER_ID);
        assertThat(result.getStatus()).isEqualTo("UNASSIGNED");
    }

    @Test
    @DisplayName("trackByOrderId — throws 404 when not found")
    void trackByOrderId_notFound() {
        when(taskRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.trackByOrderId(ORDER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── getAllTasks (admin) ────────────────────────────────────────

    @Test
    @DisplayName("getAllTasks — filters by status when provided")
    void getAllTasks_withFilter() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<DeliveryTask> page = new PageImpl<>(List.of(unassignedTask), pageable, 1);
        when(taskRepository.findByStatus("UNASSIGNED", pageable)).thenReturn(page);

        Page<DeliveryTaskResponse> result = taskService.getAllTasks("UNASSIGNED", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(taskRepository).findByStatus("UNASSIGNED", pageable);
        verify(taskRepository, never()).findAll(pageable);
    }

    @Test
    @DisplayName("getAllTasks — returns all when no filter")
    void getAllTasks_noFilter() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<DeliveryTask> page = new PageImpl<>(List.of(unassignedTask, assignedTask), pageable, 2);
        when(taskRepository.findAll(pageable)).thenReturn(page);

        Page<DeliveryTaskResponse> result = taskService.getAllTasks(null, pageable);

        assertThat(result.getContent()).hasSize(2);
        verify(taskRepository).findAll(pageable);
    }

    // ── getMyTasks ────────────────────────────────────────────────

    @Test
    @DisplayName("getMyTasks — returns tasks assigned to partner")
    void getMyTasks_returnsAssigned() {
        when(taskRepository.findByDeliveryPartnerId(PARTNER_ID)).thenReturn(List.of(assignedTask));

        List<DeliveryTaskResponse> result = taskService.getMyTasks(PARTNER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDeliveryPartnerId()).isEqualTo(PARTNER_ID);
    }
}
