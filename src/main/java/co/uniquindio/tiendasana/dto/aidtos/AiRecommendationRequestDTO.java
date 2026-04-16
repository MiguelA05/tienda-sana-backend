package co.uniquindio.tiendasana.dto.aidtos;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record AiRecommendationRequestDTO(
        @Size(max = 120, message = "objetivo debe tener maximo 120 caracteres")
        String objetivo,
        @Size(max = 120, message = "restriccion debe tener maximo 120 caracteres")
        String restriccion,
        @DecimalMin(value = "0.0", inclusive = true, message = "presupuestoMax no puede ser negativo")
        Double presupuestoMax,
        @Size(max = 80, message = "momentoDia debe tener maximo 80 caracteres")
        String momentoDia
) {
}
