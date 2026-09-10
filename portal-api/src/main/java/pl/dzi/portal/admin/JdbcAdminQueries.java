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

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Implementacja na JdbcTemplate — jawny T-SQL, jak przy audycie (ADR-0001 dec. 2).
 * Wszystkie zapytania czytają po istniejących indeksach audit_log:
 * ix_audit_log_action (action, ts_utc) i ix_audit_log_user (username, ts_utc).
 * Czas: ts_utc jest DATETIME2 w UTC — czytany jako LocalDateTime i przypinany do UTC,
 * nigdy przez java.sql.Timestamp (lekcja z AuditPersistenceIT, 2026-08-06).
 */
@Component
@RequiredArgsConstructor
public class JdbcAdminQueries implements AdminQueries {

    private static final String TILE_PREFIX = "tile:";
    private static final String APP_OPEN = "APP_OPEN";
    private static final String TILES_LIST = "TILES_LIST";

    private final JdbcTemplate jdbc;

    @Override
    public List<TileRow> tiles() {
        return jdbc.query("""
                SELECT id, code, name, tile_type, active, display_order
                FROM tiles
                ORDER BY display_order, name
                """, (rs, i) -> new TileRow(rs.getLong("id"), rs.getString("code"), rs.getString("name"),
                rs.getString("tile_type"), rs.getBoolean("active"), rs.getInt("display_order")));
    }

    @Override
    public List<PermissionRow> permissions() {
        return jdbc.query("""
                SELECT t.code, p.ad_group, p.permission_level
                FROM tile_permissions p
                JOIN tiles t ON t.id = p.tile_id
                ORDER BY t.display_order, t.name, p.ad_group
                """, (rs, i) -> new PermissionRow(rs.getString("code"), rs.getString("ad_group"),
                rs.getString("permission_level")));
    }

