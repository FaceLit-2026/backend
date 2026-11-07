package com.FaceLit.backend.environment.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.academic.model.academic.Chip;
import com.FaceLit.backend.academic.model.academic.Instructor;
import com.FaceLit.backend.facial.model.Device;
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
@Table(name = "record_environment", schema = "environment")
public class RecordEnvironment extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_record_environment", nullable = false)
    private UUID idRecordEnvironment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_environment", nullable = false)
    private Environment environment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_device", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_chip", nullable = false)
    private Chip chip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_instructor_scheduled", nullable = false)
    private Instructor instructorScheduled;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_instructor_in_charge")
    private Instructor instructorInCharge;

    @Column(name = "entry_time", nullable = false)
    private OffsetDateTime entryTime;

    @Column(name = "registration_minutes", nullable = false)
    private Integer registrationMinutes;

    @Column(name = "exit_time")
    private OffsetDateTime exitTime;

    @Column(name = "shutdown_time")
    private OffsetDateTime shutdownTime;

    @Column(name = "exit_reminder_sent", nullable = false)
    private Boolean exitReminderSent;

    @Column(name = "active", nullable = false)
    private Boolean active;
}
