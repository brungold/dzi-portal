# ADR-0001: Wybory technologiczne fundamentu (Etap 0)

Status: zaakceptowany · Data: 2026-07-07 · Dotyczy: portal-api, portal-worker, portal-common

## Kontekst

Portal wewnętrzny na Windows Server 2025 (DZI-APP01V), za IIS (Kerberos, wariant A: nagłówek
`X-Auth-User` na loopbacku). Dwa procesy (api + worker) na jednym schemacie SQL Server.
Zespół utrzymaniowy: 2 osoby. Priorytety: audytowalność, mało magii, kod czytelny za 3 lata.

## Decyzje

**1. Spring Boot 4.1.x, Java 21 (Temurin).**
Linia 3.5 straciła wsparcie OSS 30.06.2026 — nowy projekt nie startuje na gałęzi bez poprawek
bezpieczeństwa. Konsekwencja: nazwy starterów po modularyzacji (`spring-boot-starter-webmvc`,
jawny `spring-boot-starter-flyway`, startery testowe per technologia).

**2. Spring Data JDBC zamiast JPA/Hibernate.**
Zapis dzieje się wyłącznie przy `save()` — zero dirty-checkingu i lazy-loadingu, czyli dwóch
głównych źródeł "niespodzianek" ORM. Tam, gdzie potrzebny T-SQL (claim kolejki `UPDATE TOP(1)
... OUTPUT`, insert audytu), schodzimy do jawnego SQL — z JPA i tak byśmy musieli.

**3. Kolejka zadań = tabela SQL + polling. Bez brokera.**
Jeden serwer, dziesiątki zadań dziennie, wymóg pełnej audytowalności — tabela `tasks` jest
jednocześnie kolejką i rejestrem. Broker (RabbitMQ itp.) to kolejna usługa do utrzymania bez
zysku przy tej skali. Semantyka: at-least-once → skrypty muszą być idempotentne.
Próg rewizji: >1 worker-host albo wymóg latencji < 1 s.

**4. Flyway wyłącznie w portal-api.**
Jeden właściciel schematu = zero wyścigów migracji. Worker przy starcie robi tani sanity-check
(`flyway_schema_history`) i odmawia startu na pustej bazie.

**5. Własny cache TTL grup AD zamiast Caffeine.**
Rozmiar naturalnie ograniczony liczbą pracowników; leniwa ewaluacja wystarcza.
Próg wymiany: potrzeba metryk trafień, aktywnej ewikcji albo limitu pamięci.

**6. Wariant A (nagłówek z IIS) zamiast SPNEGO/Waffle w aplikacji.**
> Uzupełnione przez **ADR-0003**: tam, gdzie Kerberos jest organizacyjnie niedostępny,
> ten sam nagłówek niesie deklarację loginu (profil `declared`) — z innym modelem
> zaufania i innymi kompensacjami. Wariant A pozostaje docelowy.

IIS robi Kerberos natywnie i za darmo; aplikacja pozostaje czystą Javą testowalną MockMvc
(nagłówek da się podrobić w teście — biletu Kerberos nie). Granica zaufania: trzy warstwy
(bind 127.0.0.1, filtr honoruje tylko loopback, IIS nadpisuje nagłówek bezwarunkowo).

**7. Jedyny sekret systemu: konto read-only do LDAP.**
> Uzupełnione przez **ADR-0003**: w profilu `declared` nie ma LDAP-a w runtime,
> więc system nie ma ŻADNEGO sekretu; przynależność pochodzi z tabeli
> `user_departments`, prowizjonowanej eksportem z AD wykonywanym przez administratora.

SQL przez integrated security (gMSA), tożsamość użytkownika z Kerberosa — hasło ma wyłącznie
`svc-portal-ldap` (odczyt katalogu), podawane przez zmienne środowiskowe w definicji usługi
(WinSW), nigdy w repo. Czysta Java nie umie bindować do LDAP ambientową tożsamością Windows
bez natywnych bibliotek — świadomie płacimy jednym sekretem zamiast zależnością od JNA/Waffle.

**8. Awaria zapisu audytu NIE blokuje odpowiedzi użytkownika.**
`log.error` + wpis ginie — przy 2-osobowym utrzymaniu fail-closed oznaczałby, że padnięta baza
audytu kładzie cały portal. Do rewizji przy hardeningu (Etap 6), gdy będzie alerting.

## Zależności odrzucone / odłożone

| Zależność | Decyzja | Kiedy wraca |
|---|---|---|
| MapStruct | odrzucone — rekordy + ręczne mapowanie wystarczają | raczej nigdy |
| springdoc-openapi | odłożone | Etap 3, gdy API urośnie ponad kilka endpointów |
| spring-boot-starter-validation | odłożone | pierwszy DTO z walidacją (Etap 3/4) |
| Testcontainers (MSSQL) | odłożone | testy repozytoriów i migracji (Etap 2+) |
| WebSocket/STOMP | odłożone — najpierw ETag polling | Etap 6, jeśli polling nie wystarczy |
| Caffeine | odrzucone na teraz | próg z decyzji 5 |

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
