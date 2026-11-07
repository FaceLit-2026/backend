package com.FaceLit.backend.auth.repository.roleandpermission;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
import com.FaceLit.backend.auth.model.roleandpermission.Permission;

public interface PermissionRepository extends JpaRepository<Permission, UUID> { 

}
