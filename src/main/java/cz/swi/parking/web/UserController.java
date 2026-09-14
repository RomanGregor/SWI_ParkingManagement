package cz.swi.parking.web;

import cz.swi.parking.repo.AppUserRepository;
import cz.swi.parking.web.dto.UserResponse;
import java.util.Comparator;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the people who can hold reservations.
 *
 * <p>There is no authentication yet, so the web UI lets you pick who you are from this list.
 * When real sign-in arrives this endpoint becomes an admin-only listing.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AppUserRepository users;

    public UserController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping
    public List<UserResponse> all() {
        return users.findAll().stream()
                .sorted(Comparator.comparing(u -> u.getFullName()))
                .map(UserResponse::from)
                .toList();
    }
}
