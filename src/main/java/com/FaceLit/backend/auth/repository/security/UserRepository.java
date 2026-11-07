package com.FaceLit.backend.auth.repository.security;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import com.FaceLit.backend.auth.model.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.FaceLit.backend.auth.dto.response.security.UserListProjection;
import com.FaceLit.backend.auth.model.security.User;

// EL JpaRespository es  una interfas que proporciona metodos para relacionar con la base de datos. 
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    // User es la entidad Y login es el tipo de dato del ID (Long)

    // aqui lo que hace es verificar si existe un usuario con ese número de
    // documento
    boolean existsByDocumentNumber(String documentNumber);

    // -- PARA RECOERDAR ---- //

    // solo se necesita definir el metodo, existsByDocumentNumber (verificar si
    // existe numero de documento)
    // no pone mas metodos porque el JpaRepository ya tiene metodos predefinidos
    // como save, findById, deleteById

    Optional<User> findByDocumentNumber(String documentNumber);

    // Búsqueda case-insensitive por nombre completo (nombre + apellido) o correo.
    // Se hace JOIN con Credential porque el email vive ahí, no en User.
    @Query("""
                SELECT u FROM User u
                JOIN Credential c ON c.user = u
                WHERE LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))
                OR u.documentNumber LIKE CONCAT('%', :query, '%')
            """)
    List<User> searchByFullNameOrEmail(@Param("query") String query);

    @Query("""
                SELECT
                    u.idUser AS userId,
                    u.firstName AS firstName,
                    u.lastName AS lastName,
                    u.documentNumber AS documentNumber,
                    c.email AS email,
                    r.nameRole AS role,
                    u.accountStatus AS accountStatus,
                    u.createdAt AS registrationDate
                FROM User u
                LEFT JOIN Credential c ON c.user = u
                LEFT JOIN UserRole ur ON ur.user = u
                LEFT JOIN Role r ON r = ur.role
                ORDER BY u.createdAt DESC
            """)
    List<UserListProjection> findAllUserSummaries();

    @Query("""
                SELECT
                    u.idUser AS userId,
                    u.firstName AS firstName,
                    u.lastName AS lastName,
                    u.documentNumber AS documentNumber,
                    c.email AS email,
                    r.nameRole AS role,
                    u.accountStatus AS accountStatus,
                    u.createdAt AS registrationDate
                FROM User u
                LEFT JOIN Credential c ON c.user = u
                LEFT JOIN UserRole ur ON ur.user = u
                LEFT JOIN Role r ON r = ur.role
                WHERE LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :query, '%'))
                OR u.documentNumber LIKE CONCAT('%', :query, '%')
                ORDER BY u.createdAt DESC
            """)
    List<UserListProjection> searchUserSummaries(@Param("query") String query);

    long countByAccountStatus(AccountStatus accountStatus);

}
