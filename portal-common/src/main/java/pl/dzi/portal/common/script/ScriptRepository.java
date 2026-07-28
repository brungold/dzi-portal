/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.common.script;

import org.springframework.data.repository.Repository;

import java.util.Optional;

/**
 * Od Etapu 4: baza Repository (jak TaskRepository) — worker czyta whitelist,
 * nigdy jej nie modyfikuje (skrypty zakłada administrator SQL-em).
 */
public interface ScriptRepository extends Repository<Script, Long> {

    Optional<Script> findById(Long id);

    Optional<Script> findByCode(String code);
}
