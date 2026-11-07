package com.FaceLit.backend.auth.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Language {
    ES,
    EN,
    DE,
    PR,
    FR;

    @JsonCreator
    public static Language fromValue(String value) {
        if (value == null) {
            return null;
        }

        return switch (value.trim().toUpperCase()) {
            case "FR", "FRA", "FRANCES", "FRANCAIS", "FRANÇAIS" -> FR;
            default -> valueOf(value.trim().toUpperCase());
        };
    }

}
