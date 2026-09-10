/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.admin;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import pl.dzi.portal.admin.AdminQueries.KnownLogin;
import pl.dzi.portal.admin.AdminQueries.PermissionRow;
import pl.dzi.portal.admin.AdminQueries.TileRow;
import pl.dzi.portal.admin.AdminQueries.TileVisits;
import pl.dzi.portal.admin.AdminViews.AuditView;
import pl.dzi.portal.admin.AdminViews.LoginView;
import pl.dzi.portal.admin.AdminViews.PermissionView;
import pl.dzi.portal.admin.AdminViews.PrincipalKind;
import pl.dzi.portal.admin.AdminViews.StatsView;
import pl.dzi.portal.admin.AdminViews.TileDetail;
import pl.dzi.portal.admin.AdminViews.TileGrant;
import pl.dzi.portal.admin.AdminViews.TileOverview;
import pl.dzi.portal.admin.AdminViews.TileStatView;
import pl.dzi.portal.admin.AdminViews.UserStatView;
import pl.dzi.portal.admin.AdminViews.UserView;
import pl.dzi.portal.admin.AdminViews.UserVisitView;
import pl.dzi.portal.admin.AdminViews.VisitorView;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Panel administracyjny, Faza A (ADR-0009): wyłącznie odczyt. Składa widoki z modelu
 * odczytu; nie zna Springa Web poza ResponseStatusException (jak TilesFacade).
 *
 * Reguły:
 *  - uprawnienia rozpoznaje po kształcie wartości ad_group: „wszyscy" → ALL,
 *    zawiera kropkę → LOGIN (nazwisko.imię / imię.nazwisko / sufiksy), reszta → DEPARTMENT,
 *  - „login nieznany w audycie" = nigdy nie pojawił się w audit_log.username; to jest
 *    wykrywacz literówek w tile_permissions (zła pisownia = kafelek niewidoczny bez błędu),
 *  - portal NIE zna departamentu osoby (ADR-0005/0008: departament przychodzi z nagłówka
 *    per żądanie), więc „co widzi osoba" pokazuje kafelki departamentowe jako warunkowe.
 */
public class AdminFacade {

    static final String ADMIN_TILE = "administracja";
    static final String ALL = "wszyscy";
    static final int DEFAULT_WINDOW_DAYS = 30;
    static final int MAX_AUDIT_LIMIT = 500;
    private static final ZoneId PL = ZoneId.of("Europe/Warsaw");
    private static final Pattern LOGIN = Pattern.compile("^[a-z0-9._-]{1,64}$");
    private static final Pattern TILE_CODE = Pattern.compile("^[a-zA-Z0-9_-]{1,50}$");

    private final AdminQueries queries;
    private final Clock clock;

