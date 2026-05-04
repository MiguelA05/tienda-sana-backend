package co.uniquindio.tiendasana.dto.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ProductLotRequest(
        @NotBlank String productId,
        @NotBlank String supplierId,
        @NotNull LocalDateTime entryDate,
        @Min(1) int quantity,
        @Min(0) double unitValue
) {
}
