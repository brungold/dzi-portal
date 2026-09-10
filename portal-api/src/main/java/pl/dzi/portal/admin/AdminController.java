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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.dzi.portal.admin.AdminViews.AuditView;
import pl.dzi.portal.admin.AdminViews.LoginView;
import pl.dzi.portal.admin.AdminViews.StatsView;
import pl.dzi.portal.admin.AdminViews.TileDetail;
import pl.dzi.portal.admin.AdminViews.TileOverview;
import pl.dzi.portal.admin.AdminViews.UserView;
import pl.dzi.portal.infrastructure.audit.AuditContext;
import pl.dzi.portal.infrastructure.audit.Audited;

import java.time.LocalDate;
import java.util.List;

/**
 * /api/admin/** — panel administracyjny, Faza A: same GET-y (ADR-0009).
 *
 * Bramka jest ta sama, co dla każdego kafelka: READ na kafelku 'administracja'
 * w tile_permissions, egzekwowane @PreAuthorize przez bean 'access' (AccessFacade).
 * Nowy administrator = INSERT do tile_permissions w SQL — panel nie ma i nie będzie
 * miał endpointu, który nadaje dostęp do samego siebie. Wyłączenie kafelka
 * (active = 0) wyłącza też to API — powrót przez SQL (runbook w ADR-0009).
 *
 * Każde wywołanie trafia do audytu jak każde /api (kto oglądał panel — też jest zapisane),
 * z akcją ADMIN_VIEW i object_ref wskazującym oglądany obiekt.
 */
@RestController
@RequiredArgsConstructor
public class AdminController {

    private static final String REQUIRES_ADMIN = "@access.canRead('" + AdminFacade.ADMIN_TILE + "', authentication)";

    private final AdminFacade facade;
    private final AuditContext auditContext;

    @Audited(action = "ADMIN_VIEW")
    @PreAuthorize(REQUIRES_ADMIN)
    @GetMapping("/api/admin/tiles")
    public List<TileOverview> tiles() {
        auditContext.setObjectRef("admin:tiles");
        return facade.tilesOverview();
    }

    @Audited(action = "ADMIN_VIEW")
    @PreAuthorize(REQUIRES_ADMIN)
    @GetMapping("/api/admin/tiles/{code}")
    public TileDetail tile(@PathVariable("code") String code) {
        auditContext.setObjectRef("tile:" + code);
        return facade.tileDetail(code);
    }

    @Audited(action = "ADMIN_VIEW")
    @PreAuthorize(REQUIRES_ADMIN)
    @GetMapping("/api/admin/users/{login}")
    public UserView user(@PathVariable("login") String login) {
        auditContext.setObjectRef("user:" + login);
        return facade.userView(login);
    }

    @Audited(action = "ADMIN_VIEW")
    @PreAuthorize(REQUIRES_ADMIN)
    @GetMapping("/api/admin/logins")
    public List<LoginView> logins(@RequestParam(name = "q", required = false) String filter) {
        auditContext.setObjectRef("admin:logins");
        return facade.logins(filter);
    }

    @Audited(action = "ADMIN_VIEW")
    @PreAuthorize(REQUIRES_ADMIN)
    @GetMapping("/api/admin/stats")
    public StatsView stats(@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        auditContext.setObjectRef("admin:stats");
        return facade.stats(from, to);
    }

    @Audited(action = "ADMIN_VIEW")
    @PreAuthorize(REQUIRES_ADMIN)
    @GetMapping("/api/admin/audit")
    public List<AuditView> audit(@RequestParam(name = "limit", required = false) Integer limit,
                                 @RequestParam(name = "login", required = false) String login,
                                 @RequestParam(name = "action", required = false) String action,
                                 @RequestParam(name = "tile", required = false) String tile) {
        auditContext.setObjectRef("admin:audit");
        return facade.recentAudit(limit, login, action, tile);
    }
}
