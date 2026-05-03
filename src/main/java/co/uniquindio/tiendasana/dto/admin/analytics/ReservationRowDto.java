package co.uniquindio.tiendasana.dto.admin.analytics;

import java.time.LocalDateTime;

public record ReservationRowDto(
        String id,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaReserva,
        double total,
        String usuarioId,
        String estadoReserva,
        boolean paid
) {
}
