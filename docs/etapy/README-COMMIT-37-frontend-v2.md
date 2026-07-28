# Commit 37 — strona główna v2 + POPRAWKA: statyka w podkatalogach dawała 403

`fix(security): wzorce statyki obejmują /css/**, /js/**, /apps/**` +
`feat(frontend): hero z logotypem, kolor kafelka kodujący rodzaj, czytelniejsza stopka`

## Najpierw poprawka (to ona blokowała stronę)

`DevSecurityConfiguration.devStaticFilterChain` dopuszczał:
```
"/", "/*.html", "/*.js", "/*.css", "/favicon.ico", "/lib/**", "/img/**", "/assets/**"
```
Wzorzec `/*.css` pasuje **tylko do plików na najwyższym poziomie**. Frontend z commitu 34
wprowadził podkatalogi, więc `/css/portal.css` i `/js/portal-app.js` wpadały do łańcucha
`denyAll` → **403** → strona bez stylów i bez kafelków (logo działało, bo `/assets/**`
było na liście). Dodane: `/css/**`, `/js/**` oraz `/apps/**` (pod aplikacje HTML).

Prod bez zmian — tam statykę serwuje IIS, ten bean nie istnieje.

## Wygląd

- **Hero**: gradient butelkowej zieleni w zieleń trawy + cichy relief łuku z logotypu.
  Logotyp na **białej płycie** — jest ciemnozielony, na zielonym tle by zginął,
  a przebarwianie oficjalnego znaku jest niedopuszczalne. Login w pigułce w rogu.
- **Kolor kafelka koduje rodzaj** (nie jest ozdobą) — paleta wprost z dziedziny agencji:
  zieleń trawy = ZADANIE, złoto zboża = DANE, błękit nieba = APLIKACJA. Pasek u góry,
  kafla ikony i znacznik rodzaju biorą ten sam kolor, więc rodzaj rozpoznajesz kątem oka.
- **Kafelki**: ikona 44 px w kaflu, znacznik rodzaju, tytuł, opis, wiersz akcji ze strzałką
  (unosi się przy najechaniu). Zablokowane: wyszarzone, bez strzałki, bez uniesienia.
- **Stopka**: marka + jednostka w jednej linii, pod spodem zdanie wyjaśniające zasadę
  widoczności zamiast technicznego opisu grup AD.

## Pliki (4)

- `portal-api/src/main/java/pl/dzi/portal/infrastructure/security/DevSecurityConfiguration.java` — **poprawka**
- `frontend/index.html` · `frontend/css/portal.css` · `frontend/js/portal-app.js`

## Zastosowanie

Rozpakuj na katalog projektu (nadpisz) → **restart aplikacji** (zmiana w Javie) →
`http://localhost:8080/index.html`. Logo zostaje tam, gdzie było: `frontend/assets/logo.png`.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
