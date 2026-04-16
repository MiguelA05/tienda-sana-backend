package co.uniquindio.tiendasana.dto.aidtos;

import java.util.List;

public record AiComboRecommendationDTO(
        String titulo,
        String razon,
        double precioEstimado,
        List<AiProductoRecomendadoDTO> productos
) {
}
