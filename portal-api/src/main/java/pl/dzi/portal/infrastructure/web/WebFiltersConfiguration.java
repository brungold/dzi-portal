/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import pl.dzi.portal.common.audit.AuditWriter;
import pl.dzi.portal.infrastructure.audit.AuditFilter;

/**
 * Jawna rejestracja filtrów serwletowych z kolejnością względem łańcucha
 * Spring Security (order -100):
 *   -120  CorrelationIdFilter  -> identyfikator w MDC od pierwszej linii logu,
 *   -110  AuditFilter          -> OPLATA security: pełny czas, widzi 401/403,
 *   -105  SameOriginRequestFilter -> tania ochrona CSRF przed wejściem w security,
 *   -100  springSecurityFilterChain (w środku LoopbackHeaderAuthenticationFilter).
 */
@Configuration
class WebFiltersConfiguration {

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setOrder(-120);
        registration.addUrlPatterns("/api/*");
        return registration;
    }

    @Bean
    FilterRegistrationBean<AuditFilter> auditFilterRegistration(AuditWriter auditWriter) {
        var registration = new FilterRegistrationBean<>(new AuditFilter(auditWriter));
        registration.setOrder(-110);
        registration.addUrlPatterns("/api/*");
        return registration;
    }

    @Bean
    FilterRegistrationBean<SameOriginRequestFilter> sameOriginRequestFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new SameOriginRequestFilter());
        registration.setOrder(-105);
        registration.addUrlPatterns("/api/*");
        return registration;
    }
}
