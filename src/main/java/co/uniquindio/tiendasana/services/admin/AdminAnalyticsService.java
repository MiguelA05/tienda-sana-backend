package co.uniquindio.tiendasana.services.admin;

import co.uniquindio.tiendasana.dto.admin.analytics.*;

import java.time.LocalDate;
import java.util.List;

public interface AdminAnalyticsService {

    DashboardOverviewDto dashboard(LocalDate from, LocalDate to, boolean comparePrevious);

    SalesAnalyticsDto sales(LocalDate from, LocalDate to, int page, int size, String query, String paymentFilter);

    ReservationsAnalyticsDto reservations(LocalDate from, LocalDate to, int page, int size, String query, String estadoFilter);

    List<ProductPerformanceDto> productPerformance(LocalDate from, LocalDate to);

    List<TablePerformanceDto> tablePerformance(LocalDate from, LocalDate to);
}