    @Override
    public Map<String, TileVisits> visitsPerTile(Instant since30, Instant since90) {
        Map<String, TileVisits> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT object_ref,
                       SUM(CASE WHEN ts_utc >= ? THEN 1 ELSE 0 END) AS visits30,
                       SUM(CASE WHEN ts_utc >= ? THEN 1 ELSE 0 END) AS visits90,
                       MAX(ts_utc) AS last_visit
                FROM audit_log
                WHERE action = ? AND object_ref LIKE 'tile:%'
                GROUP BY object_ref
                """, rs -> {
                    result.put(stripTile(rs.getString("object_ref")),
                            new TileVisits(rs.getLong("visits30"), rs.getLong("visits90"), instant(rs, "last_visit")));
                }, utc(since30), utc(since90), APP_OPEN);
        return result;
    }

    @Override
    public List<Visitor> visitorsOfTile(String tileCode) {
        return jdbc.query("""
                SELECT username, COUNT(*) AS visits, MAX(ts_utc) AS last_visit
                FROM audit_log
                WHERE action = ? AND object_ref = ?
                GROUP BY username
                ORDER BY last_visit DESC
                """, (rs, i) -> new Visitor(rs.getString("username"), rs.getLong("visits"), instant(rs, "last_visit")),
                APP_OPEN, TILE_PREFIX + tileCode);
    }

    @Override
    public List<KnownLogin> knownLogins(String filter) {
        String pattern = "%" + escapeLike(filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT)) + "%";
        return jdbc.query("""
                SELECT username, MIN(ts_utc) AS first_seen, MAX(ts_utc) AS last_seen, COUNT(*) AS requests
                FROM audit_log
                WHERE username <> '-' AND username LIKE ? ESCAPE '\\'
                GROUP BY username
                ORDER BY username
                """, KNOWN_LOGIN, pattern);
    }

    @Override
    public List<UserTileVisits> visitsOfUser(String login) {
        return jdbc.query("""
                SELECT object_ref, COUNT(*) AS visits, MAX(ts_utc) AS last_visit
                FROM audit_log
                WHERE username = ? AND action = ? AND object_ref LIKE 'tile:%'
                GROUP BY object_ref
                ORDER BY last_visit DESC
                """, (rs, i) -> new UserTileVisits(stripTile(rs.getString("object_ref")), rs.getLong("visits"),
                instant(rs, "last_visit")), login, APP_OPEN);
    }

    @Override
    public Optional<KnownLogin> presence(String login) {
        List<KnownLogin> rows = jdbc.query("""
                SELECT username, MIN(ts_utc) AS first_seen, MAX(ts_utc) AS last_seen, COUNT(*) AS requests
                FROM audit_log
                WHERE username = ?
                GROUP BY username
                """, KNOWN_LOGIN, login);
        return rows.stream().findFirst();
    }

    @Override
    public List<TileStat> tileStats(Instant from, Instant to) {
        return jdbc.query("""
                SELECT object_ref, COUNT(*) AS visits, COUNT(DISTINCT username) AS distinct_users
                FROM audit_log
                WHERE action = ? AND object_ref LIKE 'tile:%' AND ts_utc >= ? AND ts_utc < ?
                GROUP BY object_ref
                ORDER BY visits DESC
                """, (rs, i) -> new TileStat(stripTile(rs.getString("object_ref")), rs.getLong("visits"),
                rs.getLong("distinct_users")), APP_OPEN, utc(from), utc(to));
    }

    @Override
    public List<UserStat> userStats(Instant from, Instant to) {
        return jdbc.query("""
                SELECT username, COUNT(*) AS visits, COUNT(DISTINCT object_ref) AS distinct_tiles
                FROM audit_log
                WHERE action = ? AND object_ref LIKE 'tile:%' AND ts_utc >= ? AND ts_utc < ?
                GROUP BY username
                ORDER BY visits DESC, username
                """, (rs, i) -> new UserStat(rs.getString("username"), rs.getLong("visits"),
                rs.getLong("distinct_tiles")), APP_OPEN, utc(from), utc(to));
    }

    @Override
    public PortalStat portalStats(Instant from, Instant to) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS visits, COUNT(DISTINCT username) AS distinct_users
                FROM audit_log
                WHERE action = ? AND ts_utc >= ? AND ts_utc < ?
                """, (rs, i) -> new PortalStat(rs.getLong("visits"), rs.getLong("distinct_users")),
                TILES_LIST, utc(from), utc(to));
    }

    @Override
    public List<AuditRow> recentAudit(int limit, String login, String action, String tileCode) {
        int top = Math.max(1, Math.min(limit, 500));
        StringBuilder sql = new StringBuilder("SELECT TOP (" + top + ") id, ts_utc, username, client_ip, http_method,"
                + " path, action, object_ref, status, http_status FROM audit_log WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (login != null && !login.isBlank()) {
            sql.append(" AND username = ?");
            params.add(login.trim().toLowerCase(Locale.ROOT));
        }
        if (action != null && !action.isBlank()) {
            sql.append(" AND action = ?");
            params.add(action.trim());
        }
        if (tileCode != null && !tileCode.isBlank()) {
            sql.append(" AND object_ref = ?");
            params.add(TILE_PREFIX + tileCode.trim());
        }
        sql.append(" ORDER BY id DESC");
        return jdbc.query(sql.toString(), (rs, i) -> new AuditRow(rs.getLong("id"), instant(rs, "ts_utc"),
                rs.getString("username"), rs.getString("client_ip"), rs.getString("http_method"),
                rs.getString("path"), rs.getString("action"), rs.getString("object_ref"),
                rs.getString("status"), rs.getInt("http_status")), params.toArray());
    }

    // ----------------------------------------------------------------------

    private static final RowMapper<KnownLogin> KNOWN_LOGIN = (rs, i) -> new KnownLogin(
            rs.getString("username"), instant(rs, "first_seen"), instant(rs, "last_seen"), rs.getLong("requests"));

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static String stripTile(String objectRef) {
        return objectRef != null && objectRef.startsWith(TILE_PREFIX) ? objectRef.substring(TILE_PREFIX.length()) : objectRef;
    }

    /** Znaki specjalne LIKE w SQL Serverze: % _ [ — użytkownik szuka fragmentu loginu, nie wzorca. */
    static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
    }
}
