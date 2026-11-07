package com.FaceLit.backend.shared.service;

//Interfaz para el service de envio de corre electronico

// Define el método que enviará el código de verificación
// al correo del usuario.
public interface EmailService {

    void sendRecoveryCode(String toEmail, String code);

    void sendNotificationEmail(String toEmail, String subject, String body);

    void sendInitialCredentials(String toEmail, String subject, String body);


}
