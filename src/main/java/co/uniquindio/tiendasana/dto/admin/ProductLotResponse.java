package co.uniquindio.tiendasana.dto.admin;

import java.time.LocalDateTime;

public record ProductLotResponse(
        String id,
        String productId,
        String supplierId,
        LocalDateTime entryDate,
        /** Cantidad inicial registrada del movimiento de entrada (lote). */
        int initialQuantity,
        double unitValue,
        int quantityRemaining,
        int quantityConsumed,
        /** ACTIVO | CONSUMIDO | ANULADO */
        String status,
        boolean voided) {
}
