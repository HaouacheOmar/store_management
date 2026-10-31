package com.store.reporting.controller;

import com.store.common.security.CurrentUser;
import com.store.common.web.ApiException;
import com.store.reporting.entity.DailyCashierSales;
import com.store.reporting.repository.DailyCashierSalesRepository;
import com.store.reporting.repository.DailyProductSalesRepository;
import com.store.reporting.repository.RankRow;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lectures du modèle de reporting (PRD M7, M8, M9) via les repositories JPA.
 * Pas de couche service tant qu'il n'y a que de la lecture. Les dates sont des jours du store.
 */
@RestController
@RequestMapping("/reporting")
@Transactional(readOnly = true)
public class ReportingController {

    public enum RankBy { QTY, REVENUE }

    public record MeToday(BigDecimal cashed, int tickets) {
    }

    public record CashierTotal(long cashierId, String name, int tickets, BigDecimal total) {
    }

    public record Ranked(long id, String name, int qty, BigDecimal revenue) {
        static Ranked of(RankRow r) {
            return new Ranked(r.id(), r.name(), r.qty().intValue(), r.revenue());
        }
    }

    public record Today(BigDecimal cashed, int tickets, int items, List<CashierTotal> cashiers, List<Ranked> products) {
    }

    public record DayPoint(LocalDate date, BigDecimal revenue, int tickets) {
    }

    public record Revenue(LocalDate from, LocalDate to, BigDecimal revenue, int tickets, BigDecimal averageBasket,
                          int itemsSold, List<DayPoint> series) {
    }

    private final DailyProductSalesRepository products;
    private final DailyCashierSalesRepository cashiers;

    public ReportingController(DailyProductSalesRepository products, DailyCashierSalesRepository cashiers) {
        this.products = products;
        this.cashiers = cashiers;
    }

    /** M9 : ce que j'ai encaissé aujourd'hui (jamais le CA du store). */
    @GetMapping("/me/today")
    public MeToday meToday() {
        var me = CurrentUser.get();
        return cashiers.findById(new DailyCashierSales.Key(me.storeId(), me.today(), me.userId()))
                .map(c -> new MeToday(c.getTotal(), c.getTicketCount()))
                .orElse(new MeToday(BigDecimal.ZERO, 0));
    }

    /** M7 : encaissé du jour par caissier + liste des produits vendus aujourd'hui. */
    @GetMapping("/today")
    @PreAuthorize("hasAuthority('SALES_VIEW_ALL')")
    public Today today() {
        var me = CurrentUser.get();
        List<CashierTotal> byCashier = cashiers.findByIdStoreIdAndIdSaleDateOrderByTotalDesc(me.storeId(), me.today())
                .stream()
                .filter(c -> c.getTicketCount() != 0 || c.getTotal().signum() != 0) // caissier dont toutes les ventes sont annulées
                .map(c -> new CashierTotal(c.getId().cashierId(), c.getCashierName(), c.getTicketCount(), c.getTotal()))
                .toList();
        List<Ranked> sold = products
                .findByIdStoreIdAndIdSaleDateAndQtyGreaterThanOrderByQtyDescProductNameAsc(me.storeId(), me.today(), 0)
                .stream()
                .map(p -> new Ranked(p.getId().productId(), p.getProductName(), p.getQty(), p.getRevenue()))
                .toList();
        return new Today(
                byCashier.stream().map(CashierTotal::total).reduce(BigDecimal.ZERO, BigDecimal::add),
                byCashier.stream().mapToInt(CashierTotal::tickets).sum(),
                sold.stream().mapToInt(Ranked::qty).sum(),
                byCashier, sold);
    }

    /** M8 : chiffre d'affaires sur une période, KPIs et série jour par jour (jours sans vente = 0). */
    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('STATS_VIEW')")
    public Revenue revenue(@RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to) {
        var me = CurrentUser.get();
        LocalDate end = to != null ? to : me.today();
        LocalDate start = checkPeriod(from != null ? from : end.minusDays(6), end);

        Map<LocalDate, List<DailyCashierSales>> byDay = cashiers.findByIdStoreIdAndIdSaleDateBetween(me.storeId(), start, end)
                .stream().collect(Collectors.groupingBy(c -> c.getId().saleDate()));
        List<DayPoint> series = start.datesUntil(end.plusDays(1)).map(day -> {
            List<DailyCashierSales> rows = byDay.getOrDefault(day, List.of());
            return new DayPoint(day,
                    rows.stream().map(DailyCashierSales::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add),
                    rows.stream().mapToInt(DailyCashierSales::getTicketCount).sum());
        }).toList();

        BigDecimal revenue = series.stream().map(DayPoint::revenue).reduce(BigDecimal.ZERO, BigDecimal::add);
        int tickets = series.stream().mapToInt(DayPoint::tickets).sum();
        BigDecimal basket = tickets == 0 ? BigDecimal.ZERO
                : revenue.divide(BigDecimal.valueOf(tickets), 2, RoundingMode.HALF_UP);
        int items = (int) products.itemsSold(me.storeId(), start, end);
        return new Revenue(start, end, revenue, tickets, basket, items, series);
    }

    /** Graphe « produits les plus vendus », filtrable par période, catégorie, top N, quantité ou montant. */
    @GetMapping("/top-products")
    @PreAuthorize("hasAuthority('STATS_VIEW')")
    public List<Ranked> topProducts(@RequestParam(required = false) LocalDate from,
                                    @RequestParam(required = false) LocalDate to,
                                    @RequestParam(required = false) Long categoryId,
                                    @RequestParam(defaultValue = "10") int limit,
                                    @RequestParam(defaultValue = "QTY") RankBy by) {
        var me = CurrentUser.get();
        LocalDate end = to != null ? to : me.today();
        LocalDate start = checkPeriod(from != null ? from : end.minusDays(6), end);
        return products.rankProducts(me.storeId(), start, end, categoryId, page(limit, by))
                .stream().map(Ranked::of).toList();
    }

    /** Même graphe par catégorie. */
    @GetMapping("/top-categories")
    @PreAuthorize("hasAuthority('STATS_VIEW')")
    public List<Ranked> topCategories(@RequestParam(required = false) LocalDate from,
                                      @RequestParam(required = false) LocalDate to,
                                      @RequestParam(defaultValue = "10") int limit,
                                      @RequestParam(defaultValue = "QTY") RankBy by) {
        var me = CurrentUser.get();
        LocalDate end = to != null ? to : me.today();
        LocalDate start = checkPeriod(from != null ? from : end.minusDays(6), end);
        return products.rankCategories(me.storeId(), start, end, page(limit, by))
                .stream().map(Ranked::of).toList();
    }

    /** Tri sur des agrégats : expressions constantes du code (JpaSort.unsafe), jamais une valeur reçue. */
    private static PageRequest page(int limit, RankBy by) {
        if (limit < 1 || limit > 50) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "limit doit être entre 1 et 50");
        }
        String first = by == RankBy.QTY ? "sum(d.qty)" : "sum(d.revenue)";
        String second = by == RankBy.QTY ? "sum(d.revenue)" : "sum(d.qty)";
        return PageRequest.of(0, limit, JpaSort.unsafe(Sort.Direction.DESC, first).andUnsafe(Sort.Direction.DESC, second));
    }

    private static LocalDate checkPeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "La date de début est après la date de fin");
        }
        if (ChronoUnit.DAYS.between(from, to) > 366) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Période limitée à un an");
        }
        return from;
    }
}
