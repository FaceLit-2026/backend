package com.FaceLit.backend.shared.service;

import java.util.UUID;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserDisplayNameService {
    private final UserRepository users;
    public UserDisplayNameService(UserRepository users) { this.users = users; }

    public String fullName(UUID id) {
        if (id == null) return "";
        return users.findById(id).map(user -> ((user.getFirstName() == null ? "" : user.getFirstName())
                + " " + (user.getLastName() == null ? "" : user.getLastName())).trim()).orElse("");
    }
}
