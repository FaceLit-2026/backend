package com.FaceLit.backend.shared.util;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;
import com.FaceLit.backend.shared.constants.AppConstants;

// Singleton gestionado por Spring y servicio compartido que elimina duplicación de generación de códigos.
@Component
public class VerificationCodeGenerator {

    private final SecureRandom random = new SecureRandom();

    // Genera un código de 6 dígitos con ceros a la izquierda si aplica
    // Ejemplo: 483 -> "000483"

    public String generate() {
        return String.format(
                "%0" + AppConstants.VERIFICATION_CODE_LENGTH + "d",
                random.nextInt(AppConstants.VERIFICATION_CODE_MAX));
    }

}
