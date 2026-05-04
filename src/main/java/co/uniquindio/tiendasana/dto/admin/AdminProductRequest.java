package co.uniquindio.tiendasana.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record AdminProductRequest(
        @NotBlank String name,
        @NotBlank String description,
        @NotBlank String category,
        @NotNull @PositiveOrZero Double price,
        @NotBlank @Pattern(regexp = "^https?://.+", message = "imageUrl debe ser una URL valida") String imageUrl,
        /** Opcional en creación; el stock lo gobiernan los lotes. */
        Boolean outOfStock,
        /** Solo se usa al crear: lote de apertura para que el producto quede visible desde el inicio. */
        @PositiveOrZero Integer initialStock
) {
}
