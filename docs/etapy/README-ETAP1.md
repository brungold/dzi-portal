# Etap 1 — paczka: stalowa nitka security na serwerze

## Co jest w paczce

**Nowe pliki** (rozpakuj na katalog repo, struktura się pokrywa):

| Plik | Rola |
|---|---|
| `docs/etap1-runbook.md` | Kompletny runbook: Faza A (konsola) → Faza B (usługa), kryteria akceptacji, tabela debugowania |
| `deploy/iis/setup-iis.ps1` | Idempotentna konfiguracja IIS: ARR proxy, allowed server variables, witryna 443, Windows Auth (Negotiate przed NTLM) |
| `deploy/iis/verify-etap1.ps1` | Weryfikacja każdego ogniwa łańcucha + **test anty-spoof** nagłówka |
| `deploy/deploy-api.ps1` | Minimalny redeploy jara (stop → backup → podmiana → start → health) |
| `deploy/winsw/portal-api.xml.template` | Definicja usługi (gMSA, env z sekretem LDAP, Windows-ROOT, logi) |
| `deploy/winsw/README-WinSW.md` | Instalacja WinSW, ACL na pliku z sekretem, DLL integrated security |

**Jeden plik ZMIENIONY względem Etapu 0** (nadpisz):

- `portal-api/src/main/java/pl/dzi/portal/infrastructure/security/LdapConfiguration.java`
  — dochodzą twarde timeouty JNDI (connect 5 s, read 10 s). Bez nich niedostępny kontroler
  domeny wiesza wątek żądania na kilkadziesiąt sekund przy pierwszym trafieniu spoza cache'u.

## Plan commitów (kontynuacja numeracji z Etapu 0)

| # | Commit | Zakres |
|---|--------|--------|
| 7 | `feat(security): timeouty połączeń LDAP` | zmieniony `LdapConfiguration.java` |
| 8 | `feat(deploy): IIS, WinSW i runbook Etapu 1` | `deploy/iis/*.ps1`, `deploy/winsw/*`, `deploy/deploy-api.ps1`, `docs/etap1-runbook.md` |

## Opcjonalny dodatek (commit 9, polecam)

W `portal-api/pom.xml` do pluginu Boota dopisz generowanie build-info — przy częstych
redeployach `GET /actuator/info` mówi, **która wersja jara naprawdę działa**:

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals>
                <goal>build-info</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Kolejność działań

1. Commity 7–8 (albo po prostu nadpisz pliki lokalnie).
2. `mvn -pl portal-api -am clean package` na stacji.
3. Dalej ściśle według `docs/etap1-runbook.md` — Faza A, potem Faza B.

## Uczciwe zastrzeżenie

Skrypty PowerShell pisane były "na sucho" (bez serwera pod ręką). Są defensywne
i idempotentne, ale IIS-owe cmdlety potrafią różnić się zachowaniem między wersjami —
dlatego pierwszym krokiem po setup-iis.ps1 jest zawsze `verify-etap1.ps1`.
Jeśli coś świeci na czerwono, wklej pełny output — poprawimy punktowo.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
