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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * Ustawienia profilu {@code declared} (deklarowana tożsamość — ADR-0003,
 * model przynależności: ADR-0005 — deklarowany departament).
 *
 * Bezpieczny default: {@code allowedCidrs} PUSTE, czyli deklaracje przyjmowane
 * wyłącznie z loopbacku (zachowanie identyczne jak wariant A). Otwarcie na sieć
 * wymaga DWÓCH świadomych ruchów: ustawienia {@code server.address} ORAZ wpisania
 * CIDR-ów tutaj — jeden przełącznik nie wystarczy.
 *
 * @param allowedCidrs          zakresy (CIDR albo pojedyncze IP), z których wolno
 *                              przyjąć deklarację tożsamości, np. {@code 10.20.0.0/16}
 * @param maxRequestsPerMinute  prosty sufit żądań /api z jednego adresu — tłumi
 *                              zapętlone skrypty; 120 = 2/s, poniżej progu odczuwalności
 *                              dla przeglądarki (polling ETag + status zadania)
 * @param anomalyDistinctLogins ile RÓŻNYCH loginów z jednego adresu w oknie uznajemy
 *                              jeszcze za normalne; przekroczenie = sygnatura podszywania
 * @param anomalyWindow         okno obserwacji dla powyższego licznika
 * @param blockDuration         czas blokady adresu po wykryciu anomalii
 * @param deptHeader            nazwa nagłówka z DEKLAROWANYM departamentem (ADR-0005);
 *                              wartość = skrót z AD extensionattribute12 (dzi/dag/dpb…),
 *                              normalizowana do małych liter po stronie serwera
 */
@ConfigurationProperties(prefix = "portal.security.declared")
record DeclaredIdentityProperties(
        @DefaultValue List<String> allowedCidrs,
        @DefaultValue("120") int maxRequestsPerMinute,
        @DefaultValue("3") int anomalyDistinctLogins,
        @DefaultValue("10m") Duration anomalyWindow,
        @DefaultValue("15m") Duration blockDuration,
        @DefaultValue("X-Auth-Dept") String deptHeader) {
}
