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

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.dzi.portal.infrastructure.audit.Audited;
import pl.dzi.portal.infrastructure.web.ClientIpResolver;
import pl.dzi.portal.infrastructure.web.PortalRequestAttributes;

import java.util.Set;

/**
 * Endpoint diagnostyczny stalowej nitki (Etap 1) — zostaje na stałe.
 * Odpowiada na pytanie "za kogo ma mnie portal i jakie widzi grupy",
 * czyli 90% debugowania Kerberos/IIS/LDAP jednym GET-em.
 * Od Etapu 2 jest też pierwszym konsumentem @Audited — w audit_log widać action=WHOAMI.
 */
@RestController
class WhoAmIController {

    @Audited(action = "WHOAMI")
    @GetMapping("/api/whoami")
    WhoAmIResponse whoAmI(Authentication authentication, HttpServletRequest request) {
        PortalUser user = (PortalUser) authentication.getPrincipal();
        return new WhoAmIResponse(
                user.login(),
                user.groups(),
                ClientIpResolver.resolve(request),
                (String) request.getAttribute(PortalRequestAttributes.CORRELATION_ID));
    }

    record WhoAmIResponse(String login, Set<String> groups, String clientIp, String correlationId) {
    }
}
