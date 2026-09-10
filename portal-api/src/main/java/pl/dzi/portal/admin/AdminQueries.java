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
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Model odczytu panelu administracyjnego (ADR-0009, Faza A). Wyłącznie SELECT-y —
 * panel niczego nie zapisuje. Interfejs istnieje po to, żeby testy plasterkowe
 * (WebMvcTest) podstawiały wersję in-memory, a SQL był sprawdzany osobno na
 * prawdziwym SQL Serverze (JdbcAdminQueriesIT).
 *
 * Definicja „wejścia": wpis audytu {@code APP_OPEN} z {@code object_ref = 'tile:<kod>'}
 * (otwarcie strony modułu). Pobrania danych, css/js i odświeżenia z 304 nie liczą się.
 * „Wejście na portal" = {@code TILES_LIST} (strona główna).
 */
public interface AdminQueries {

    record TileRow(long id, String code, String name, String tileType, boolean active, int displayOrder) {
    }

    record PermissionRow(String tileCode, String adGroup, String level) {
    }

    record TileVisits(long visits30, long visits90, Instant lastVisit) {
    }

    record Visitor(String login, long visits, Instant lastVisit) {
    }

    record KnownLogin(String login, Instant firstSeen, Instant lastSeen, long requests) {
    }

    record UserTileVisits(String tileCode, long visits, Instant lastVisit) {
    }

    record TileStat(String tileCode, long visits, long distinctUsers) {
    }

    record UserStat(String login, long visits, long distinctTiles) {
    }

    record PortalStat(long visits, long distinctUsers) {
    }

    record AuditRow(long id, Instant ts, String username, String clientIp, String httpMethod, String path,
                    String action, String objectRef, String status, int httpStatus) {
    }

    /** Wszystkie kafelki, także nieaktywne, w kolejności strony głównej. */
    List<TileRow> tiles();

    /** Wszystkie wpisy uprawnień z kodem kafelka. */
    List<PermissionRow> permissions();

    /** Wejścia per kafelek: w oknie 30 dni, 90 dni i ostatnie wejście (bez okna). Klucz = kod kafelka. */
    Map<String, TileVisits> visitsPerTile(Instant since30, Instant since90);

    /** Kto faktycznie wchodził do modułu — z audytu, malejąco po ostatnim wejściu. */
    List<Visitor> visitorsOfTile(String tileCode);

    /** Loginy, które kiedykolwiek pojawiły się w audycie (filtr = fragment loginu, pusty = wszystkie). */
    List<KnownLogin> knownLogins(String filter);

    /** Wejścia danej osoby per kafelek. */
    List<UserTileVisits> visitsOfUser(String login);

    /** Pierwsze/ostatnie pojawienie się loginu w audycie; empty = login nieznany portalowi. */
    Optional<KnownLogin> presence(String login);

    List<TileStat> tileStats(Instant from, Instant to);

    List<UserStat> userStats(Instant from, Instant to);

    PortalStat portalStats(Instant from, Instant to);

    /** Ostatnie wpisy audytu (limit 1–500), filtry opcjonalne (null/puste = brak filtra). */
    List<AuditRow> recentAudit(int limit, String login, String action, String tileCode);
}
