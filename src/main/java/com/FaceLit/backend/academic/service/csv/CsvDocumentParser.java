package com.FaceLit.backend.academic.service.csv;

import com.FaceLit.backend.academic.exception.AcademicException;
import com.FaceLit.backend.shared.constants.AppConstants;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/** CSV syntax, encoding and structural validation; no database or notification dependencies. */
@org.springframework.stereotype.Component
public class CsvDocumentParser {
    public List<CsvRow> readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AcademicException("El archivo no contiene ninguna fila de datos para procesar.");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".csv")) {
            throw new AcademicException("El archivo debe tener extensión .csv.");
        }
        if (file.getSize() > AppConstants.CSV_MAX_FILE_BYTES) {
            throw new AcademicException("El archivo es demasiado grande. El tamaño máximo permitido es 5 MB.");
        }
        String content;
        try {
            content = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(file.getBytes()))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new AcademicException(
                    "El archivo no está en formato UTF-8. Vuelve a guardarlo con esa codificación.");
        } catch (IOException exception) {
            throw new AcademicException("No fue posible leer el archivo CSV.");
        }
        List<String> lines = content.lines().toList();
        if (lines.isEmpty()) {
            throw new AcademicException("El archivo no contiene ninguna fila de datos para procesar.");
        }
        List<String> headers = parseLine(lines.get(0).replaceFirst("^\\uFEFF", "")).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
        if (!headers.contains(AppConstants.CSV_TYPE_COLUMN)) {
            throw new AcademicException("El archivo debe incluir la columna 'tipo'.");
        }
        List<CsvRow> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            if (lines.get(index).isBlank()) {
                continue;
            }
            if (rows.size() >= AppConstants.CSV_MAX_DATA_ROWS) {
                throw new AcademicException("El archivo supera el máximo de " + AppConstants.CSV_MAX_DATA_ROWS
                        + " filas permitidas por carga. Divide la información en varios archivos.");
            }
            List<String> values = parseLine(lines.get(index));
            Map<String, String> cells = new HashMap<>();
            for (int cell = 0; cell < headers.size(); cell++) {
                cells.put(headers.get(cell), cell < values.size() ? values.get(cell).trim() : "");
            }
            String type = cells.getOrDefault("tipo", "").toLowerCase(Locale.ROOT);
            if (!Set.of("programa", "ficha", "aprendiz", "instructor").contains(type)) {
                throw new AcademicException(
                        "Tipo de fila no reconocido en la fila " + (index + 1) + ": '" + type + "'.");
            }
            cells.put("tipo", type);
            rows.add(new CsvRow(index + 1, cells));
        }
        if (rows.isEmpty()) {
            throw new AcademicException("El archivo no contiene ninguna fila de datos para procesar.");
        }
        return rows;
    }

    private List<String> parseLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < line.length(); index++) {
            char current = line.charAt(index);
            if (current == '"' && quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                value.append('"');
                index++;
            } else if (current == '"' && (quoted || value.length() == 0)) {
                quoted = !quoted;
            } else if (current == ',' && !quoted) {
                result.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) {
            throw new AcademicException("Hay comillas sin cerrar en una fila del archivo CSV.");
        }
        result.add(value.toString());
        return result;
    }

}
