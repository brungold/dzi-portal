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

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeclaredDbGroupResolverTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DeclaredDbGroupResolver resolver = new DeclaredDbGroupResolver(jdbcTemplate);

    @Test
    void should_resolve_departments_and_add_everyone_group_for_known_login() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("jkowalski")))
                .thenReturn(List.of("dzi", "finanse"));

        Set<String> groups = resolver.resolveGroups("jkowalski");

        assertThat(groups).containsExactlyInAnyOrder("dzi", "finanse", DeclaredDbGroupResolver.GROUP_EVERYONE);
    }

    @Test
    void should_lowercase_login_before_lookup() {
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("jkowalski")))
                .thenReturn(List.of("dzi"));

        Set<String> groups = resolver.resolveGroups("JKowalski");

        assertThat(groups).contains("dzi");
    }

    @Test
    void should_fail_closed_with_empty_set_for_unknown_login() {
        // nieznana deklaracja nie dostaje NICZEGO — także grupy "wszyscy";
        // pusty zbiór => AccessFacade odmawia wszystkiego => 403 + DENIED w audycie
        when(jdbcTemplate.queryForList(anyString(), eq(String.class), eq("obcy")))
                .thenReturn(List.of());

        Set<String> groups = resolver.resolveGroups("obcy");

        assertThat(groups).isEmpty();
    }
}
