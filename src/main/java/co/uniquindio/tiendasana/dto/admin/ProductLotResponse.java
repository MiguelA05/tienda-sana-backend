package co.uniquindio.tiendasana.dto.admin;

import java.time.LocalDateTime;

public record ProductLotResponse(
        String id,
        String productId,
        String supplierId,
        LocalDateTime entryDate,
        int quantity,
        double unitValue
) {
}