    public AdminFacade(AdminQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    public List<TileOverview> tilesOverview() {
        Instant now = clock.instant();
        Map<String, TileVisits> visits = queries.visitsPerTile(now.minus(30, ChronoUnit.DAYS), now.minus(90, ChronoUnit.DAYS));
        Map<String, Long> permissionCounts = queries.permissions().stream()
                .collect(Collectors.groupingBy(PermissionRow::tileCode, Collectors.counting()));
        return queries.tiles().stream()
                .map(tile -> {
                    TileVisits v = visits.getOrDefault(tile.code(), new TileVisits(0, 0, null));
                    return new TileOverview(tile.code(), tile.name(), tile.tileType(), tile.active(), tile.displayOrder(),
                            permissionCounts.getOrDefault(tile.code(), 0L).intValue(), v.visits30(), v.visits90(), v.lastVisit());
                })
                .toList();
    }

    public TileDetail tileDetail(String code) {
        String tileCode = requireTileCode(code);
        TileRow tile = queries.tiles().stream()
                .filter(candidate -> candidate.code().equals(tileCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kafelek nie istnieje"));
        Set<String> known = knownLoginSet();
        List<PermissionView> permissions = queries.permissions().stream()
                .filter(permission -> permission.tileCode().equals(tileCode))
                .map(permission -> toPermissionView(permission, known))
                .toList();
        List<VisitorView> visitors = queries.visitorsOfTile(tileCode).stream()
                .map(visitor -> new VisitorView(visitor.login(), visitor.visits(), visitor.lastVisit()))
                .toList();
        return new TileDetail(tile.code(), tile.name(), tile.tileType(), tile.active(), tile.displayOrder(),
                permissions, visitors);
    }

    public UserView userView(String rawLogin) {
        String login = requireLogin(rawLogin);
        Map<String, String> names = tileNames();
        List<PermissionRow> all = queries.permissions();
        List<TileGrant> byLogin = all.stream()
                .filter(permission -> permission.adGroup().equalsIgnoreCase(login))
                .map(permission -> grant(permission, names, "login"))
                .toList();
        List<TileGrant> forAll = all.stream()
                .filter(permission -> kindOf(permission.adGroup()) == PrincipalKind.ALL)
                .map(permission -> grant(permission, names, ALL))
                .toList();
        List<TileGrant> byDepartment = all.stream()
                .filter(permission -> kindOf(permission.adGroup()) == PrincipalKind.DEPARTMENT)
                .map(permission -> grant(permission, names, permission.adGroup().toLowerCase(Locale.ROOT)))
                .toList();
        List<UserVisitView> visits = queries.visitsOfUser(login).stream()
                .map(visit -> new UserVisitView(visit.tileCode(), names.getOrDefault(visit.tileCode(), visit.tileCode()),
                        visit.visits(), visit.lastVisit()))
                .toList();
        Optional<KnownLogin> presence = queries.presence(login);
        return new UserView(login, presence.isPresent(),
                presence.map(KnownLogin::firstSeen).orElse(null), presence.map(KnownLogin::lastSeen).orElse(null),
                byLogin, forAll, byDepartment, visits);
    }

    public List<LoginView> logins(String filter) {
        return queries.knownLogins(filter == null ? "" : filter).stream()
                .map(known -> new LoginView(known.login(), known.firstSeen(), known.lastSeen(), known.requests()))
                .toList();
    }

    public StatsView stats(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), PL);
        LocalDate effectiveTo = to == null ? today : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.minusDays(DEFAULT_WINDOW_DAYS) : from;
        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data 'od' jest późniejsza niż 'do'");
        }
        Instant start = effectiveFrom.atStartOfDay(PL).toInstant();
        Instant end = effectiveTo.plusDays(1).atStartOfDay(PL).toInstant();
        Map<String, String> names = tileNames();
        List<TileStatView> perTile = queries.tileStats(start, end).stream()
                .map(stat -> new TileStatView(stat.tileCode(), names.getOrDefault(stat.tileCode(), stat.tileCode()),
                        stat.visits(), stat.distinctUsers()))
                .toList();
        List<UserStatView> perUser = queries.userStats(start, end).stream()
                .map(stat -> new UserStatView(stat.login(), stat.visits(), stat.distinctTiles()))
                .toList();
        AdminQueries.PortalStat portal = queries.portalStats(start, end);
        return new StatsView(effectiveFrom, effectiveTo, portal.visits(), portal.distinctUsers(), perTile, perUser);
    }

    public List<AuditView> recentAudit(Integer limit, String login, String action, String tileCode) {
        int effectiveLimit = limit == null ? 50 : Math.max(1, Math.min(limit, MAX_AUDIT_LIMIT));
        String effectiveLogin = login == null || login.isBlank() ? null : requireLogin(login);
        String effectiveCode = tileCode == null || tileCode.isBlank() ? null : requireTileCode(tileCode);
        String effectiveAction = action == null || action.isBlank() ? null : action.trim().toUpperCase(Locale.ROOT);
        return queries.recentAudit(effectiveLimit, effectiveLogin, effectiveAction, effectiveCode).stream()
                .map(row -> new AuditView(row.id(), row.ts(), row.username(), row.clientIp(), row.httpMethod(),
                        row.path(), row.action(), row.objectRef(), row.status(), row.httpStatus()))
                .toList();
    }

    // ----------------------------------------------------------------------

    static PrincipalKind kindOf(String principal) {
        String value = principal == null ? "" : principal.trim().toLowerCase(Locale.ROOT);
        if (ALL.equals(value)) {
            return PrincipalKind.ALL;
        }
        return value.contains(".") ? PrincipalKind.LOGIN : PrincipalKind.DEPARTMENT;
    }

    private static PermissionView toPermissionView(PermissionRow permission, Set<String> knownLogins) {
        PrincipalKind kind = kindOf(permission.adGroup());
        boolean known = kind != PrincipalKind.LOGIN
                || knownLogins.contains(permission.adGroup().trim().toLowerCase(Locale.ROOT));
        return new PermissionView(permission.adGroup(), kind, permission.level(), known);
    }

    private static TileGrant grant(PermissionRow permission, Map<String, String> names, String via) {
        return new TileGrant(permission.tileCode(), names.getOrDefault(permission.tileCode(), permission.tileCode()),
                permission.level(), via);
    }

    private Map<String, String> tileNames() {
        return queries.tiles().stream().collect(Collectors.toMap(TileRow::code, TileRow::name, (a, b) -> a));
    }

    private Set<String> knownLoginSet() {
        return queries.knownLogins("").stream()
                .map(KnownLogin::login)
                .map(login -> login.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String requireLogin(String raw) {
        String login = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (!LOGIN.matcher(login).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Niepoprawny login");
        }
        return login;
    }

    private static String requireTileCode(String raw) {
        String code = raw == null ? "" : raw.trim();
        if (!TILE_CODE.matcher(code).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Niepoprawny kod kafelka");
        }
        return code;
    }
}
