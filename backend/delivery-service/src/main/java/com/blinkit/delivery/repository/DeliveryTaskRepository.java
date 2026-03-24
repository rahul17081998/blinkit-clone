package com.blinkit.delivery.repository;

import com.blinkit.delivery.entity.DeliveryTask;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeliveryTaskRepository extends MongoRepository<DeliveryTask, String> {

    Optional<DeliveryTask> findByTaskId(String taskId);

    Optional<DeliveryTask> findByOrderId(String orderId);

    List<DeliveryTask> findByDeliveryPartnerId(String deliveryPartnerId);

    List<DeliveryTask> findAllByStatus(String status);

    Page<DeliveryTask> findAll(Pageable pageable);

    Page<DeliveryTask> findByStatus(String status, Pageable pageable);

    /**
     * Find tasks still in-progress (ASSIGNED / PICKED_UP / OUT_FOR_DELIVERY)
     * whose estimated delivery time has passed — these need auto-completion.
     */
    @Query("{ 'status': { $in: ['ASSIGNED', 'PICKED_UP', 'OUT_FOR_DELIVERY'] }, 'estimatedDeliveryAt': { $lt: ?0 } }")
    List<DeliveryTask> findStaleInProgressTasks(Instant now);

    /** Tasks in a specific status whose updatedAt is before the given threshold — used by simulation scheduler. */
    List<DeliveryTask> findByStatusAndUpdatedAtBefore(String status, Instant before);
}
