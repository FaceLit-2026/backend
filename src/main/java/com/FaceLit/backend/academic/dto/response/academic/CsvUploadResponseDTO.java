package com.FaceLit.backend.academic.dto.response.academic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import lombok.Getter;

@Getter
public class CsvUploadResponseDTO {

    private final List<CsvRowResult> creados = new ArrayList<>();
    private final List<CsvRowResult> actualizados = new ArrayList<>();
    private final List<CsvRowError> inconsistenciasBloqueadas = new ArrayList<>();
    private final List<CsvRowError> erroresDeReferencia = new ArrayList<>();
    private final List<CsvPendingTransferResult> trasladosPendientes = new ArrayList<>();
    private final List<CsvGeneratedPasswordResult> contrasenasGeneradas = new ArrayList<>();

    public record CsvRowResult(int fila, String tipo, String detalle) {
    }

    public record CsvRowError(int fila, String tipo, String mensaje, String valorActual, String valorArchivo) {
    }

    public record CsvPendingTransferResult(UUID idPendingTransfer, int fila, String aprendiz,
            String fichaActual, String fichaPropuesta) {
    }

    public record CsvGeneratedPasswordResult(int fila, String tipo, String documento, String correo,
            String contrasenaTemporal) {
    }

}
