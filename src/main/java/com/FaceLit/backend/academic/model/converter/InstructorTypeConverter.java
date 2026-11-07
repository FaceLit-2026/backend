package com.FaceLit.backend.academic.model.converter;

import com.FaceLit.backend.academic.model.enums.InstructorType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class InstructorTypeConverter implements AttributeConverter<InstructorType, String> {

    @Override
    public String convertToDatabaseColumn(InstructorType attribute) {
        if (attribute == null) {
            return null;
        }
        return switch (attribute) {
            case ESPECIFICO -> "SPECIFIC";
            case TRANSVERSAL -> "CROSS-CUTTING";
        };
    }

    @Override
    public InstructorType convertToEntityAttribute(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "ESPECIFICO", "SPECIFIC" -> InstructorType.ESPECIFICO;
            case "TRANSVERSAL", "CROSS-CUTTING", "CROSS_CUTTING" -> InstructorType.TRANSVERSAL;
            default -> throw new IllegalArgumentException("Tipo de instructor no soportado: " + value);
        };
    }
}