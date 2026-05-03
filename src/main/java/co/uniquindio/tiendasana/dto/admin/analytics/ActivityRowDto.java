package co.uniquindio.tiendasana.dto.admin.analytics;

import java.time.LocalDateTime;

public record ActivityRowDto(
        String id,
        LocalDateTime fecha,
        double total,
        String tipo,
        String estado,
        String drillRoute
) {
}
