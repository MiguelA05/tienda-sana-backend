package co.uniquindio.tiendasana.dto.aidtos;

public record AiProductoRecomendadoDTO(
        String id,
        String nombre,
        String categoria,
        String imagen,
        double precioUnitario
) {
}
