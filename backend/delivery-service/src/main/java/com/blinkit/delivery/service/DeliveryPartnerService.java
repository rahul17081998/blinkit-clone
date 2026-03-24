package com.blinkit.delivery.service;

import com.blinkit.delivery.dto.request.RegisterPartnerRequest;
import com.blinkit.delivery.dto.request.UpdateLocationRequest;
import com.blinkit.delivery.dto.response.DeliveryPartnerResponse;
import com.blinkit.delivery.entity.DeliveryPartner;
import com.blinkit.delivery.repository.DeliveryPartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryPartnerService {

    private final DeliveryPartnerRepository partnerRepository;
    private final MongoTemplate mongoTemplate;

    public DeliveryPartnerResponse register(String partnerId, String email, RegisterPartnerRequest req) {
        if (partnerRepository.existsByPartnerId(partnerId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Delivery partner profile already exists");
        }
        DeliveryPartner partner = DeliveryPartner.builder()
                .partnerId(partnerId)
                .email(email)
                .name(req.getName())
                .phone(req.getPhone())
                .vehicleType(req.getVehicleType())
                .vehicleNumber(req.getVehicleNumber())
                .isAvailable(true)
                .isActive(true)
                .avgRating(5.0)
                .totalDeliveries(0)
                .build();
        partnerRepository.save(partner);
        log.info("Delivery partner registered: partnerId={}", partnerId);
        return DeliveryPartnerResponse.from(partner);
    }

    public DeliveryPartnerResponse getMyProfile(String partnerId) {
        return DeliveryPartnerResponse.from(findByPartnerId(partnerId));
    }

    public DeliveryPartnerResponse updateProfile(String partnerId, RegisterPartnerRequest req) {
        DeliveryPartner partner = findByPartnerId(partnerId);
        partner.setName(req.getName());
        partner.setPhone(req.getPhone());
        partner.setVehicleType(req.getVehicleType());
        partner.setVehicleNumber(req.getVehicleNumber());
        partnerRepository.save(partner);
        return DeliveryPartnerResponse.from(partner);
    }

    public DeliveryPartnerResponse setAvailability(String partnerId, boolean available) {
        DeliveryPartner partner = findByPartnerId(partnerId);
        partner.setIsAvailable(available);
        partnerRepository.save(partner);
        log.info("Partner {} availability set to {}", partnerId, available);
        return DeliveryPartnerResponse.from(partner);
    }

    public DeliveryPartnerResponse toggleAvailability(String partnerId) {
        DeliveryPartner partner = findByPartnerId(partnerId);
        partner.setIsAvailable(!partner.getIsAvailable());
        partnerRepository.save(partner);
        log.info("Partner {} availability toggled to {}", partnerId, partner.getIsAvailable());
        return DeliveryPartnerResponse.from(partner);
    }

    public DeliveryPartnerResponse updateLocation(String partnerId, UpdateLocationRequest req) {
        DeliveryPartner partner = findByPartnerId(partnerId);
        partner.setCurrentLat(req.getLat());
        partner.setCurrentLng(req.getLng());
        partner.setLastLocationUpdatedAt(Instant.now());
        partnerRepository.save(partner);
        return DeliveryPartnerResponse.from(partner);
    }

    // ── Admin ────────────────────────────────────────────────────

    public Page<DeliveryPartnerResponse> getAllPartners(Pageable pageable) {
        return partnerRepository.findAll(pageable).map(DeliveryPartnerResponse::from);
    }

    public DeliveryPartnerResponse getPartnerById(String partnerId) {
        return DeliveryPartnerResponse.from(findByPartnerId(partnerId));
    }

    public DeliveryPartnerResponse togglePartnerActive(String partnerId) {
        DeliveryPartner partner = findByPartnerId(partnerId);
        partner.setIsActive(!partner.getIsActive());
        partnerRepository.save(partner);
        log.info("Admin toggled partner {} isActive to {}", partnerId, partner.getIsActive());
        return DeliveryPartnerResponse.from(partner);
    }

    // ── Internal helpers ──────────────────────────────────────────

    public DeliveryPartner findByPartnerId(String partnerId) {
        return partnerRepository.findByPartnerId(partnerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery partner not found"));
    }

    /**
     * Find first available, active partner with no active cooldown.
     * Immediately marks the partner as unavailable to prevent double-assignment.
     */
    public Optional<DeliveryPartner> findAvailablePartner() {
        Instant now = Instant.now();
        Criteria cooldownOk = new Criteria().orOperator(
                Criteria.where("cooldownUntil").is(null),
                Criteria.where("cooldownUntil").lt(now)
        );
        Query query = new Query(
                Criteria.where("isAvailable").is(true)
                        .and("isActive").is(true)
                        .andOperator(cooldownOk)
        ).limit(1);
        List<DeliveryPartner> results = mongoTemplate.find(query, DeliveryPartner.class);
        log.debug("findAvailablePartner: found {} candidate(s)", results.size());
        if (results.isEmpty()) return Optional.empty();
        DeliveryPartner partner = results.get(0);
        partner.setIsAvailable(false);
        partnerRepository.save(partner);
        return Optional.of(partner);
    }

    /** Find all partners whose cooldown period has expired. */
    public List<DeliveryPartner> findPartnersWithExpiredCooldown() {
        Instant now = Instant.now();
        Query query = new Query(
                Criteria.where("isAvailable").is(false)
                        .and("isActive").is(true)
                        .and("cooldownUntil").ne(null).lt(now)
        );
        return mongoTemplate.find(query, DeliveryPartner.class);
    }

    /** Persist partner changes (called by task service after delivery/cooldown updates). */
    public void savePartner(DeliveryPartner partner) {
        partnerRepository.save(partner);
    }

    /**
     * Find any active partner for simulation, or create a synthetic "Blinkit Rider"
     * if no partner exists. Used by the delivery simulation scheduler.
     */
    public DeliveryPartner findOrCreateSimulationPartner() {
        return partnerRepository.findAll().stream()
                .filter(p -> Boolean.TRUE.equals(p.getIsActive()))
                .findFirst()
                .orElseGet(() -> {
                    DeliveryPartner sim = DeliveryPartner.builder()
                            .partnerId(UUID.randomUUID().toString())
                            .name("Blinkit Rider")
                            .phone("+91-9999999999")
                            .vehicleType("MOTORCYCLE")
                            .vehicleNumber("DL01AB1234")
                            .isAvailable(false)
                            .isActive(true)
                            .avgRating(4.8)
                            .totalDeliveries(0)
                            .build();
                    DeliveryPartner saved = partnerRepository.save(sim);
                    log.info("Simulation: created synthetic delivery partner {}", saved.getPartnerId());
                    return saved;
                });
    }
}
