package com.FaceLit.backend.facial.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.FaceLit.backend.facial.model.FacialEvent;
import com.FaceLit.backend.facial.model.enums.AttendanceStatus;
import com.FaceLit.backend.facial.model.enums.FacialEventOrigin;
import com.FaceLit.backend.facial.model.enums.FacialEventType;

import lombok.Getter;

@Getter
public class FacialEventResponseDTO {

    private final UUID idFacialEvent;
    private final UUID idRecordEnvironment;
    private final UUID idDevice;
    private final UUID idApprentice;
    private final OffsetDateTime eventDatetime;
    private final FacialEventType eventType;
    private final String recognitionResult;
    private final AttendanceStatus attendanceStatus;
    private final BigDecimal matchScore;
    private final FacialEventOrigin origin;
    private final Boolean excuse;

    public FacialEventResponseDTO(FacialEvent event) {
        this.idFacialEvent = event.getIdFacialEvent();
        this.idRecordEnvironment = event.getRecordEnvironment().getIdRecordEnvironment();
        this.idDevice = event.getDevice().getIdDevice();
        this.idApprentice = event.getApprentice().getIdApprentice();
        this.eventDatetime = event.getEventDatetime();
        this.eventType = event.getEventType();
        this.recognitionResult = event.getRecognitionResult();
        this.attendanceStatus = event.getAttendanceStatus();
        this.matchScore = event.getMatchScore();
        this.origin = event.getOrigin();
        this.excuse = event.getExcuse();
    }
}
