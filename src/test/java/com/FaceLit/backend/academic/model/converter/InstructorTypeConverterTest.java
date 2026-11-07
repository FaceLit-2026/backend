package com.FaceLit.backend.academic.model.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.FaceLit.backend.academic.model.enums.InstructorType;

class InstructorTypeConverterTest {

    @Test
    void shouldConvertSpanishInstructorTypes() {
        InstructorTypeConverter converter = new InstructorTypeConverter();

        assertEquals("SPECIFIC", converter.convertToDatabaseColumn(InstructorType.ESPECIFICO));
        assertEquals("CROSS-CUTTING", converter.convertToDatabaseColumn(InstructorType.TRANSVERSAL));
        assertEquals(InstructorType.ESPECIFICO, converter.convertToEntityAttribute("SPECIFIC"));
        assertEquals(InstructorType.TRANSVERSAL, converter.convertToEntityAttribute("CROSS-CUTTING"));
    }
}
