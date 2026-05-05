package co.uniquindio.tiendasana.controllers;

import co.uniquindio.tiendasana.dto.EmailDTO;
import co.uniquindio.tiendasana.services.interfaces.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/internal")
public class InternalEmailController {

    private final EmailService emailService;

    @GetMapping("/test-email")
    public ResponseEntity<Map<String, Object>> testEmail(
            @RequestParam String to,
            @RequestParam(defaultValue = "Prueba local Resend") String subject,
            @RequestParam(defaultValue = "Hola, esta es una prueba local de Resend desde Tienda Sana.") String body) {
        try {
            emailService.sendEmailNow(new EmailDTO(subject, body, to));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "to", to,
                    "subject", subject));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "to", to,
                    "subject", subject,
                    "error", e.getMessage()));
        }
    }
}