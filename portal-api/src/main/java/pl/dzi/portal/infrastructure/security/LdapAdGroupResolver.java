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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.support.LdapEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.springframework.ldap.query.LdapQueryBuilder.query;

/**
 * Grupy AD w dwóch krokach:
 *  1) DN użytkownika po sAMAccountName,
 *  2) jedno zapytanie o grupy z regułą LDAP_MATCHING_RULE_IN_CHAIN (OID 1.2.840.113556.1.4.1941),
 *     która po stronie kontrolera domeny rozwiązuje zagnieżdżenia (grupy w grupach).
 * Wartości wstawiane do filtra są enkodowane (LdapEncoder) — DN i login nie mogą wstrzyknąć składni filtra.
 */
@Slf4j
@RequiredArgsConstructor
final class LdapAdGroupResolver implements AdGroupResolver {

    private static final String MATCHING_RULE_IN_CHAIN = "1.2.840.113556.1.4.1941";

    private final LdapTemplate ldapTemplate;
    private final PortalLdapProperties properties;

    @Override
    public Set<String> resolveGroups(String samAccountName) {
        Optional<String> userDn = findUserDn(samAccountName);
        if (userDn.isEmpty()) {
            log.warn("Nie znaleziono użytkownika '{}' w AD — zwracam pusty zbiór grup", samAccountName);
            return Set.of();
        }
        return findPortalGroups(userDn.get());
    }

    private Optional<String> findUserDn(String samAccountName) {
        List<String> dns = ldapTemplate.search(
                query().where("objectClass").is("user").and("sAMAccountName").is(samAccountName),
                (ContextMapper<String>) ctx -> ((DirContextOperations) ctx).getNameInNamespace());
        if (dns.size() > 1) {
            log.warn("sAMAccountName '{}' zwrócił {} obiektów — używam pierwszego", samAccountName, dns.size());
        }
        return dns.stream().findFirst();
    }

    private Set<String> findPortalGroups(String userDn) {
        String filter = "(&(objectClass=group)"
                + "(sAMAccountName=" + LdapEncoder.filterEncode(properties.groupPrefix()) + "*)"
                + "(member:" + MATCHING_RULE_IN_CHAIN + ":=" + LdapEncoder.filterEncode(userDn) + "))";
        List<String> names = ldapTemplate.search("", filter,
                (AttributesMapper<String>) attributes -> (String) attributes.get("sAMAccountName").get());
        return Set.copyOf(names);
    }
}
