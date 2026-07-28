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
 * Portal DZI — logika strony głównej (commit 37).
 *
 * Renderowanie DYNAMICZNE z /api/tiles: serwer zwraca wyłącznie kafelki,
 * do których użytkownik ma prawo (RBAC w AccessFacade) — frontend niczego
 * nie rozstrzyga, tylko pokazuje. Dodanie kafelka = INSERT w bazie.
 *
 * Bezpieczeństwo:
 *  - zero innerHTML z danymi: wyłącznie createElement + textContent,
 *  - URL-e kafelków LINK przechodzą sanityzację (tylko http/https/ścieżki),
 *  - okna otwierane z 'noopener' — nowa karta nie ma uchwytu do portalu,
 *  - CSP w index.html blokuje inline JS, więc nawet przemycony znacznik
 *    <script> w danych nie wykona się.
 */
(function () {
    'use strict';

    const TILES_URL = '/api/tiles';
    const WHOAMI_URL = '/api/whoami';
    const POLL_INTERVAL_MS = 2500;
    const TERMINAL_STATUSES = ['SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED'];

    /* Etykiety i czasowniki akcji per rodzaj kafelka (tileType z API).
       Osobna kolumna "kategoria" to kandydat na mały commit backendowy (V4). */
    const RODZAJE = {
        SCRIPT: { etykieta: 'ZADANIE', akcja: 'Uruchom' },
        REPORT: { etykieta: 'DANE', akcja: 'Przeglądaj dane' },
        LINK: { etykieta: 'APLIKACJA', akcja: 'Otwórz' }
    };

    /* Ikony inline (sieć zamknięta — bez CDN-ów typu lucide). */
    const IKONY = {
        SCRIPT: 'M8 6l6 6-6 6M15 18h4',
        REPORT: 'M5 19V9m5 10V5m5 14v-7m5 7H4',
        LINK: 'M10 14L20 4m0 0h-6m6 0v6M9 5H6a2 2 0 0 0-2 2v11a2 2 0 0 0 2 2h11a2 2 0 0 0 2-2v-3'
    };

    const stan = { kafelki: [], fraza: '', rodzaj: 'WSZYSTKIE' };

    function fetchJson(url, options) {
        return fetch(url, Object.assign({ headers: { 'Accept': 'application/json' } }, options))
            .then(async response => {
                if (response.ok) {
                    return response.json();
                }
                let opis = 'HTTP ' + response.status;
                try {
                    const problem = await response.json();
                    opis = problem.detail || problem.title || opis;
                } catch (ignored) { /* odpowiedź bez treści */ }
                throw new Error(opis);
            });
    }

    /* LINK-i pochodzą z bazy administrowanej SQL-em, ale obrona w głąb nic
       nie kosztuje: przepuszczamy wyłącznie http(s) i ścieżki względne. */
    function bezpiecznyUrl(surowy) {
        if (!surowy) {
            return null;
        }
        try {
            const url = new URL(surowy, window.location.origin);
            return (url.protocol === 'https:' || url.protocol === 'http:') ? url.href : null;
        } catch (zly) {
            return null;
        }
    }

    function komunikat(tekst, czyBlad) {
        const el = document.getElementById('komunikat');
        el.textContent = tekst;
        el.hidden = false;
        if (czyBlad) {
            el.setAttribute('data-blad', '');
        } else {
            el.removeAttribute('data-blad');
        }
    }

    function ikona(tileType) {
        const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('fill', 'none');
        svg.setAttribute('stroke', 'currentColor');
        svg.setAttribute('stroke-width', '2');
        svg.setAttribute('stroke-linecap', 'round');
        svg.setAttribute('stroke-linejoin', 'round');
        svg.setAttribute('aria-hidden', 'true');
        const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
        path.setAttribute('d', IKONY[tileType] || IKONY.LINK);
        svg.appendChild(path);
        const box = document.createElement('span');
        box.className = 'ikona';
        box.appendChild(svg);
        return box;
    }

    function budujKafelek(tile) {
        const rodzaj = RODZAJE[tile.tileType] || { etykieta: tile.tileType, akcja: 'Otwórz' };

        /* Semantyka: LINK to <a> (prawy klik/„otwórz w nowej karcie" działają),
           SCRIPT i REPORT to <button> — wykonują akcję w obrębie portalu. */
        let el;
        if (tile.tileType === 'LINK') {
            el = document.createElement('a');
            const url = bezpiecznyUrl(tile.actionRef);
            if (url) {
                el.href = url;
                el.target = '_blank';
                el.rel = 'noopener noreferrer';
            } else {
                el.setAttribute('data-zablokowany', '');
                el.title = 'Nieprawidłowy adres kafelka — zgłoś administratorowi portalu';
            }
        } else {
            el = document.createElement('button');
            el.type = 'button';
        }
        el.className = 'kafelek';
        el.dataset.code = tile.code;
        el.dataset.rodzaj = tile.tileType;   // CSS dobiera kolor akcentu wg rodzaju

        const naglowek = document.createElement('div');
        naglowek.className = 'kafelek-naglowek';
        naglowek.appendChild(ikona(tile.tileType));
        const rodzajEl = document.createElement('span');
        rodzajEl.className = 'rodzaj';
        rodzajEl.textContent = rodzaj.etykieta;
        naglowek.appendChild(rodzajEl);
        el.appendChild(naglowek);

        const tytul = document.createElement('h2');
        tytul.textContent = tile.name;
        el.appendChild(tytul);

        const opis = document.createElement('p');
        opis.className = 'opis';
        opis.textContent = tile.description || '';
        el.appendChild(opis);

        const akcja = document.createElement('div');
        akcja.className = 'kafelek-akcja';
        const akcjaTekst = document.createElement('span');
        akcjaTekst.textContent = rodzaj.akcja;
        akcja.appendChild(akcjaTekst);
        const strzalka = document.createElement('span');
        strzalka.className = 'strzalka';
        strzalka.setAttribute('aria-hidden', 'true');
        strzalka.textContent = '→';
        akcja.appendChild(strzalka);
        const stanEl = document.createElement('span');
        stanEl.className = 'stan';
        stanEl.hidden = true;
        akcja.appendChild(stanEl);
        el.appendChild(akcja);

        if (tile.tileType === 'SCRIPT' && !tile.canExecute) {
            el.setAttribute('data-zablokowany', '');
            el.title = 'Możesz zobaczyć ten kafelek, ale uruchomienie wymaga wyższych uprawnień';
            akcjaTekst.textContent = 'Brak uprawnień do uruchomienia';
            strzalka.remove();
        }

        el.addEventListener('click', function (event) {
            if (tile.tileType !== 'LINK') {
                event.preventDefault();
                klik(tile, el, stanEl);
            } else if (el.hasAttribute('data-zablokowany')) {
                event.preventDefault();
            }
        });
        return el;
    }

    function klik(tile, el, stanEl) {
        if (tile.tileType === 'REPORT') {
            window.location.href = 'dataset.html?code=' + encodeURIComponent(tile.actionRef);
            return;
        }
        if (tile.tileType === 'SCRIPT') {
            if (!tile.canExecute) {
                komunikat('Uruchomienie „' + tile.name + '" wymaga uprawnień EXECUTE — nadaje administrator portalu.', true);
                return;
            }
            uruchom(tile, el, stanEl);
        }
    }

    async function uruchom(tile, el, stanEl) {
        if (el.hasAttribute('data-biegnie')) {
            komunikat('Zadanie „' + tile.name + '" już trwa — poczekaj na wynik.', false);
            return;
        }
        el.setAttribute('data-biegnie', '');
        const strzalkaEl = el.querySelector('.strzalka');
        if (strzalkaEl) {
            strzalkaEl.hidden = true;   // stan zadania zajmuje miejsce strzalki
        }
        pokazStan(stanEl, 'biegnie', 'trwa…');
        try {
            const zlecone = await fetchJson('/api/tiles/' + encodeURIComponent(tile.code) + '/run', { method: 'POST' });
            komunikat('Uruchomiono: ' + tile.name + ' (zadanie #' + zlecone.taskId + ')', false);
            const zadanie = await czekajNaKoniec(zlecone.taskId);
            await pokazWynik(tile, zadanie, stanEl);
        } catch (blad) {
            pokazStan(stanEl, 'blad', 'błąd');
            komunikat('Nie udało się uruchomić „' + tile.name + '": ' + blad.message, true);
        } finally {
            el.removeAttribute('data-biegnie');
        }
    }

    function czekajNaKoniec(taskId) {
        return new Promise((resolve, reject) => {
            const timer = setInterval(async () => {
                try {
                    const zadanie = await fetchJson('/api/tasks/' + taskId);
                    if (TERMINAL_STATUSES.includes(zadanie.status)) {
                        clearInterval(timer);
                        resolve(zadanie);
                    }
                } catch (blad) {
                    clearInterval(timer);
                    reject(blad);
                }
            }, POLL_INTERVAL_MS);
        });
    }

    async function pokazWynik(tile, zadanie, stanEl) {
        if (zadanie.status === 'SUCCEEDED') {
            pokazStan(stanEl, 'ok', '✔ gotowe');
            komunikat('✔ ' + tile.name + ' — zakończone poprawnie (zadanie #' + zadanie.id + ')', false);
            return;
        }
        const etykieta = zadanie.status === 'TIMED_OUT'
            ? '⏱ limit czasu'
            : '✖ błąd' + (zadanie.exitCode != null ? ' (kod ' + zadanie.exitCode + ')' : '');
        pokazStan(stanEl, 'blad', etykieta);
        komunikat('✖ ' + tile.name + ' — ' + etykieta + '. Log zadania w konsoli (F12).', true);
        try {
            const log = await fetchJson('/api/tasks/' + zadanie.id + '/log');
            console.group('portal: log zadania #' + zadanie.id + ' [' + zadanie.status + ']');
            log.forEach(linia => console.log('[' + linia.stream + '] ' + linia.line));
            console.groupEnd();
        } catch (blad) {
            console.warn('portal: nie udało się pobrać logu zadania', blad);
        }
    }

    function pokazStan(stanEl, typ, tekst) {
        stanEl.className = 'stan stan-' + typ;
        stanEl.hidden = false;
        if (typ === 'biegnie') {
            stanEl.textContent = '';
            const kolko = document.createElement('span');
            kolko.className = 'kolko';
            stanEl.appendChild(kolko);
            stanEl.appendChild(document.createTextNode(tekst));
        } else {
            stanEl.textContent = tekst;
        }
    }

    /* ===== Filtrowanie i render ===== */

    function pasuje(tile) {
        if (stan.rodzaj !== 'WSZYSTKIE' && tile.tileType !== stan.rodzaj) {
            return false;
        }
        if (!stan.fraza) {
            return true;
        }
        const fraza = stan.fraza.toLowerCase();
        return (tile.name || '').toLowerCase().includes(fraza)
                || (tile.description || '').toLowerCase().includes(fraza);
    }

    function renderuj() {
        const siatka = document.getElementById('siatka');
        const pusto = document.getElementById('pusto');
        siatka.replaceChildren();
        const widoczne = stan.kafelki.filter(pasuje);
        widoczne.forEach(tile => siatka.appendChild(budujKafelek(tile)));

        if (stan.kafelki.length === 0) {
            pusto.textContent = 'Nie masz jeszcze dostępu do żadnego kafelka. '
                    + 'Dostępy nadaje administrator portalu na podstawie grup AD — napisz do niego, czego potrzebujesz.';
            pusto.hidden = false;
        } else if (widoczne.length === 0) {
            pusto.textContent = 'Nic nie pasuje do wyszukiwania. Zmień frazę albo wybierz inny rodzaj.';
            pusto.hidden = false;
        } else {
            pusto.hidden = true;
        }
    }

    function zbudujFiltry() {
        const kontener = document.getElementById('filtry');
        const obecne = [...new Set(stan.kafelki.map(tile => tile.tileType))];
        const opcje = [['WSZYSTKIE', 'Wszystkie']].concat(
                obecne.map(typ => [typ, (RODZAJE[typ] || { etykieta: typ }).etykieta]));
        if (opcje.length <= 2) {
            return; // jeden rodzaj — filtry tylko zaśmiecają
        }
        opcje.forEach(([wartosc, etykieta]) => {
            const przycisk = document.createElement('button');
            przycisk.type = 'button';
            przycisk.className = 'filtr';
            przycisk.textContent = etykieta;
            przycisk.setAttribute('aria-pressed', String(wartosc === stan.rodzaj));
            przycisk.addEventListener('click', () => {
                stan.rodzaj = wartosc;
                kontener.querySelectorAll('.filtr').forEach(inny =>
                        inny.setAttribute('aria-pressed', String(inny === przycisk)));
                renderuj();
            });
            kontener.appendChild(przycisk);
        });
    }

    async function init() {
        document.getElementById('szukaj').addEventListener('input', event => {
            stan.fraza = event.target.value.trim();
            renderuj();
        });
        try {
            const [kafelki, whoami] = await Promise.all([fetchJson(TILES_URL), fetchJson(WHOAMI_URL)]);
            stan.kafelki = kafelki;
            document.getElementById('uzytkownik-login').textContent = whoami.login;
            document.getElementById('uzytkownik-info').textContent =
                    'kafelki: ' + kafelki.length + ' · grupy: ' + (whoami.groups || []).length;
            document.getElementById('uzytkownik').hidden = false;
            zbudujFiltry();
            renderuj();
        } catch (blad) {
            console.error('portal:', blad);
            komunikat('Nie udało się wczytać portalu: ' + blad.message
                    + '. Odśwież stronę; jeśli problem wraca — zgłoś administratorowi.', true);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
