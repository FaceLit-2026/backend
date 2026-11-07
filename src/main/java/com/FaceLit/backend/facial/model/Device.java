package com.FaceLit.backend.facial.model;

import java.util.UUID;

import com.FaceLit.backend.environment.model.Environment;
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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "device", schema = "facialrecognition")
public class Device extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_device", nullable = false)
    private UUID idDevice;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_environment", nullable = false)
    private Environment environment;

    @Column(name = "device_code", nullable = false, unique = true, length = 50)
    private String deviceCode;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "origin_ip", length = 50)
    private String originIp;
}
