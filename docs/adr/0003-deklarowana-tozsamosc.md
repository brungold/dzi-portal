# ADR-0003: Deklarowana tożsamość (profil `declared`)

Status: zaakceptowany · Data: 2026-07-21 · Uzupełnia ADR-0001 (dec. 6 i 7)

## Kontekst

Mały moduł/wdrożenie wewnątrz jednostki **bez możliwości integracji z usługą
katalogową** (AD/Kerberos/LDAPS) — ograniczenie organizacyjne, nie techniczne.
Wariant A opiera całą tożsamość na Kerberosie; bez niego nagłówek `X-Auth-User`
traci oparcie w sekrecie. Decyzja użytkownika systemu: akceptujemy model
**deklaracji tożsamości** i budujemy wokół niego maksimum kompensacji.

## Decyzja

**1. Klient deklaruje WYŁĄCZNIE login (nagłówek `X-Auth-User`). Nic więcej.**
Departament, przynależność, uprawnienia — żadna z tych rzeczy nie podróżuje
w żądaniu. Serwer wyprowadza je sam: `login -> user_departments (SQL) ->
tile_permissions -> AccessFacade`. Model „klient przysyła departament, serwer
sprawdza, czy istnieje" został **odrzucony**: atrybuty katalogowe są jawne
(login = format e-maila, departamentów kilkanaście), więc ich „weryfikacja
istnienia" nie dowodzi niczego — dostęp miałby każdy, kto wpisze właściwy
string. NIE upraszczać tego z powrotem.

**2. Egzekwowanie i audyt istniejącym silnikiem — zero równoległej bramki.**
Deklaracja wpina się jako źródło tożsamości do niezmienionego łańcucha
(`@PreAuthorize` + `AccessFacade`, dwustronny RBAC, append-only `audit_log`
przez `AuditFilter`). Endpoint „verify" zwracający SUKCES do interpretacji
przez skrypt kliencki został **odrzucony**: decyzja wykonywana na maszynie
użytkownika nie jest kontrolą dostępu (klient może zignorować odmowę).
Skrypty wołają realne endpointy (`/api/tiles`, `/api/tiles/{code}/run`),
a treść/akcję wydaje serwer po własnej decyzji.

**3. Granica zaufania: loopback + jawna lista CIDR; podwójny przełącznik.**
Default bajt w bajt jak wariant A (tylko loopback). Otwarcie wymaga zmiany
`server.address` ORAZ `allowed-cidrs` — jedno bez drugiego nic nie otwiera.

**4. Zapobiec podszyciu się nie można — więc je wykrywamy.**
Sygnatura nadużycia w tym modelu: jeden adres deklarujący wiele loginów.
Limiter blokuje adres po przekroczeniu progu różnych loginów w oknie
(+ zwykły sufit żądań/min); 429 ląduje w audycie z deklarowanym loginem
i adresem. Nieznany login = pusty zbiór grup = 403 wszędzie (fail-closed,
bez grupy „wszyscy").

**5. Zakaz danych wrażliwych za kafelkami w tym trybie.**
Skoro tożsamość jest deklaracją, poziom ochrony treści = „każdy w podsieci,
kto zna czyjś login". Kafelki z danymi osobowymi/finansowymi/kadrowymi nie
wchodzą pod ten profil. Baner WARN przy starcie przypomina o tym na głos.

**6. Prowizja przynależności: eksport z AD przez administratora, nie runtime.**
`deploy/declared/export-user-departments.ps1` generuje INSERT-y (admin może
czytać katalog ze swojej stacji — ograniczenie dotyczy integracji runtime).
Dane referencyjne zarządzane jak `tile_permissions` (§7 briefu).

## Odrzucone / odłożone

| Opcja | Decyzja | Kiedy wraca (próg rewizji) |
|---|---|---|
| Hasła lokalne (argon2id) | odłożone | pierwszy kafelek z danymi wrażliwymi ALBO pierwszy incydent podszycia |
| mTLS (PKI jednostki, `usercertificate` w AD) | odłożone — najsilniejsza opcja bez AD-runtime | jak wyżej; sprawdzić dostępność wystawiania certyfikatów zanim padnie na hasła |
| Weryfikacja deklarowanego departamentu „czy istnieje" | **odrzucone na stałe** | nigdy — patrz decyzja 1 |
| Bramka verify + egzekwowanie w skrypcie klienta | **odrzucone na stałe** | nigdy — patrz decyzja 2 |
| Broker/Redis pod limiter | odrzucone | >1 instancja API (dziś stan w pamięci wystarcza) |

## Konsekwencje

- Wpisy w `audit_log` odpowiadają na pytanie „co zadeklarowano i skąd",
  a nie „kto naprawdę siedział przy klawiaturze" — wartość dowodowa niższa
  niż w wariancie A; przy incydencie korelować z adresem i logami stacji.
- Restart procesu zeruje liczniki limitera (świadome, jak cache grup).
- Utrzymanie `user_departments` to nowy obowiązek (odświeżanie po zmianach
  kadrowych) — koszt zapisany, analogiczny do luster seedu demo.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
