package com.FaceLit.backend.facial.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.FaceLit.backend.facial.model.Device;

public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByDeviceCode(String deviceCode);
}
