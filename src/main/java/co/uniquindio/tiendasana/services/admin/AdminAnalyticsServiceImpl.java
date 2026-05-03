package co.uniquindio.tiendasana.services.admin;

import co.uniquindio.tiendasana.dto.admin.analytics.*;
import co.uniquindio.tiendasana.model.mongo.ProductoDocument;
import co.uniquindio.tiendasana.model.mongo.ReservaDocument;
import co.uniquindio.tiendasana.model.mongo.VentaProductoDocument;
import co.uniquindio.tiendasana.model.vo.DetalleVentaProducto;
import co.uniquindio.tiendasana.model.vo.Pago;
import co.uniquindio.tiendasana.repos.mongo.ProductoDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.ReservaDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.TableDocumentRepository;
import co.uniquindio.tiendasana.repos.mongo.VentaProductoDocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AdminAnalyticsServiceImpl implements AdminAnalyticsService {

    private final VentaProductoDocumentRepository ventaRepo;
    private final ReservaDocumentRepository reservaRepo;
    private final ProductoDocumentRepository productoRepo;
    private final TableDocumentRepository tableRepo;

    private static LocalDateTime startOfDay(LocalDate d) {
        return d.atStartOfDay();
    }

    private static LocalDateTime endOfDay(LocalDate d) {
        return d.atTime(23, 59, 59, 999_000_000);
    }

    private static boolean isPaidSale(VentaProductoDocument v) {
        Pago p = v.getPago();
        if (p == null || p.getStatus() == null) {
            return false;
        }
        String st = p.getStatus();
        String detail = p.getStatusDetail() != null ? p.getStatusDetail() : "";
        return ("approved".equalsIgnoreCase(st) || "APPROVED".equalsIgnoreCase(st))
                && "accredited".equalsIgnoreCase(detail);
    }

    /** Misma regla que ventas: pago acreditado. */
    private static boolean isPaidReservation(ReservaDocument r) {
        Pago p = r.getPago();
        if (p == null || p.getStatus() == null) {
            return false;
        }
        String st = p.getStatus();
        String detail = p.getStatusDetail() != null ? p.getStatusDetail() : "";
        return ("approved".equalsIgnoreCase(st) || "APPROVED".equalsIgnoreCase(st))
                && "accredited".equalsIgnoreCase(detail);
    }

    private Map<String, ProductoDocument> productMap() {
        return productoRepo.findAllByOrderByNombreAsc().stream()
                .collect(Collectors.toMap(ProductoDocument::getId, p -> p, (a, b) -> a));
    }

    @Override
    public DashboardOverviewDto dashboard(LocalDate from, LocalDate to, boolean comparePrevious) {
        LocalDateTime t0 = startOfDay(from);
        LocalDateTime t1 = endOfDay(to);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        LocalDateTime p0 = startOfDay(prevFrom);
        LocalDateTime p1 = endOfDay(prevTo);

        List<VentaProductoDocument> ventas = ventaRepo.findByFechaBetweenInclusive(t0, t1);
        List<VentaProductoDocument> ventasPrev = comparePrevious
                ? ventaRepo.findByFechaBetweenInclusive(p0, p1)
                : List.of();
        List<ReservaDocument> reservas = reservaRepo.findTouchingPeriod(t0, t1);
        Map<String, ProductoDocument> pmap = productMap();

        double totalPaid = ventas.stream().filter(AdminAnalyticsServiceImpl::isPaidSale).mapToDouble(VentaProductoDocument::getTotal).sum();
        double totalPrev = ventasPrev.stream().filter(AdminAnalyticsServiceImpl::isPaidSale).mapToDouble(VentaProductoDocument::getTotal).sum();
        Double trendSales = comparePrevious && totalPrev > 0 ? ((totalPaid - totalPrev) / totalPrev) * 100.0
                : comparePrevious && totalPrev == 0 && totalPaid > 0 ? 100.0 : null;

        long orders = ventas.size();
        long ordersPrev = ventasPrev.size();
        Double trendOrders = comparePrevious && ordersPrev > 0 ? ((orders - ordersPrev) / (double) ordersPrev) * 100.0
                : comparePrevious && ordersPrev == 0 && orders > 0 ? 100.0 : null;

        long clients = ventas.stream().map(VentaProductoDocument::getEmailUsuario).filter(Objects::nonNull).distinct().count();
        long clientsPrev = ventasPrev.stream().map(VentaProductoDocument::getEmailUsuario).filter(Objects::nonNull).distinct().count();
        Double trendClients = comparePrevious && clientsPrev > 0 ? ((clients - clientsPrev) / (double) clientsPrev) * 100.0 : null;

        LocalDate today = LocalDate.now();
        long resToday = reservas.stream()
                .filter(r -> r.getFechaReserva() != null && r.getFechaReserva().toLocalDate().equals(today))
                .count();

        List<KpiDto> kpis = List.of(
                new KpiDto("sales", "Ventas totales (periodo)", formatMoney(totalPaid), trendSales,
                        "Solo pagos acreditados"),
                new KpiDto("orders", "Pedidos registrados", String.valueOf(orders), trendOrders, "Órdenes creadas en el rango"),
                new KpiDto("clients", "Clientes atendidos", String.valueOf(clients), trendClients, "Correos distintos con compra"),
                new KpiDto("resToday", "Reservas hoy (fecha servicio)", String.valueOf(resToday), null, "Por fecha de reserva")
        );

        List<SeriesPointDto> seriesCur = salesByDaySeries(ventas, from, to, true);
        List<SeriesPointDto> seriesPrev = comparePrevious ? salesByDaySeries(ventasPrev, prevFrom, prevTo, true) : List.of();

        List<LabelValueDto> byWd = salesByWeekday(ventas);
        List<LabelValueDto> byHr = salesByHourSlot(ventas);
        List<ProductRankDto> top = topProducts(ventas, pmap, 5, true);
        List<ProductRankDto> bottom = topProducts(ventas, pmap, 5, false);
        List<LabelValueDto> resByDay = reservationsByDay(reservas, from, to);
        List<LabelValueDto> resByHr = reservationsByHour(reservas);
        List<ActivityRowDto> activity = recentActivity(ventas, reservas, 25);

        return new DashboardOverviewDto(from, to, kpis, seriesCur, seriesPrev, byWd, byHr, top, bottom, resByDay, resByHr, activity);
    }

    private static String formatMoney(double v) {
        return String.format(Locale.forLanguageTag("es-CO"), "$%,.0f", v);
    }

    private List<SeriesPointDto> salesByDaySeries(List<VentaProductoDocument> ventas, LocalDate from, LocalDate to, boolean paidOnly) {
        Map<LocalDate, Double> map = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            map.put(d, 0.0);
        }
        for (VentaProductoDocument v : ventas) {
            if (v.getFecha() == null) {
                continue;
            }
            if (paidOnly && !isPaidSale(v)) {
                continue;
            }
            LocalDate d = v.getFecha().toLocalDate();
            map.merge(d, v.getTotal(), Double::sum);
        }
        return map.entrySet().stream().map(e -> new SeriesPointDto(e.getKey(), e.getValue())).toList();
    }

    private List<LabelValueDto> salesByWeekday(List<VentaProductoDocument> ventas) {
        double[] sums = new double[7];
        for (VentaProductoDocument v : ventas) {
            if (!isPaidSale(v) || v.getFecha() == null) {
                continue;
            }
            int idx = v.getFecha().getDayOfWeek().getValue() - 1;
            sums[idx] += v.getTotal();
        }
        List<LabelValueDto> out = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            DayOfWeek dow = DayOfWeek.of(i + 1);
            String label = dow.getDisplayName(TextStyle.SHORT, new Locale("es", "CO"));
            out.add(new LabelValueDto(label, sums[i]));
        }
        return out;
    }

    private List<LabelValueDto> salesByHourSlot(List<VentaProductoDocument> ventas) {
        String[] labels = {"0h–5h", "6h–11h", "12h–17h", "18h–23h"};
        double[] sums = new double[4];
        for (VentaProductoDocument v : ventas) {
            if (!isPaidSale(v) || v.getFecha() == null) {
                continue;
            }
            int h = v.getFecha().getHour();
            int slot = h <= 5 ? 0 : h <= 11 ? 1 : h <= 17 ? 2 : 3;
            sums[slot] += v.getTotal();
        }
        List<LabelValueDto> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            list.add(new LabelValueDto(labels[i], sums[i]));
        }
        return list;
    }

    private List<ProductRankDto> topProducts(List<VentaProductoDocument> ventas, Map<String, ProductoDocument> pmap, int n, boolean top) {
        Map<String, long[]> units = new HashMap<>();
        Map<String, double[]> revenue = new HashMap<>();
        for (VentaProductoDocument v : ventas) {
            if (!isPaidSale(v) || v.getProductos() == null) {
                continue;
            }
            for (DetalleVentaProducto d : v.getProductos()) {
                if (d.getProductoId() == null) {
                    continue;
                }
                String pid = String.valueOf(d.getProductoId());
                units.computeIfAbsent(pid, k -> new long[]{0})[0] += d.getCantidad();
                revenue.computeIfAbsent(pid, k -> new double[]{0})[0] += d.getValor();
            }
        }
        Stream<Map.Entry<String, long[]>> stream = units.entrySet().stream();
        Comparator<Map.Entry<String, long[]>> cmp = Comparator.comparingLong(e -> e.getValue()[0]);
        stream = top ? stream.sorted(cmp.reversed()) : stream.sorted(cmp);
        return stream.limit(n).map(e -> {
            String pid = e.getKey();
            String name = pmap.containsKey(pid) ? pmap.get(pid).getNombre() : pid;
            double rev = revenue.getOrDefault(pid, new double[]{0})[0];
            return new ProductRankDto(pid, name, e.getValue()[0], rev);
        }).toList();
    }

    private List<LabelValueDto> reservationsByDay(List<ReservaDocument> reservas, LocalDate from, LocalDate to) {
        Map<LocalDate, Double> map = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            map.put(d, 0.0);
        }
        for (ReservaDocument r : reservas) {
            LocalDate d = r.getFechaReserva() != null ? r.getFechaReserva().toLocalDate()
                    : (r.getFechaCreacion() != null ? r.getFechaCreacion().toLocalDate() : null);
            if (d == null || d.isBefore(from) || d.isAfter(to)) {
                continue;
            }
            map.merge(d, 1.0, Double::sum);
        }
        return map.entrySet().stream().map(e -> new LabelValueDto(e.getKey().toString(), e.getValue())).toList();
    }

    private List<LabelValueDto> reservationsByHour(List<ReservaDocument> reservas) {
        String[] labels = {"0h–5h", "6h–11h", "12h–17h", "18h–23h"};
        double[] counts = new double[4];
        for (ReservaDocument r : reservas) {
            LocalDateTime t = r.getFechaReserva() != null ? r.getFechaReserva() : r.getFechaCreacion();
            if (t == null) {
                continue;
            }
            int h = t.getHour();
            int slot = h <= 5 ? 0 : h <= 11 ? 1 : h <= 17 ? 2 : 3;
            counts[slot] += 1;
        }
        List<LabelValueDto> list = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            list.add(new LabelValueDto(labels[i], counts[i]));
        }
        return list;
    }

    private List<ActivityRowDto> recentActivity(List<VentaProductoDocument> ventas, List<ReservaDocument> reservas, int limit) {
        record Ev(LocalDateTime t, ActivityRowDto row) {
        }
        List<Ev> ev = new ArrayList<>();
        for (VentaProductoDocument v : ventas) {
            if (v.getFecha() == null) {
                continue;
            }
            String estado = isPaidSale(v) ? "Pagada" : (v.getPago() == null ? "Pendiente pago" : "En proceso");
            ev.add(new Ev(v.getFecha(), new ActivityRowDto(
                    v.getId(),
                    v.getFecha(),
                    v.getTotal(),
                    "Pedido",
                    estado,
                    "analytics/sales?id=" + v.getId()
            )));
        }
        for (ReservaDocument r : reservas) {
            LocalDateTime t = r.getFechaCreacion() != null ? r.getFechaCreacion() : r.getFechaReserva();
            if (t == null) {
                continue;
            }
            String estado = r.getEstadoReserva() != null ? r.getEstadoReserva() : "";
            ev.add(new Ev(t, new ActivityRowDto(
                    r.getId(),
                    t,
                    r.getValorReserva(),
                    "Reserva",
                    estado,
                    "analytics/reservations?id=" + r.getId()
            )));
        }
        return ev.stream()
                .sorted(Comparator.comparing(Ev::t).reversed())
                .limit(limit)
                .map(Ev::row)
                .toList();
    }

    @Override
    public SalesAnalyticsDto sales(LocalDate from, LocalDate to, int page, int size, String query, String paymentFilter) {
        LocalDateTime t0 = startOfDay(from);
        LocalDateTime t1 = endOfDay(to);
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate prevTo = from.minusDays(1);
        LocalDate prevFrom = prevTo.minusDays(days - 1);
        List<VentaProductoDocument> ventas = ventaRepo.findByFechaBetweenInclusive(t0, t1);
        List<VentaProductoDocument> prev = ventaRepo.findByFechaBetweenInclusive(startOfDay(prevFrom), endOfDay(prevTo));
        Map<String, ProductoDocument> pmap = productMap();

        List<VentaProductoDocument> paid = ventas.stream().filter(AdminAnalyticsServiceImpl::isPaidSale).toList();
        double total = paid.stream().mapToDouble(VentaProductoDocument::getTotal).sum();
        double prevTotal = prev.stream().filter(AdminAnalyticsServiceImpl::isPaidSale).mapToDouble(VentaProductoDocument::getTotal).sum();
        Double growth = prevTotal > 0 ? ((total - prevTotal) / prevTotal) * 100.0 : (total > 0 ? 100.0 : null);
        double ticket = paid.isEmpty() ? 0 : total / paid.size();

        List<KpiDto> kpis = List.of(
                new KpiDto("total", "Ventas totales", formatMoney(total), growth, "Pagos acreditados"),
                new KpiDto("ticket", "Ticket promedio", formatMoney(ticket), null, "Por pedido pagado"),
                new KpiDto("growth", "Vs periodo anterior", growth == null ? "—" : String.format(Locale.US, "%.1f %%", growth), null, prevFrom + " → " + prevTo)
        );

        List<SeriesPointDto> cur = salesByDaySeries(ventas, from, to, true);
        List<SeriesPointDto> pser = salesByDaySeries(prev, prevFrom, prevTo, true);

        Map<String, Double> byCat = new HashMap<>();
        Map<String, Double> byPay = new HashMap<>();
        Map<String, long[]> byProd = new HashMap<>();
        for (VentaProductoDocument v : paid) {
            if (v.getProductos() != null) {
                for (DetalleVentaProducto d : v.getProductos()) {
                    String pid = String.valueOf(d.getProductoId());
                    String cat = pmap.containsKey(pid) ? pmap.get(pid).getCategoria() : "Sin categoría";
                    byCat.merge(cat, (double) d.getValor(), Double::sum);
                    byProd.computeIfAbsent(pid, k -> new long[]{0})[0] += d.getCantidad();
                }
            }
            Pago p = v.getPago();
            String method = p != null && p.getPaymentType() != null ? p.getPaymentType() : "Desconocido";
            byPay.merge(method, v.getTotal(), Double::sum);
        }
        List<LabelValueDto> catRows = byCat.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> new LabelValueDto(e.getKey(), e.getValue()))
                .toList();
        List<ProductRankDto> prodRows = byProd.entrySet().stream()
                .map(e -> {
                    String pid = e.getKey();
                    String name = pmap.containsKey(pid) ? pmap.get(pid).getNombre() : pid;
                    double rev = paid.stream().flatMap(x -> x.getProductos() != null ? x.getProductos().stream() : Stream.empty())
                            .filter(d -> String.valueOf(d.getProductoId()).equals(pid))
                            .mapToDouble(DetalleVentaProducto::getValor).sum();
                    return new ProductRankDto(pid, name, e.getValue()[0], rev);
                })
                .sorted(Comparator.comparingLong(ProductRankDto::unitsSold).reversed())
                .toList();
        List<LabelValueDto> payRows = byPay.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(e -> new LabelValueDto(e.getKey(), e.getValue()))
                .toList();

        String q = query == null ? "" : query.trim().toLowerCase();
        String pf = paymentFilter == null ? "ALL" : paymentFilter.toUpperCase();
        List<VentaProductoDocument> filtered = ventas.stream()
                .filter(v -> q.isEmpty() || (v.getId() != null && v.getId().toLowerCase().contains(q))
                        || (v.getEmailUsuario() != null && v.getEmailUsuario().toLowerCase().contains(q)))
                .filter(v -> switch (pf) {
                    case "PAID" -> isPaidSale(v);
                    case "PENDING" -> !isPaidSale(v);
                    default -> true;
                })
                .sorted(Comparator.comparing(VentaProductoDocument::getFecha, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        int totalE = filtered.size();
        int pages = size <= 0 ? 1 : Math.max(1, (int) Math.ceil(totalE / (double) size));
        int p = Math.max(0, page);
        int s = Math.min(200, Math.max(1, size <= 0 ? 20 : size));
        int fromIdx = Math.min(p * s, totalE);
        int toIdx = Math.min(fromIdx + s, totalE);
        List<SalesOrderRowDto> slice = filtered.subList(fromIdx, toIdx).stream().map(this::toSalesRow).toList();
        PagedTableDto<SalesOrderRowDto> table = new PagedTableDto<>(slice, totalE, p, s, pages);

        return new SalesAnalyticsDto(from, to, kpis, cur, pser, catRows, prodRows, payRows, table);
    }

    private SalesOrderRowDto toSalesRow(VentaProductoDocument v) {
        Pago p = v.getPago();
        return new SalesOrderRowDto(
                v.getId(),
                v.getFecha(),
                v.getTotal(),
                v.getEmailUsuario(),
                p != null ? p.getPaymentType() : null,
                p != null ? p.getStatus() : null,
                isPaidSale(v)
        );
    }

    @Override
    public ReservationsAnalyticsDto reservations(LocalDate from, LocalDate to, int page, int size, String query, String estadoFilter) {
        LocalDateTime t0 = startOfDay(from);
        LocalDateTime t1 = endOfDay(to);
        List<ReservaDocument> reservas = reservaRepo.findTouchingPeriod(t0, t1);

        long total = reservas.size();
        long cancelled = reservas.stream().map(ReservaDocument::getEstadoReserva)
                .filter(Objects::nonNull)
                .filter(s -> s.toUpperCase(Locale.ROOT).contains("CANCEL"))
                .count();
        long visibleTables = Math.max(1, tableRepo.countByVisibleToClientTrue());
        long paidCount = reservas.stream().filter(AdminAnalyticsServiceImpl::isPaidReservation).count();
        double occPct = Math.min(100.0, (paidCount / (double) visibleTables) * 10.0);
        double rotation = paidCount / (double) visibleTables;

        List<KpiDto> kpis = List.of(
                new KpiDto("tot", "Total reservas", String.valueOf(total), null, "En el periodo"),
                new KpiDto("can", "Cancelaciones", String.valueOf(cancelled), null, null),
                new KpiDto("occ", "Indicador ocupación", String.format(Locale.US, "%.1f %%", occPct), null, "Heurística: pagadas vs. mesas visibles"),
                new KpiDto("rot", "Rotación (reservas / mesa)", String.format(Locale.US, "%.2f", rotation), null, "Pagadas acreditadas / mesas visibles")
        );

        List<LabelValueDto> byDay = reservationsByDay(reservas, from, to);
        List<LabelValueDto> byHr = reservationsByHour(reservas);

        double avgHours = reservas.stream()
                .filter(AdminAnalyticsServiceImpl::isPaidReservation)
                .filter(r -> r.getFechaReserva() != null && r.getFechaFinReserva() != null)
                .mapToDouble(r -> ChronoUnit.MINUTES.between(r.getFechaReserva(), r.getFechaFinReserva()) / 60.0)
                .average().orElse(0);

        String q = query == null ? "" : query.trim().toLowerCase();
        String es = estadoFilter == null ? "" : estadoFilter.trim().toLowerCase();
        List<ReservaDocument> filtered = reservas.stream()
                .filter(r -> q.isEmpty() || (r.getId() != null && r.getId().toLowerCase().contains(q))
                        || (r.getUsuarioId() != null && r.getUsuarioId().toLowerCase().contains(q)))
                .filter(r -> es.isEmpty() || (r.getEstadoReserva() != null && r.getEstadoReserva().toLowerCase().contains(es)))
                .sorted(Comparator.comparing(ReservaDocument::getFechaCreacion, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();

        int totalE = filtered.size();
        int s = Math.min(200, Math.max(1, size <= 0 ? 20 : size));
        int p = Math.max(0, page);
        int pages = Math.max(1, (int) Math.ceil(totalE / (double) s));
        int fromIdx = Math.min(p * s, totalE);
        int toIdx = Math.min(fromIdx + s, totalE);
        List<ReservationRowDto> slice = filtered.subList(fromIdx, toIdx).stream().map(this::toResRow).toList();
        PagedTableDto<ReservationRowDto> table = new PagedTableDto<>(slice, totalE, p, s, pages);

        return new ReservationsAnalyticsDto(from, to, kpis, byDay, byHr,
                avgHours > 0 ? avgHours : null,
                rotation > 0 ? rotation : null,
                table);
    }

    private ReservationRowDto toResRow(ReservaDocument r) {
        return new ReservationRowDto(
                r.getId(),
                r.getFechaCreacion(),
                r.getFechaReserva(),
                r.getValorReserva(),
                r.getUsuarioId(),
                r.getEstadoReserva(),
                isPaidReservation(r)
        );
    }

    @Override
    public List<ProductPerformanceDto> productPerformance(LocalDate from, LocalDate to) {
        List<VentaProductoDocument> ventas = ventaRepo.findByFechaBetweenInclusive(startOfDay(from), endOfDay(to));
        Map<String, Long> units = new HashMap<>();
        for (VentaProductoDocument v : ventas) {
            if (!isPaidSale(v) || v.getProductos() == null) {
                continue;
            }
            for (DetalleVentaProducto d : v.getProductos()) {
                String pid = String.valueOf(d.getProductoId());
                units.merge(pid, (long) d.getCantidad(), Long::sum);
            }
        }
        if (units.isEmpty()) {
            return productoRepo.findAllByOrderByNombreAsc().stream()
                    .map(p -> new ProductPerformanceDto(p.getId(), 0L, "sin ventas"))
                    .toList();
        }
        long max = units.values().stream().mapToLong(Long::longValue).max().orElse(1);
        long minNonZero = units.values().stream().filter(u -> u > 0).mapToLong(Long::longValue).min().orElse(0);
        Map<String, ProductoDocument> pmap = productMap();
        return pmap.values().stream().map(p -> {
            long u = units.getOrDefault(p.getId(), 0L);
            String pop;
            if (u == 0) {
                pop = "sin ventas";
            } else if (u >= max * 0.7) {
                pop = "alta rotación";
            } else if (minNonZero > 0 && u <= minNonZero * 1.5 && u < max * 0.3) {
                pop = "baja rotación";
            } else {
                pop = "media";
            }
            return new ProductPerformanceDto(p.getId(), u, pop);
        }).sorted(Comparator.comparingLong(ProductPerformanceDto::unitsSold).reversed()).toList();
    }

    @Override
    public List<TablePerformanceDto> tablePerformance(LocalDate from, LocalDate to) {
        LocalDateTime t0 = startOfDay(from);
        LocalDateTime t1 = endOfDay(to);
        List<ReservaDocument> reservas = reservaRepo.findTouchingPeriod(t0, t1);
        Map<String, long[]> byTable = new HashMap<>();
        for (ReservaDocument r : reservas) {
            if (r.getMesas() == null) {
                continue;
            }
            r.getMesas().forEach(m -> {
                if (m.getId() != null) {
                    byTable.computeIfAbsent(m.getId(), k -> new long[]{0})[0] += 1;
                }
            });
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        long visible = Math.max(1, tableRepo.countByVisibleToClientTrue());
        return tableRepo.findAll().stream().map(td -> {
            long c = byTable.getOrDefault(td.getId(), new long[]{0})[0];
            double rate = Math.min(100.0, (c / (double) Math.max(1, days)) / visible * 100.0);
            return new TablePerformanceDto(td.getId(), td.getNombre(), c, rate);
        }).sorted(Comparator.comparingLong(TablePerformanceDto::reservationsInPeriod).reversed()).toList();
    }
}
