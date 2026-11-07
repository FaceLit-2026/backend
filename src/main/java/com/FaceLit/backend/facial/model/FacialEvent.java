package com.FaceLit.backend.facial.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.academic.model.academic.Apprentice;
import com.FaceLit.backend.environment.model.RecordEnvironment;
import com.FaceLit.backend.facial.model.enums.AttendanceStatus;
import com.FaceLit.backend.facial.model.enums.FacialEventOrigin;
import com.FaceLit.backend.facial.model.enums.FacialEventType;
import com.FaceLit.backend.shared.model.AuditBase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "facial_event", schema = "facialrecognition")
public class FacialEvent extends AuditBase {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id_facial_event", nullable = false)
    private UUID idFacialEvent;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_record_environment", nullable = false)
    private RecordEnvironment recordEnvironment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_device", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_apprentice", nullable = false)
    private Apprentice apprentice;

    @Column(name = "event_datetime", nullable = false)
    private OffsetDateTime eventDatetime;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private FacialEventType eventType;

    @Column(name = "recognition_result", nullable = false, length = 20)
    private String recognitionResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "attendance_status", nullable = false, length = 20)
    private AttendanceStatus attendanceStatus;

    @Column(name = "match_score", precision = 5, scale = 4)
    private BigDecimal matchScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 10)
    private FacialEventOrigin origin;

    @Column(name = "excuse")
    private Boolean excuse;
}
