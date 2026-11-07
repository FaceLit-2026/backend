package com.FaceLit.backend.academic.service.csv;

import java.util.regex.Pattern;
import com.FaceLit.backend.shared.constants.AppConstants;

/** Shared lexical rules used by CSV importers; one source for accepted formats. */
final class CsvValidationRules {
    private CsvValidationRules() {}

    static final Pattern DOCUMENT = Pattern.compile(
            "\\d{" + AppConstants.DOCUMENT_NUMBER_MIN_LENGTH + "," + AppConstants.DOCUMENT_NUMBER_MAX_LENGTH
                    + "}");
    static final Pattern PROGRAM_CODE = Pattern.compile("[A-Za-z0-9]{"
            + AppConstants.PROGRAM_CODE_MIN_LENGTH + "," + AppConstants.PROGRAM_CODE_MAX_LENGTH + "}");
    static final Pattern CHIP_CODE = Pattern.compile("\\d{" + AppConstants.CHIP_CODE_LENGTH + "}");
    static final Pattern NAME = Pattern.compile("[\\p{L}]+(?: [\\p{L}]+)*");
}
