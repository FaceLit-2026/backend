package com.FaceLit.backend.auth.service.serviceImpl.security;

import java.util.UUID;
import org.springframework.stereotype.Service;
import com.FaceLit.backend.auth.dto.response.security.ProfileResponseDTO;
import com.FaceLit.backend.auth.exception.UserManagementException;
import com.FaceLit.backend.auth.repository.security.UserRepository;
import com.FaceLit.backend.auth.model.security.User;
import com.FaceLit.backend.auth.service.security.ProfileService;

@Service
public class ProfileServiceImpl implements  ProfileService {

   
    private final UserRepository userRepository;

    public ProfileServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public ProfileResponseDTO getMyProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserManagementException("Usuario no encontrado"));

        return new ProfileResponseDTO(
                user.getIdUser(),
                user.getFirstName(),
                user.getLastName(),
                user.getDocumentNumber(),
                user.getCredential().getEmail()
        );
    }

}
