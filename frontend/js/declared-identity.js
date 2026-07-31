/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
/*
 * declared-identity.js — deklaracja loginu w przeglądarce (profil `declared`, ADR-0003).
 *
 * Zasada działania: JEDNA nakładka na window.fetch zamiast zmian w logice stron.
 * Każde wywołanie /api czeka na ustalenie tożsamości i dostaje nagłówek X-Auth-User.
 * Dzięki temu portal-app.js, portal-bootstrap.js i inline-skrypt dataset.html
 * pozostają NIETKNIĘTE — jeden punkt integracji, jeden plik do przeglądu.
 *
 * Wykrywanie trybu (zero konfiguracji):
 *  - sonda GET /api/whoami BEZ nagłówka,
 *  - 200  -> tryb przezroczysty (wariant A: nagłówek wstrzykuje IIS; albo dev-fallback)
 *            — moduł nic nie robi, okno nigdy się nie pokazuje,
 *  - 401  -> tryb declared — okno deklaracji, login do localStorage, nagłówek do /api.
 *
 * Klient deklaruje WYŁĄCZNIE login (ADR-0003). Normalizacja lustrzana do serwera:
 * "DZI\jkowalski" / "jkowalski@dzi.pl" -> "jkowalski", małe litery (CHECK w V4).
 * 401 przy ustawionym loginie = serwer odrzucił deklarację (np. spoza zaufanych
 * zakresów) -> czyścimy zapis, prosimy ponownie, po zatwierdzeniu przeładowanie.
 *
 * CSP: zero inline — DOM przez createElement, style przez element.style (CSSOM),
 * obsługa zdarzeń przez addEventListener. Zależności: żadnych.
 */
