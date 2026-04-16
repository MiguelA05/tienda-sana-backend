package co.uniquindio.tiendasana.services.interfaces;

import co.uniquindio.tiendasana.dto.aidtos.AiRecommendationRequestDTO;
import co.uniquindio.tiendasana.dto.aidtos.AiRecommendationResponseDTO;

public interface AiRecommendationService {
    AiRecommendationResponseDTO generarRecomendaciones(AiRecommendationRequestDTO requestDTO) throws Exception;
}
