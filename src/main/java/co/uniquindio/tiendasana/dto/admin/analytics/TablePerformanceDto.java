package co.uniquindio.tiendasana.dto.admin.analytics;

public record TablePerformanceDto(
        String tableId,
        String tableName,
        long reservationsInPeriod,
        double occupancyRatePercent
) {
}
