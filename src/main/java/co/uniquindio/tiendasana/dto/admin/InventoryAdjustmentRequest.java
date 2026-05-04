package co.uniquindio.tiendasana.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * @param direction IN entrada de stock, OUT salida
 * @param targetLotId opcional: IN suma disponible en ese lote; OUT descuenta solo de ese lote. Si es null, OUT usa FIFO
 *                     entre lotes; IN crea un movimiento de entrada tipo ajuste (nueva fila de lote).
 */
public record InventoryAdjustmentRequest(
        @NotBlank String productId,
        @NotNull InventoryAdjustmentRequest.Direction direction,
        @NotNull @Min(1) Integer quantity,
        @NotBlank String reason,
        String targetLotId) {

    public enum Direction {
        IN,
        OUT
    }
}
