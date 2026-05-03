package co.uniquindio.tiendasana.dto.admin.analytics;

/**
 * KPI con valor mostrable y tendencia vs periodo anterior (porcentaje; null si no aplica).
 */
public record KpiDto(String id, String label, String value, Double trendPercent, String hint) {
}
