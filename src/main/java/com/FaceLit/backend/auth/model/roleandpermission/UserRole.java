package com.FaceLit.backend.auth.model.roleandpermission;


import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.shared.model.AuditBase;
import com.FaceLit.backend.auth.model.security.User;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
    name = "user_role",
    schema = "roleandpermission",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_user_role",
            columnNames = { "id_user_app", "id_role" }
        )
    }
)
public class UserRole extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_user_role", nullable = false)
    private UUID idUserRole;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_user_app", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_role", nullable = false)
    private Role role;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;
}
