package com.FaceLit.backend.auth.repository.roleandpermission;

import org.springframework.data.jpa.repository.JpaRepository;
import com.FaceLit.backend.auth.model.roleandpermission.RolePermission;
import java.util.List;
import java.util.UUID;

public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    // Buscar una lista de los permisos que tiene cada rol
    List<RolePermission> findByRole_IdRole(UUID idRole);

}
