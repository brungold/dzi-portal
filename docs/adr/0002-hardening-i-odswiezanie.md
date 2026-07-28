# ADR-0002: Odświeżanie i hardening (Etap 6)

Status: zaakceptowany · Data: 2026-07-07 · Uzupełnia ADR-0001

**1. Odświeżanie danych: polling z ETag, nie STOMP/WebSocket.**
Token stanu = `COUNT(*):SUM(version):MAX(updated_at)` (jedna agregacja po indeksie, wyliczany —
zero stanu do psucia). Klient pyta co 10 s z If-None-Match; brak zmian = 304 bez treści.
Przy realnej skali portalu (kilkudziesięciu użytkowników, dane zmieniane kilka razy dziennie)
to koszt pomijalny, a architektura zostaje request-response: łatwa do debugowania (curl),
przeżywa restarty IIS/ARR bez specjalnej konfiguracji WebSocketów.
**Próg wejścia STOMP:** wymóg latencji <2 s, ALBO >50 równoczesnych widzów jednego zbioru,
ALBO push zdarzeń zadań (dziś polling statusu wystarcza).

**2. Odpowiedzi 304 poza rejestrem audytu.**
304 nie niesie danych i nie jest zdarzeniem — audytowanie „nic się nie zmieniło co 10 s"
zalałoby rejestr szumem (setki wpisów na godzinę na otwartą kartę) i UKRYŁO zdarzenia
istotne. Audytowane pozostają wszystkie odczyty z treścią (200) i wszystkie odmowy.

**3. Retencja rejestrów poza tożsamościami runtime.**
DENY UPDATE/DELETE dla gMSA na `audit_log` zostaje nienaruszone — usuwanie starych wpisów
wykonuje odrębne konto utrzymaniowe z jawnym GRANT DELETE, przez Task Scheduler
(SQL Express nie ma Agenta). Partiami po 5000 wierszy. Okresy: audit 400 dni,
task_log 90 dni (wiersze `tasks` zostają — to rejestr biznesowy zleceń).

**4. db_ddladmin zostaje przy gMSA-PortalApi.**
Rozdzielenie tożsamości migracyjnej od runtime wymagałoby trybu „migrate-only" w aplikacji
albo kroku Flyway CLI w deployu — koszt niewspółmierny przy 2-osobowym utrzymaniu.
Ryzyko realne: ddladmin może ALTER/DROP tabel (głośne, widoczne), ale NIE może zmieniać
uprawnień — DENY na audit_log trzyma. **Próg rewizji:** wymóg formalny compliance
albo pojawienie się dedykowanego pipeline'u wdrożeniowego.

**5. NTFS: katalog skryptów tylko-do-odczytu dla workera.**
Kluczowa granica: gdyby gMSA-PortalWorker mógł pisać do `D:\portal\scripts`, każda
podatność pozwalająca zapisać plik zamieniałaby whitelistę w „wykonaj cokolwiek".
Zapis mają wyłącznie Administratorzy; egzekwuje `verify-hardening.ps1` (twardy FAIL).

**6. Graceful shutdown API (20 s) < stoptimeout WinSW (30 s).**
Deploy nie ucina żądań w locie; przerwane zadanie workera kończy się FAILED,
ewentualne sieroty domyka OrphanSweeper — świadomie akceptujemy at-least-once.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
