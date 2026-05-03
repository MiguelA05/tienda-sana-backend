package co.uniquindio.tiendasana.dto.admin.analytics;

import java.time.LocalDate;
import java.util.List;

public record DashboardOverviewDto(
        LocalDate from,
        LocalDate to,
        List<KpiDto> kpis,
        List<SeriesPointDto> salesByDayCurrent,
        List<SeriesPointDto> salesByDayPrevious,
        List<LabelValueDto> salesByWeekday,
        List<LabelValueDto> salesByHourSlot,
        List<ProductRankDto> topProducts,
        List<ProductRankDto> bottomProducts,
        List<LabelValueDto> reservationsByDay,
        List<LabelValueDto> reservationsByHourSlot,
        List<ActivityRowDto> recentActivity
) {
}
