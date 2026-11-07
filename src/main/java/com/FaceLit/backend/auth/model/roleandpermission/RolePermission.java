package com.FaceLit.backend.auth.model.roleandpermission;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.shared.model.AuditBase;

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
    name = "role_permission",
    schema = "roleandpermission",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uq_role_permission",
            columnNames = { "id_role", "id_permission" }
        )
    }
)
public class RolePermission extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_role_permission", nullable = false)
    private UUID idRolePermission;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_role", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_permission", nullable = false)
    private Permission permission;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;
}