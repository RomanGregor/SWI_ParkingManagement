package cz.swi.parking.web.dto;

import cz.swi.parking.domain.AppUser;
import cz.swi.parking.domain.UserRole;
import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String fullName,
        UserRole role,
        LocalDate accessibilityPermitValidUntil) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(),
                user.getAccessibilityPermitValidUntil());
    }
}
