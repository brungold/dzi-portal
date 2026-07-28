/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.infrastructure.audit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

/**
 * Przepisuje @Audited(action=...) z metody kontrolera do atrybutu requestu,
 * skąd zbiera go AuditFilter przy zapisie wpisu. Rejestracja: WebMvcConfiguration.
 */
public final class AuditedActionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            Audited audited = handlerMethod.getMethodAnnotation(Audited.class);
            if (audited != null) {
                request.setAttribute(PortalRequestAttributes.ACTION, audited.action());
            }
        }
        return true;
    }
}
