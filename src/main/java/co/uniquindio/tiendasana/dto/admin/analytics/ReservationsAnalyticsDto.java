package co.uniquindio.tiendasana.dto.admin.analytics;

import java.time.LocalDate;
import java.util.List;

public record ReservationsAnalyticsDto(
        LocalDate from,
        LocalDate to,
        List<KpiDto> kpis,
        List<LabelValueDto> reservationsByDay,
        List<LabelValueDto> reservationsByHourSlot,
        Double avgOccupationHours,
        Double tableRotation,
        PagedTableDto<ReservationRowDto> reservations
) {
}
