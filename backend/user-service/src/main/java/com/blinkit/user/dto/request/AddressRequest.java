package com.blinkit.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import com.blinkit.common.enums.AddressLabel;
import lombok.Data;

@Data
public class AddressRequest {

    @NotNull
    private AddressLabel label;

    @NotBlank @Size(max = 100)
    private String recipientName;

    @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$")
    private String recipientPhone;

    @NotBlank
    private String flatNo;

    @NotBlank
    private String building;

    private String street;

    @NotBlank
    private String area;

    @NotBlank
    private String city;

    @NotBlank
    private String state;

    @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Pincode must be 6 digits")
    private String pincode;

    private String landmark;

    @NotNull
    private Double lat;

    @NotNull
    private Double lng;
}
