package co.uniquindio.tiendasana.dto.aidtos;

import java.util.List;

public record AiRecommendationResponseDTO(
        List<AiComboRecommendationDTO> recomendaciones,
        String aviso,
        String origen
) {
}
