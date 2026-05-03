package co.uniquindio.tiendasana.controllers.admin;

import co.uniquindio.tiendasana.dto.admin.analytics.*;
import co.uniquindio.tiendasana.dto.jwtdtos.MessageDTO;
import co.uniquindio.tiendasana.services.admin.AdminAnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;

    private static LocalDate defaultTo(LocalDate to) {
        return to != null ? to : LocalDate.now();
    }

    private static LocalDate defaultFrom(LocalDate from, LocalDate to) {
        LocalDate t = defaultTo(to);
        return from != null ? from : t.minusDays(29);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<MessageDTO<DashboardOverviewDto>> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Boolean comparePrevious) {
        LocalDate t = defaultTo(to);
        LocalDate f = defaultFrom(from, t);
        // Sin parámetro → comparar (comportamiento por defecto). false explícito → no comparar.
        boolean compare = comparePrevious == null || comparePrevious;
        return ResponseEntity.ok(new MessageDTO<>(false, analyticsService.dashboard(f, t, compare)));
    }

    @GetMapping("/sales")
    public ResponseEntity<MessageDTO<SalesAnalyticsDto>> sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "ALL") String paymentStatus) {
        LocalDate t = defaultTo(to);
        LocalDate f = defaultFrom(from, t);
        return ResponseEntity.ok(new MessageDTO<>(false, analyticsService.sales(f, t, page, size, q, paymentStatus)));
    }

    @GetMapping("/reservations")
    public ResponseEntity<MessageDTO<ReservationsAnalyticsDto>> reservations(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String estado) {
        LocalDate t = defaultTo(to);
        LocalDate f = defaultFrom(from, t);
        return ResponseEntity.ok(new MessageDTO<>(false, analyticsService.reservations(f, t, page, size, q, estado)));
    }

    @GetMapping("/product-performance")
    public ResponseEntity<MessageDTO<List<ProductPerformanceDto>>> productPerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = defaultTo(to);
        LocalDate f = defaultFrom(from, t);
        return ResponseEntity.ok(new MessageDTO<>(false, analyticsService.productPerformance(f, t)));
    }

    @GetMapping("/table-performance")
    public ResponseEntity<MessageDTO<List<TablePerformanceDto>>> tablePerformance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate t = defaultTo(to);
        LocalDate f = defaultFrom(from, t);
        return ResponseEntity.ok(new MessageDTO<>(false, analyticsService.tablePerformance(f, t)));
    }
}
