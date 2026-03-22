package com.blinkit.user.service;

import com.blinkit.user.dto.request.AddressRequest;
import com.blinkit.user.dto.request.UpdateProfileRequest;
import com.blinkit.user.entity.Address;
import com.blinkit.user.entity.UserProfile;
import com.blinkit.user.repository.AddressRepository;
import com.blinkit.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository profileRepo;
    private final AddressRepository addressRepo;

    // ── Profile ───────────────────────────────────────────────────

    public UserProfile getProfile(String userId) {
        return profileRepo.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
    }

    public UserProfile updateProfile(String userId, String email, UpdateProfileRequest req) {
        UserProfile profile = profileRepo.findByUserId(userId)
                .orElseGet(() -> UserProfile.builder().userId(userId).email(email).build());

        if (profile.getEmail() == null && email != null && !email.isBlank()) {
            profile.setEmail(email);
        }
        profile.setFirstName(req.getFirstName());
        profile.setLastName(req.getLastName());
        profile.setPhone(req.getPhone());
        profile.setDateOfBirth(req.getDateOfBirth());
        profile.setGender(req.getGender());
        return profileRepo.save(profile);
    }

    // ── Addresses ─────────────────────────────────────────────────

    public List<Address> getAddresses(String userId) {
        return addressRepo.findByUserId(userId);
    }

    public Address getAddressById(String addressId) {
        return addressRepo.findByAddressId(addressId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    public Address addAddress(String userId, AddressRequest req) {
        Address address = Address.builder()
                .addressId(UUID.randomUUID().toString())
                .userId(userId)
                .label(req.getLabel())
                .recipientName(req.getRecipientName())
                .recipientPhone(req.getRecipientPhone())
                .flatNo(req.getFlatNo())
                .building(req.getBuilding())
                .street(req.getStreet())
                .area(req.getArea())
                .city(req.getCity())
                .state(req.getState())
                .pincode(req.getPincode())
                .landmark(req.getLandmark())
                .lat(req.getLat())
                .lng(req.getLng())
                .isDefault(false)
                .build();
        return addressRepo.save(address);
    }

    public Address updateAddress(String userId, String addressId, AddressRequest req) {
        Address address = addressRepo.findByAddressIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));

        address.setLabel(req.getLabel());
        address.setRecipientName(req.getRecipientName());
        address.setRecipientPhone(req.getRecipientPhone());
        address.setFlatNo(req.getFlatNo());
        address.setBuilding(req.getBuilding());
        address.setStreet(req.getStreet());
        address.setArea(req.getArea());
        address.setCity(req.getCity());
        address.setState(req.getState());
        address.setPincode(req.getPincode());
        address.setLandmark(req.getLandmark());
        address.setLat(req.getLat());
        address.setLng(req.getLng());
        return addressRepo.save(address);
    }

    public void deleteAddress(String userId, String addressId) {
        addressRepo.findByAddressIdAndUserId(addressId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        addressRepo.deleteByAddressIdAndUserId(addressId, userId);
    }

    public void deleteUserData(String userId) {
        profileRepo.deleteByUserId(userId);
        addressRepo.deleteByUserId(userId);
        log.info("Hard deleted profile and addresses for userId={}", userId);
    }

    public Address setDefaultAddress(String userId, String addressId) {
        List<Address> all = addressRepo.findByUserId(userId);
        // Clear current default
        all.forEach(a -> a.setIsDefault(false));
        addressRepo.saveAll(all);
        // Set new default
        Address target = all.stream()
                .filter(a -> a.getAddressId().equals(addressId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
        target.setIsDefault(true);
        return addressRepo.save(target);
    }
}
