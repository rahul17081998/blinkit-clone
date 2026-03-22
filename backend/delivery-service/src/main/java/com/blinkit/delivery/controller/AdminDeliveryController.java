package com.blinkit.delivery.controller;

import com.blinkit.common.dto.ApiResponse;
import com.blinkit.delivery.dto.request.AssignPartnerRequest;
import com.blinkit.delivery.dto.response.DeliveryPartnerResponse;
import com.blinkit.delivery.dto.response.DeliveryTaskResponse;
import com.blinkit.delivery.service.DeliveryPartnerService;
import com.blinkit.delivery.service.DeliveryTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/delivery/admin")
@RequiredArgsConstructor
public class AdminDeliveryController {

    private final DeliveryTaskService taskService;
    private final DeliveryPartnerService partnerService;

    // ── Tasks ────────────────────────────────────────────────────

    @GetMapping("/tasks")
    public ResponseEntity<ApiResponse<Page<DeliveryTaskResponse>>> getAllTasks(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("Tasks fetched", taskService.getAllTasks(status, pageable)));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<ApiResponse<DeliveryTaskResponse>> getTask(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String taskId) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok("Task fetched", taskService.getTaskById(taskId)));
    }

    @GetMapping("/tasks/by-order/{orderId}")
    public ResponseEntity<ApiResponse<DeliveryTaskResponse>> getTaskByOrderId(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String orderId) {
        requireAdmin(role);
        return taskService.getTaskByOrderId(orderId)
                .map(t -> ResponseEntity.ok(ApiResponse.ok("Task fetched", t)))
                .orElse(ResponseEntity.ok(ApiResponse.ok("No delivery task found", null)));
    }

    @PostMapping("/tasks/{taskId}/assign")
    public ResponseEntity<ApiResponse<DeliveryTaskResponse>> assignPartner(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String taskId,
            @Valid @RequestBody AssignPartnerRequest req) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok("Partner assigned", taskService.assignPartner(taskId, req)));
    }

    // ── Partners ─────────────────────────────────────────────────

    @GetMapping("/partners")
    public ResponseEntity<ApiResponse<Page<DeliveryPartnerResponse>>> getAllPartners(
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdmin(role);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.ok("Partners fetched", partnerService.getAllPartners(pageable)));
    }

    @GetMapping("/partners/{partnerId}")
    public ResponseEntity<ApiResponse<DeliveryPartnerResponse>> getPartner(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String partnerId) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok("Partner fetched", partnerService.getPartnerById(partnerId)));
    }

    @PutMapping("/partners/{partnerId}/toggle")
    public ResponseEntity<ApiResponse<DeliveryPartnerResponse>> togglePartnerActive(
            @RequestHeader("X-User-Role") String role,
            @PathVariable String partnerId) {
        requireAdmin(role);
        return ResponseEntity.ok(ApiResponse.ok("Partner status updated", partnerService.togglePartnerActive(partnerId)));
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }
}
