/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.tiles;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import pl.dzi.portal.infrastructure.security.PortalUser;

import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * Jedyne miejsce, w którym pada odpowiedź "czy TEN użytkownik może TO zrobić z TYM kafelkiem".
 *
 * Podwójne egzekwowanie RBAC:
 *  1) miękko: TilesFacade filtruje listę — użytkownik nie widzi kafelków bez uprawnień (UX),
 *  2) twardo: każdy endpoint akcji przechodzi przez tę fasadę w @PreAuthorize —
 *     ręcznie sklejony request na znany kod kafelka kończy się 403 + wpisem DENIED w audycie.
 *
 * Świadomie: nieznany kod kafelka => false => 403 (a nie 404) — brak uprawnień
 * nie zdradza, czy zasób w ogóle istnieje.
 *
 * Authentication przychodzi jawnie parametrem (w SpEL: zmienna `authentication`),
 * zamiast być wyciągane z SecurityContextHolder w środku — zależność widać w sygnaturze.
 */
@RequiredArgsConstructor
public class AccessFacade {

    private final TilePermissionRepository tilePermissions;

    public boolean canRead(String tileCode, Authentication authentication) {
        return hasLevel(tileCode, authentication, PermissionLevel.READ);
    }

    public boolean canExecute(String tileCode, Authentication authentication) {
        return hasLevel(tileCode, authentication, PermissionLevel.EXECUTE);
    }

    public boolean canEdit(String tileCode, Authentication authentication) {
        return hasLevel(tileCode, authentication, PermissionLevel.EDIT);
    }

    private boolean hasLevel(String tileCode, Authentication authentication, PermissionLevel required) {
        Set<String> groups = groupsOf(authentication);
        if (groups.isEmpty()) {
            return false; // także dlatego, że SQL "IN ()" nie istnieje — patrz TilePermissionRepository
        }
        return tilePermissions.findForTileAndGroups(tileCode, lowercased(groups)).stream()
                .anyMatch(permission -> permission.permissionLevel().covers(required));
    }

    /** Grupy AD porównujemy case-insensitive — tak jak robi to samo AD. */
    static Collection<String> lowercased(Collection<String> groups) {
        return groups.stream().map(group -> group.toLowerCase(Locale.ROOT)).toList();
    }

    static Set<String> groupsOf(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof PortalUser user) {
            return user.groups();
        }
        return Set.of();
    }
}
