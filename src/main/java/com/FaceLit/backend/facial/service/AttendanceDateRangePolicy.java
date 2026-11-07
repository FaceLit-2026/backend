package com.FaceLit.backend.facial.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.http.HttpStatus;

import com.FaceLit.backend.facial.exception.FacialEventException;

/** Enforces the rolling six-calendar-month window for attendance reports. */
public final class AttendanceDateRangePolicy {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Bogota");

    private AttendanceDateRangePolicy() {}

    public static void validate(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        LocalDate earliestDate = today.minusMonths(6);

        if (dateFrom == null || dateTo == null || dateFrom.isAfter(dateTo)
                || dateFrom.isBefore(earliestDate) || dateTo.isAfter(today)) {
            throw new FacialEventException(
                    "El rango debe estar entre los últimos seis meses y la fecha actual.",
                    HttpStatus.BAD_REQUEST);
        }
    }
}
