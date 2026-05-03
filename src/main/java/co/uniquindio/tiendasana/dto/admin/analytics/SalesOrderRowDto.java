package co.uniquindio.tiendasana.dto.admin.analytics;

import java.time.LocalDateTime;

public record SalesOrderRowDto(
        String id,
        LocalDateTime fecha,
        double total,
        String emailCliente,
        String paymentType,
        String estadoPago,
        boolean paid
) {
}
