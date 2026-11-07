package com.FaceLit.backend.shared.util;

import java.util.function.Function;

public final class DeletionGuard {

    private DeletionGuard() {
        // Evita instanciación — es solo un contenedor de lógica estática
    }

    // dependentCount -> cuántos registros dependientes existen
    // dependentLabel -> singular label for the actual dependent entity
    // actionHint -> qué debe hacer el usuario antes de poder eliminar
    // exceptionFactory -> el constructor de la excepción propia de cada módulo
    public static void assertNoDependents(
            long dependentCount,
            String dependentLabel,
            String actionHint,
            Function<String, RuntimeException> exceptionFactory) {

        if (dependentCount > 0) {
            String message = "No se puede eliminar porque tiene "
                    + dependentCount + " " + dependentLabel
                    + (dependentCount == 1 ? "" : "s")
                    + " asociado(s). " + actionHint;
            throw exceptionFactory.apply(message);
        }
    }

}