(function () {
    'use strict';

    var HEADER = 'X-Auth-User';                    // = portal.security.header (application-declared.yml)
    var STORAGE_KEY = 'dzi-portal.declared-login';
    var WHOAMI_URL = '/api/whoami';

    var originalFetch = window.fetch.bind(window);
    var pamiec = null;          // zapasowy magazyn, gdy localStorage niedostępny
    var tryb = null;            // null = nieustalony, 'transparent' | 'declared'
    var gotowosc = null;        // Promise<string|null> — login albo null (tryb przezroczysty)
    var przeladowanieWToku = false;

    /* ===== magazyn loginu ===== */

    function odczytajLogin() {
        try { return window.localStorage.getItem(STORAGE_KEY) || pamiec; }
        catch (bez) { return pamiec; }
    }

    function zapiszLogin(login) {
        pamiec = login;
        try { window.localStorage.setItem(STORAGE_KEY, login); } catch (bez) { /* tryb prywatny itp. */ }
    }

    function wyczyscLogin() {
        pamiec = null;
        try { window.localStorage.removeItem(STORAGE_KEY); } catch (bez) { /* jw. */ }
    }

    /* ===== normalizacja i walidacja (lustro DeclaredHeaderAuthenticationFilter + CHECK z V4) ===== */

    function normalizuj(surowy) {
        var wartosc = String(surowy || '').trim();
        var backslash = wartosc.lastIndexOf('\\');
        if (backslash >= 0) { wartosc = wartosc.substring(backslash + 1); }
        var malpa = wartosc.indexOf('@');
        if (malpa > 0) { wartosc = wartosc.substring(0, malpa); }
        return wartosc.toLowerCase();
    }

    function bladWalidacji(login) {
        if (!login) { return 'Podaj login.'; }
        if (login.length > 128) { return 'Login jest za długi (limit 128 znaków).'; }
        if (!/^[a-z0-9._-]+$/.test(login)) {
            return 'Login może zawierać małe litery, cyfry oraz znaki . _ - (bez spacji).';
        }
        return null;
    }

    /* ===== rozpoznanie żądań do API ===== */

    function czyApi(wejscie) {
        var surowyUrl = (typeof wejscie === 'string') ? wejscie
                : (wejscie && typeof wejscie.url === 'string') ? wejscie.url
                : String(wejscie);
        try {
            var url = new URL(surowyUrl, window.location.origin);
            return url.origin === window.location.origin
                    && (url.pathname === '/api' || url.pathname.indexOf('/api/') === 0);
        } catch (zly) {
            return false;
        }
    }

    function zNaglowkiem(wejscie, opcje, login) {
        var naglowki = new Headers(
                (opcje && opcje.headers) || (wejscie && wejscie.headers) || undefined);
        naglowki.set(HEADER, login);
        var nowe = Object.assign({}, opcje || {});
        nowe.headers = naglowki;
        return nowe;
    }

    /* ===== okno deklaracji (DOM budowany w JS — zgodnie z CSP bez inline) ===== */

    var okno = null;

    function styl(el, wlasciwosci) {
        Object.keys(wlasciwosci).forEach(function (klucz) { el.style[klucz] = wlasciwosci[klucz]; });
        return el;
    }

    function pokazOkno(ustawienia) {
        if (okno) { okno.scrim.remove(); okno = null; }

        var scrim = styl(document.createElement('div'), {
            position: 'fixed', inset: '0', zIndex: '10000',
            background: 'rgba(10, 24, 18, 0.55)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontFamily: "system-ui, 'Segoe UI', sans-serif"
        });

        var karta = styl(document.createElement('div'), {
            background: '#FFFFFF', color: '#16241E', borderRadius: '14px',
            padding: '26px 28px', width: 'calc(100% - 32px)', maxWidth: '400px',
            boxShadow: '0 14px 40px rgba(16, 40, 30, 0.25)', boxSizing: 'border-box'
        });
        karta.setAttribute('role', 'dialog');
        karta.setAttribute('aria-modal', 'true');
        karta.setAttribute('aria-labelledby', 'deklaracja-tytul');

        var tytul = styl(document.createElement('h2'), {
            margin: '0 0 6px', fontSize: '18px', color: '#00543D'
        });
        tytul.id = 'deklaracja-tytul';
        tytul.textContent = 'Portal DZI — deklaracja tożsamości';

        var opis = styl(document.createElement('p'), {
            margin: '0 0 14px', fontSize: '13px', lineHeight: '1.5', color: '#5E6E67'
        });
        opis.textContent = 'Podaj swój login domenowy. Portal działa w trybie deklarowanej '
                + 'tożsamości: dostęp wynika z przypisania loginu do departamentu po stronie '
                + 'serwera, a każda deklaracja i jej adres są zapisywane w rejestrze audytu.';

        var etykieta = styl(document.createElement('label'), {
            display: 'block', fontSize: '12px', fontWeight: '600', marginBottom: '4px'
        });
        etykieta.textContent = 'Login';
        etykieta.htmlFor = 'deklaracja-login';

        var pole = styl(document.createElement('input'), {
            display: 'block', width: '100%', boxSizing: 'border-box',
            padding: '9px 11px', fontSize: '14px', color: '#16241E',
            border: '1px solid #E1EAE5', borderRadius: '10px', outline: 'none'
        });
        pole.type = 'text';
        pole.id = 'deklaracja-login';
        pole.autocomplete = 'username';
        pole.spellcheck = false;
        pole.placeholder = 'np. jkowalski';
        if (ustawienia.wstepny) { pole.value = ustawienia.wstepny; }

        var blad = styl(document.createElement('p'), {
            margin: '8px 0 0', fontSize: '12px', color: '#A93226', minHeight: '15px'
        });
        blad.setAttribute('role', 'alert');
        blad.textContent = ustawienia.komunikat || '';

        var przycisk = styl(document.createElement('button'), {
            marginTop: '14px', width: '100%', padding: '10px 14px',
            fontSize: '14px', fontWeight: '600', color: '#FFFFFF',
            background: '#00543D', border: '0', borderRadius: '10px', cursor: 'pointer'
        });
        przycisk.type = 'button';
        przycisk.textContent = 'Wejdź do portalu';

        function zatwierdz() {
            var login = normalizuj(pole.value);
            var problem = bladWalidacji(login);
            if (problem) {
                blad.textContent = problem;
                pole.focus();
                return;
            }
            zapiszLogin(login);
            scrim.remove();
            okno = null;
            ustawienia.poZatwierdzeniu(login);
        }

        przycisk.addEventListener('click', zatwierdz);
        pole.addEventListener('keydown', function (zdarzenie) {
            if (zdarzenie.key === 'Enter') { zdarzenie.preventDefault(); zatwierdz(); }
        });

        karta.appendChild(tytul);
        karta.appendChild(opis);
        karta.appendChild(etykieta);
        karta.appendChild(pole);
        karta.appendChild(blad);
        karta.appendChild(przycisk);
        scrim.appendChild(karta);
        document.body.appendChild(scrim);
        pole.focus();

        okno = { scrim: scrim };
    }

    /* ===== ustalenie tożsamości (leniwe, jednokrotne) ===== */

    function ustalTozsamosc() {
        if (gotowosc) { return gotowosc; }
        gotowosc = new Promise(function (resolve) {
            var zapamietany = odczytajLogin();
            if (zapamietany) {
                // Zapis z poprzedniej wizyty: deklarujemy bez sondy. W wariancie A nagłówek
                // i tak nadpisze IIS, a w dev przejmie go filtr — nieszkodliwe w obu.
                tryb = 'declared';
                resolve(zapamietany);
                return;
            }
            originalFetch(WHOAMI_URL, {
                credentials: 'same-origin', cache: 'no-store',
                headers: { 'Accept': 'application/json' }
            }).then(function (odpowiedz) {
                if (odpowiedz.status === 401) {
                    tryb = 'declared';
                    pokazOkno({ poZatwierdzeniu: function (login) { resolve(login); } });
                } else {
                    // 200 = tożsamość daje środowisko (wariant A / dev-fallback).
                    // Inne statusy i błędy sieci też przepuszczamy bez okna —
                    // strony pokażą własne komunikaty, a moduł nie udaje bramki.
                    tryb = 'transparent';
                    resolve(null);
                }
            }).catch(function () {
                tryb = 'transparent';
                resolve(null);
            });
        });
        return gotowosc;
    }

    function odrzuconaDeklaracja() {
        if (przeladowanieWToku) { return; }
        przeladowanieWToku = true;
        wyczyscLogin();
        pokazOkno({
            komunikat: 'Serwer odrzucił deklarację (401). Wpisz login ponownie.',
            poZatwierdzeniu: function () { window.location.reload(); }
        });
    }

    /* ===== nakładka na fetch ===== */

    window.fetch = function (wejscie, opcje) {
        if (!czyApi(wejscie)) {
            return originalFetch(wejscie, opcje);
        }
        return ustalTozsamosc().then(function (login) {
            if (!login) {
                return originalFetch(wejscie, opcje);
            }
            return originalFetch(wejscie, zNaglowkiem(wejscie, opcje, login)).then(function (odpowiedz) {
                if (odpowiedz.status === 401) {
                    odrzuconaDeklaracja();
                    return new Promise(function () { /* strona zaraz się przeładuje */ });
                }
                return odpowiedz;
            });
        });
    };

    /* ===== zmiana użytkownika ===== */

    function zmienUzytkownika() {
        pokazOkno({
            wstepny: odczytajLogin() || '',
            poZatwierdzeniu: function () { window.location.reload(); }
        });
    }

    function podepnijZmiane() {
        if (tryb !== 'declared') { return; }
        var el = document.getElementById('uzytkownik-login');
        if (!el || el.dataset.deklaracjaPodpieta) { return; }
        el.dataset.deklaracjaPodpieta = '1';
        el.title = 'Kliknij, aby zmienić deklarowany login';
        el.setAttribute('role', 'button');
        el.tabIndex = 0;
        el.style.cursor = 'pointer';
        el.style.textDecoration = 'underline dotted';
        el.addEventListener('click', zmienUzytkownika);
        el.addEventListener('keydown', function (zdarzenie) {
            if (zdarzenie.key === 'Enter' || zdarzenie.key === ' ') {
                zdarzenie.preventDefault();
                zmienUzytkownika();
            }
        });
    }

    // Element loginu w hero wypełnia portal-app po odpowiedzi whoami — podpinamy się
    // z opóźnieniem prostym odpytaniem, bez MutationObserverów (KISS).
    var proby = 0;
    var licznik = window.setInterval(function () {
        podepnijZmiane();
        if (++proby >= 40 || (document.getElementById('uzytkownik-login')
                && document.getElementById('uzytkownik-login').dataset.deklaracjaPodpieta)) {
            window.clearInterval(licznik);
        }
    }, 500);

    /* ===== diagnostyka (konsola / inne strony bez elementu hero) ===== */

    window.PortalIdentity = {
        current: function () { return odczytajLogin(); },
        change: zmienUzytkownika,
        clear: function () { wyczyscLogin(); window.location.reload(); }
    };
})();
