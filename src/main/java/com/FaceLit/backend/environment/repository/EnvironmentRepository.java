package com.FaceLit.backend.environment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.FaceLit.backend.environment.model.Environment;
import com.FaceLit.backend.environment.model.enums.EnvironmentState;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    @Query("select e from Environment e where e.state = :state and e.deletedAt is null order by e.environmentName asc")
    List<Environment> findByStateOrderByEnvironmentNameAsc(@Param("state") EnvironmentState state);

    List<Environment> findByDeletedAtIsNullOrderByEnvironmentNameAsc();

    List<Environment> findAllByOrderByEnvironmentNameAsc();

    @Query("""
            select e from Environment e
            where e.state = :state
              and e.deletedAt is null
              and lower(e.environmentName) like lower(concat('%', :search, '%'))
            order by e.environmentName asc
            """)
    List<Environment> searchActive(@Param("search") String search, @Param("state") EnvironmentState state);

    @Query("""
            select e from Environment e
            where lower(trim(e.environmentName)) = lower(trim(:environmentName))
            """)
    Optional<Environment> findByNormalizedName(@Param("environmentName") String environmentName);
}
