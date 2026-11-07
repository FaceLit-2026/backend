package com.FaceLit.backend.academic.service.csv;

import java.util.Map;
import com.FaceLit.backend.academic.dto.response.academic.CsvUploadResponseDTO;

/** Parsed CSV row with its source line number and uniform error context. */
public record CsvRow(int number, Map<String, String> cells) {
    public String value(String column) {
        return cells.getOrDefault(column, "");
    }

    public CsvUploadResponseDTO.CsvRowError error(String message) {
        return new CsvUploadResponseDTO.CsvRowError(number, value("tipo"), message, null, null);
    }
}
