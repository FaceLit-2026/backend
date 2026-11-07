package com.FaceLit.backend.facial.service;

import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.facial.exception.FacialEventException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;

/** Shared authorization rules for facial use cases. */
@org.springframework.stereotype.Component
public class FacialAccessPolicy {
    public boolean hasRole(UUID actor, String role) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return actor != null && authentication != null && authentication.isAuthenticated()
                && actor.toString().equals(authentication.getName())
                && authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public void requireSessionOperator(RecordEnvironment record, UUID actor) {
        boolean belongs = record.getInstructorScheduled() != null
                && record.getInstructorScheduled().getUser().getIdUser().equals(actor)
                || record.getInstructorInCharge() != null
                && record.getInstructorInCharge().getUser().getIdUser().equals(actor);
        if (record.getDeletedAt() != null || !(hasRole(actor, "COORDINATOR") || hasRole(actor, "INSTRUCTOR") && belongs)) {
            throw new FacialEventException("No puedes operar esta sesión.", HttpStatus.FORBIDDEN);
        }
    }

}
