package com.FaceLit.backend.auth.model.roleandpermission;

import java.util.UUID;

import com.FaceLit.backend.shared.model.AuditBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "permission", schema = "roleandpermission")
public class Permission extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_permission", nullable = false)
    private UUID idPermission;

    @Column(name = "name_permission", nullable = false, length = 100)
    private String namePermission;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}