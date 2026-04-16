package co.uniquindio.tiendasana.services.implementations;

import co.uniquindio.tiendasana.dto.aidtos.AiComboRecommendationDTO;
import co.uniquindio.tiendasana.dto.aidtos.AiProductoRecomendadoDTO;
import co.uniquindio.tiendasana.dto.aidtos.AiRecommendationRequestDTO;
import co.uniquindio.tiendasana.dto.aidtos.AiRecommendationResponseDTO;
import co.uniquindio.tiendasana.model.mongo.ProductoDocument;
import co.uniquindio.tiendasana.repos.mongo.ProductoDocumentRepository;
import co.uniquindio.tiendasana.services.interfaces.AiRecommendationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AiRecommendationServiceImp implements AiRecommendationService {

    private final ProductoDocumentRepository productoDocumentRepository;
    private final ObjectMapper objectMapper;

    @Value("${deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${deepseek.timeout-ms:12000}")
    private int deepseekTimeoutMs;

    @Value("${deepseek.enabled:true}")
    private boolean deepseekEnabled;

    public AiRecommendationServiceImp(ProductoDocumentRepository productoDocumentRepository, ObjectMapper objectMapper) {
        this.productoDocumentRepository = productoDocumentRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public AiRecommendationResponseDTO generarRecomendaciones(AiRecommendationRequestDTO requestDTO) throws Exception {
        List<ProductoDocument> visibles = productoDocumentRepository
                .findAllByActiveTrueAndOutOfStockFalseAndStockQuantityGreaterThanOrderByNombreAsc(0)
                .stream()
                .limit(25)
                .collect(Collectors.toList());

        if (visibles.isEmpty()) {
            throw new IllegalStateException("No hay productos disponibles para recomendar.");
        }

        if (!deepseekEnabled || deepseekApiKey == null || deepseekApiKey.isBlank()) {
            return fallbackResponse(visibles, requestDTO, "fallback_sin_api_key");
        }

        try {
            return generarConDeepSeek(visibles, requestDTO);
        } catch (Exception e) {
            return fallbackResponse(visibles, requestDTO, "fallback_por_error");
        }
    }

    private AiRecommendationResponseDTO generarConDeepSeek(List<ProductoDocument> visibles,
                                                           AiRecommendationRequestDTO requestDTO) throws Exception {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(3000, deepseekTimeoutMs)))
                .build();

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", deepseekModel);
        body.put("temperature", 0.3);
        body.put("max_tokens", 650);

        ArrayNode messages = body.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", "Eres un asesor de compra para restaurante saludable. Responde solo JSON valido.");
        messages.addObject()
                .put("role", "user")
                .put("content", buildPrompt(visibles, requestDTO));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(deepseekBaseUrl) + "/chat/completions"))
                .timeout(Duration.ofMillis(Math.max(5000, deepseekTimeoutMs)))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + deepseekApiKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("DeepSeek retorno estado HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) {
            throw new IllegalStateException("DeepSeek no retorno contenido util.");
        }

        JsonNode parsedJson = parseJsonFromText(content);
        List<AiComboRecommendationDTO> combos = mapCombos(parsedJson, visibles);
        if (combos.isEmpty()) {
            throw new IllegalStateException("No se pudieron construir recomendaciones validas desde la respuesta de IA.");
        }

        String aviso = parsedJson.path("disclaimer").asText("Sugerencias generadas por IA; verifica alergias e ingredientes antes de comprar.");
        return new AiRecommendationResponseDTO(combos, aviso, "deepseek");
    }

    private String buildPrompt(List<ProductoDocument> visibles, AiRecommendationRequestDTO requestDTO) {
        String catalogo = visibles.stream()
                .map(p -> String.format(Locale.ROOT,
                        "- id=%s | nombre=%s | categoria=%s | precio=%.2f | descripcion=%s",
                        p.getId(),
                        p.getNombre(),
                        safeText(p.getCategoria()),
                        p.getPrecioUnitario(),
                        safeText(p.getDescripcion())))
                .collect(Collectors.joining("\n"));

        return "Devuelve exactamente un JSON con esta forma: " +
                "{\"combos\":[{\"titulo\":\"\",\"razon\":\"\",\"productIds\":[\"\"],\"estimatedPrice\":0}],\"disclaimer\":\"\"}. " +
                "Reglas: propone 2 combos, maximo 3 productos por combo, usa solo ids existentes del catalogo, " +
                "estimatedPrice debe ser suma real, evita texto adicional fuera del JSON.\n" +
                "Preferencias usuario: objetivo=" + safeText(requestDTO.objetivo()) +
                ", restriccion=" + safeText(requestDTO.restriccion()) +
                ", presupuestoMax=" + (requestDTO.presupuestoMax() == null ? "sin limite" : requestDTO.presupuestoMax()) +
                ", momentoDia=" + safeText(requestDTO.momentoDia()) + "\n" +
                "Catalogo:\n" + catalogo;
    }

    private List<AiComboRecommendationDTO> mapCombos(JsonNode parsedJson, List<ProductoDocument> visibles) {
        Map<String, ProductoDocument> byId = new LinkedHashMap<>();
        for (ProductoDocument producto : visibles) {
            byId.put(producto.getId(), producto);
        }

        List<AiComboRecommendationDTO> combos = new ArrayList<>();
        JsonNode combosNode = parsedJson.path("combos");
        if (!combosNode.isArray()) {
            return combos;
        }

        for (JsonNode comboNode : combosNode) {
            List<AiProductoRecomendadoDTO> productos = new ArrayList<>();
            JsonNode idsNode = comboNode.path("productIds");
            if (idsNode.isArray()) {
                for (JsonNode idNode : idsNode) {
                    ProductoDocument doc = byId.get(idNode.asText());
                    if (doc != null) {
                        productos.add(toProductoDTO(doc));
                    }
                }
            }

            if (productos.isEmpty()) {
                continue;
            }

            double precioReal = productos.stream().mapToDouble(AiProductoRecomendadoDTO::precioUnitario).sum();
            double precioEstimado = comboNode.path("estimatedPrice").asDouble(precioReal);
            if (precioEstimado <= 0) {
                precioEstimado = precioReal;
            }

            combos.add(new AiComboRecommendationDTO(
                    comboNode.path("titulo").asText("Combo recomendado"),
                    comboNode.path("razon").asText("Seleccionado segun tus preferencias."),
                    round2(precioEstimado),
                    productos
            ));

            if (combos.size() >= 2) {
                break;
            }
        }

        return combos;
    }

    private AiRecommendationResponseDTO fallbackResponse(List<ProductoDocument> visibles,
                                                         AiRecommendationRequestDTO requestDTO,
                                                         String origen) {
        List<ProductoDocument> candidatos = filtrarPorPresupuesto(visibles, requestDTO.presupuestoMax());
        List<ProductoDocument> ordenados = candidatos.stream()
                .sorted(Comparator.comparingDouble(ProductoDocument::getPrecioUnitario))
                .collect(Collectors.toList());

        List<AiComboRecommendationDTO> combos = new ArrayList<>();
        if (!ordenados.isEmpty()) {
            List<AiProductoRecomendadoDTO> comboLigero = ordenados.stream().limit(2).map(this::toProductoDTO).toList();
            combos.add(new AiComboRecommendationDTO(
                    "Combo ligero recomendado",
                    "Seleccion de productos de entrada para facilitar una compra rapida.",
                    round2(comboLigero.stream().mapToDouble(AiProductoRecomendadoDTO::precioUnitario).sum()),
                    comboLigero
            ));
        }

        if (ordenados.size() >= 3) {
            List<AiProductoRecomendadoDTO> comboCompleto = List.of(
                    toProductoDTO(ordenados.get(0)),
                    toProductoDTO(ordenados.get(1)),
                    toProductoDTO(ordenados.get(2))
            );
            combos.add(new AiComboRecommendationDTO(
                    "Combo completo sugerido",
                    "Alternativa balanceada para comparar opciones de compra.",
                    round2(comboCompleto.stream().mapToDouble(AiProductoRecomendadoDTO::precioUnitario).sum()),
                    comboCompleto
            ));
        }

        if (combos.isEmpty()) {
            List<AiProductoRecomendadoDTO> soloUno = List.of(toProductoDTO(visibles.get(0)));
            combos.add(new AiComboRecommendationDTO(
                    "Recomendacion inicial",
                    "No se encontraron suficientes productos para armar combos mas amplios.",
                    round2(soloUno.get(0).precioUnitario()),
                    soloUno
            ));
        }

        return new AiRecommendationResponseDTO(
                combos,
                "Sugerencias generadas por motor de respaldo. Puedes volver a intentar para obtener recomendacion IA.",
                origen
        );
    }

    private List<ProductoDocument> filtrarPorPresupuesto(List<ProductoDocument> visibles, Double presupuestoMax) {
        if (presupuestoMax == null || presupuestoMax <= 0) {
            return visibles;
        }
        List<ProductoDocument> filtrados = visibles.stream()
                .filter(p -> p.getPrecioUnitario() <= presupuestoMax)
                .collect(Collectors.toList());
        return filtrados.isEmpty() ? visibles : filtrados;
    }

    private JsonNode parseJsonFromText(String content) throws IOException {
        String trimmed = content.trim();
        if (trimmed.startsWith("```") && trimmed.endsWith("```")) {
            trimmed = trimmed.replaceAll("^```(?:json)?", "").replaceAll("```$", "").trim();
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start >= 0 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }

        return objectMapper.readTree(trimmed);
    }

    private AiProductoRecomendadoDTO toProductoDTO(ProductoDocument doc) {
        return new AiProductoRecomendadoDTO(
                doc.getId(),
                doc.getNombre(),
                doc.getCategoria(),
                doc.getImagen(),
                round2(doc.getPrecioUnitario())
        );
    }

    private static String trimTrailingSlash(String value) {
        return value != null && value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String safeText(String value) {
        return value == null || value.isBlank() ? "no especificado" : value.trim();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
