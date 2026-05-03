package co.uniquindio.tiendasana.dto.admin.analytics;

import java.time.LocalDate;
import java.util.List;

public record SalesAnalyticsDto(
        LocalDate from,
        LocalDate to,
        List<KpiDto> kpis,
        List<SeriesPointDto> salesByDayCurrent,
        List<SeriesPointDto> salesByDayPrevious,
        List<LabelValueDto> salesByCategory,
        List<ProductRankDto> salesByProduct,
        List<LabelValueDto> salesByPaymentMethod,
        PagedTableDto<SalesOrderRowDto> orders
) {
}
