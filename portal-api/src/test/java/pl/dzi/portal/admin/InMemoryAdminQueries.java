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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Atrapa modelu odczytu dla testów plasterkowych i jednostkowych: kafelki, uprawnienia
 * i „wejścia" trzymane w listach; agregaty liczone strumieniami — tak, jak liczy je SQL.
 */
final class InMemoryAdminQueries implements AdminQueries {

    /** Jeden wpis audytu w wersji minimalnej: kto, kiedy, jaka akcja, jaki obiekt. */
    record Entry(long id, Instant ts, String username, String action, String objectRef, int httpStatus) {
    }

    final List<TileRow> tiles = new ArrayList<>();
    final List<PermissionRow> permissions = new ArrayList<>();
    final List<Entry> audit = new ArrayList<>();

    static InMemoryAdminQueries sample() {
        InMemoryAdminQueries q = new InMemoryAdminQueries();
        q.tiles.add(new TileRow(1, "red-pisma-sprawy", "ReD Dokumenty i Sprawy", "LINK", true, 10));
        q.tiles.add(new TileRow(2, "epo-podpis", "EPO — podpis", "LINK", true, 30));
        q.tiles.add(new TileRow(3, "wylaczony", "Nieaktywny", "LINK", false, 40));
        q.tiles.add(new TileRow(9, "administracja", "Administracja portalu", "LINK", true, 900));
        q.permissions.add(new PermissionRow("red-pisma-sprawy", "jan.kowalski", "READ"));
        q.permissions.add(new PermissionRow("red-pisma-sprawy", "kowalska.anna2", "READ"));
        q.permissions.add(new PermissionRow("red-pisma-sprawy", "literowka.login", "READ"));
        q.permissions.add(new PermissionRow("epo-podpis", "dzi", "READ"));
        q.permissions.add(new PermissionRow("epo-podpis", "wszyscy", "READ"));
        q.permissions.add(new PermissionRow("administracja", "admin.portal", "READ"));   // login (z kropką), nie departament
        q.audit.add(new Entry(1, Instant.parse("2026-09-01T08:00:00Z"), "jan.kowalski", "TILES_LIST", null, 200));
        q.audit.add(new Entry(2, Instant.parse("2026-09-01T08:00:05Z"), "jan.kowalski", "APP_OPEN", "tile:red-pisma-sprawy", 200));
        q.audit.add(new Entry(3, Instant.parse("2026-09-05T09:00:00Z"), "kowalska.anna2", "APP_OPEN", "tile:red-pisma-sprawy", 200));
        q.audit.add(new Entry(4, Instant.parse("2026-09-08T10:00:00Z"), "jan.kowalski", "APP_OPEN", "tile:red-pisma-sprawy", 200));
        q.audit.add(new Entry(5, Instant.parse("2026-09-08T10:05:00Z"), "jan.kowalski", "APP_DENIED", "tile:epo-podpis", 403));
        q.audit.add(new Entry(6, Instant.parse("2026-06-01T10:00:00Z"), "stary.login", "APP_OPEN", "tile:epo-podpis", 200));
        return q;
    }

    @Override
    public List<TileRow> tiles() {
        return tiles.stream().sorted(Comparator.comparingInt(TileRow::displayOrder)).toList();
    }

    @Override
    public List<PermissionRow> permissions() {
        return List.copyOf(permissions);
    }

    @Override
    public Map<String, TileVisits> visitsPerTile(Instant since30, Instant since90) {
        Map<String, TileVisits> result = new LinkedHashMap<>();
        opens().collect(Collectors.groupingBy(Entry::objectRef)).forEach((ref, entries) -> result.put(strip(ref),
                new TileVisits(
                        entries.stream().filter(e -> !e.ts().isBefore(since30)).count(),
                        entries.stream().filter(e -> !e.ts().isBefore(since90)).count(),
                        entries.stream().map(Entry::ts).max(Comparator.naturalOrder()).orElse(null))));
        return result;
    }

