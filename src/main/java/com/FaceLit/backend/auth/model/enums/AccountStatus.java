package com.FaceLit.backend.auth.model.enums;

public enum AccountStatus { // EstadoCuenta

    ACTIVE, // Cuenta activa, puede iniciar sesión
    INACTIVE, // Cuenta inactiva
    BLOCKED // Cuenta bloqueada por seguridad o administración

}

// enum es un tipo de dato cerrado que solo permite ciertos valores.
// osea Este campo SOLO puede tener estos valores, nada más (ACTIVO, INACTIVO,
// PENDIENTE, BLOQUEADO)
// El dato se declara asi en la entidad (private EstadoCuenta estadoCuenta; ),1
// Solo acepta valores definidos
