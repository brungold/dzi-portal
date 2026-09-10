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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Odpowiedzi /api/admin/** — rekordy, jak w pozostałych modułach (ADR-0001: bez MapStruct). */
public final class AdminViews {

    private AdminViews() {
    }

    /** Rodzaj wpisu w tile_permissions.ad_group: login imienny, skrót departamentu albo „wszyscy". */
    public enum PrincipalKind { LOGIN, DEPARTMENT, ALL }

    public record TileOverview(String code, String name, String tileType, boolean active, int displayOrder,
                               int permissionCount, long visits30, long visits90, Instant lastVisit) {
    }

    /** knownInAudit: dla LOGIN — czy login kiedykolwiek wszedł na portal (literówka = false); dla reszty zawsze true. */
    public record PermissionView(String principal, PrincipalKind kind, String level, boolean knownInAudit) {
    }

    public record VisitorView(String login, long visits, Instant lastVisit) {
    }

    public record TileDetail(String code, String name, String tileType, boolean active, int displayOrder,
                             List<PermissionView> permissions, List<VisitorView> visitors) {
    }

    /** via: „login" (imiennie), „wszyscy" albo skrót departamentu, przez który kafelek jest widoczny. */
    public record TileGrant(String code, String name, String level, String via) {
    }

    public record UserVisitView(String code, String name, long visits, Instant lastVisit) {
    }

    public record UserView(String login, boolean knownInAudit, Instant firstSeen, Instant lastSeen,
                           List<TileGrant> byLogin, List<TileGrant> forAll, List<TileGrant> byDepartment,
                           List<UserVisitView> visits) {
    }

    public record LoginView(String login, Instant firstSeen, Instant lastSeen, long requests) {
    }

    public record TileStatView(String code, String name, long visits, long distinctUsers) {
    }

    public record UserStatView(String login, long visits, long distinctTiles) {
    }

    public record StatsView(LocalDate from, LocalDate to, long portalVisits, long portalUsers,
                            List<TileStatView> perTile, List<UserStatView> perUser) {
    }

    public record AuditView(long id, Instant ts, String username, String clientIp, String httpMethod, String path,
                            String action, String objectRef, String status, int httpStatus) {
    }
}
