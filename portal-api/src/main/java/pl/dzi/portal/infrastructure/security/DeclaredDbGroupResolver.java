/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.security;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Profil {@code declared}: "grupami" użytkownika są jego DEPARTAMENTY z tabeli
 * user_departments — jedynego źródła prawdy o przynależności (klient jej nie deklaruje).
 * Reszta łańcucha (AccessFacade, tile_permissions, @PreAuthorize) działa bez zmian:
 * w kolumnie tile_permissions.ad_group zamiast grup AD stoją nazwy departamentów.
 *
 * Semantyka fail-closed:
 *  - login nieobecny w tabeli => PUSTY zbiór => 403 na wszystkim, DENIED w audycie;
 *    nieznana deklaracja nie dostaje nawet kafelków "dla wszystkich",
 *  - login znany => departamenty + syntetyczna grupa "wszyscy" (odpowiednik wildcardu
 *    "*": kafelek publiczny to jeden wiersz tile_permissions z ad_group='wszyscy').
 *
 * Konwencja: loginy i departamenty w tabeli małymi literami (pilnuje jej skrypt
 * prowizji i CHECK w migracji); porównanie w AccessFacade i tak jest case-insensitive.
 */
final class DeclaredDbGroupResolver implements AdGroupResolver {

    static final String GROUP_EVERYONE = "wszyscy";

    private static final String SELECT_DEPARTMENTS = """
            SELECT department FROM user_departments WHERE login = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    DeclaredDbGroupResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Set<String> resolveGroups(String samAccountName) {
        String login = samAccountName.toLowerCase(Locale.ROOT);
        List<String> departments = jdbcTemplate.queryForList(SELECT_DEPARTMENTS, String.class, login);
        if (departments.isEmpty()) {
            return Set.of();
        }
        Set<String> groups = new HashSet<>(departments);
        groups.add(GROUP_EVERYONE);
        return Set.copyOf(groups);
    }
}