    @Override
    public List<Visitor> visitorsOfTile(String tileCode) {
        return opens().filter(e -> e.objectRef().equals("tile:" + tileCode))
                .collect(Collectors.groupingBy(Entry::username)).entrySet().stream()
                .map(entry -> new Visitor(entry.getKey(), entry.getValue().size(),
                        entry.getValue().stream().map(Entry::ts).max(Comparator.naturalOrder()).orElse(null)))
                .sorted(Comparator.comparing(Visitor::lastVisit).reversed())
                .toList();
    }

    @Override
    public List<KnownLogin> knownLogins(String filter) {
        String needle = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        return audit.stream().filter(e -> e.username().contains(needle))
                .collect(Collectors.groupingBy(Entry::username)).entrySet().stream()
                .map(entry -> known(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(KnownLogin::login))
                .toList();
    }

    @Override
    public List<UserTileVisits> visitsOfUser(String login) {
        return opens().filter(e -> e.username().equals(login))
                .collect(Collectors.groupingBy(Entry::objectRef)).entrySet().stream()
                .map(entry -> new UserTileVisits(strip(entry.getKey()), entry.getValue().size(),
                        entry.getValue().stream().map(Entry::ts).max(Comparator.naturalOrder()).orElse(null)))
                .toList();
    }

    @Override
    public Optional<KnownLogin> presence(String login) {
        List<Entry> entries = audit.stream().filter(e -> e.username().equals(login)).toList();
        return entries.isEmpty() ? Optional.empty() : Optional.of(known(login, entries));
    }

    @Override
    public List<TileStat> tileStats(Instant from, Instant to) {
        return opens().filter(e -> inRange(e, from, to))
                .collect(Collectors.groupingBy(Entry::objectRef)).entrySet().stream()
                .map(entry -> new TileStat(strip(entry.getKey()), entry.getValue().size(),
                        entry.getValue().stream().map(Entry::username).distinct().count()))
                .sorted(Comparator.comparingLong(TileStat::visits).reversed())
                .toList();
    }

    @Override
    public List<UserStat> userStats(Instant from, Instant to) {
        return opens().filter(e -> inRange(e, from, to))
                .collect(Collectors.groupingBy(Entry::username)).entrySet().stream()
                .map(entry -> new UserStat(entry.getKey(), entry.getValue().size(),
                        entry.getValue().stream().map(Entry::objectRef).distinct().count()))
                .sorted(Comparator.comparingLong(UserStat::visits).reversed())
                .toList();
    }

    @Override
    public PortalStat portalStats(Instant from, Instant to) {
        List<Entry> entries = audit.stream()
                .filter(e -> "TILES_LIST".equals(e.action()) && inRange(e, from, to))
                .toList();
        return new PortalStat(entries.size(), entries.stream().map(Entry::username).distinct().count());
    }

    @Override
    public List<AuditRow> recentAudit(int limit, String login, String action, String tileCode) {
        return audit.stream()
                .filter(e -> login == null || e.username().equals(login))
                .filter(e -> action == null || action.equals(e.action()))
                .filter(e -> tileCode == null || ("tile:" + tileCode).equals(e.objectRef()))
                .sorted(Comparator.comparingLong(Entry::id).reversed())
                .limit(limit)
                .map(e -> new AuditRow(e.id(), e.ts(), e.username(), "10.0.0.1", "GET", "/x", e.action(),
                        e.objectRef(), e.httpStatus() >= 400 ? "DENIED" : "SUCCESS", e.httpStatus()))
                .toList();
    }

    private java.util.stream.Stream<Entry> opens() {
        return audit.stream().filter(e -> "APP_OPEN".equals(e.action()) && e.objectRef() != null);
    }

    private static boolean inRange(Entry e, Instant from, Instant to) {
        return !e.ts().isBefore(from) && e.ts().isBefore(to);
    }

    private static KnownLogin known(String login, List<Entry> entries) {
        return new KnownLogin(login,
                entries.stream().map(Entry::ts).min(Comparator.naturalOrder()).orElse(null),
                entries.stream().map(Entry::ts).max(Comparator.naturalOrder()).orElse(null),
                entries.size());
    }

    private static String strip(String ref) {
        return ref.startsWith("tile:") ? ref.substring(5) : ref;
    }
}
