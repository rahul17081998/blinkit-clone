package com.blinkit.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.blinkit.common.enums.Gender;

import java.time.LocalDate;

@Data
public class UpdateProfileRequest {

    @NotBlank @Size(max = 50)
    private String firstName;

    @NotBlank @Size(max = 50)
    private String lastName;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
    private String phone;

    private LocalDate dateOfBirth;

    private Gender gender;
}
