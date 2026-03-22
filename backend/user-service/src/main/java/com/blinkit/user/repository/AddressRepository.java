package com.blinkit.user.repository;

import com.blinkit.user.entity.Address;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends MongoRepository<Address, String> {
    List<Address> findByUserId(String userId);
    Optional<Address> findByAddressId(String addressId);
    Optional<Address> findByAddressIdAndUserId(String addressId, String userId);
    void deleteByAddressIdAndUserId(String addressId, String userId);
    void deleteByUserId(String userId);
}
