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

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Whitelist skryptów: jedyne, co worker ma prawo uruchomić. API nigdy nie przyjmuje
 * ścieżki ani komendy z żądania — wyłącznie code + zwalidowane parametry.
 */
@Table("scripts")
public record Script(
        @Id Long id,
        String code,
        String path,
        ScriptType scriptType,
        String paramsSchema,
        int timeoutSeconds,
        boolean active) {
}
