/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
/**
 * portal-bootstrap.js — JEDYNA warstwa integracji statycznego frontendu z API portalu.
 * (v3, Etap 5: kafelki REPORT otwierają widok zbioru danych — dataset.html)
 *
 * Kontrakt z plikiem kafelkowym (index.html od autora frontendu):
 *   1) każdy kafelek dostaje atrybut data-tile-id="<code z tabeli tiles>",
 *   2) przed </body>: <script src="portal-bootstrap.js" defer></script>,
 *   3) ŻADNYCH innych zmian.
 *
 * Zero zależności, zero CDN. Vanilla JS, same-origin fetch.
 */
(function () {
    'use strict';

    const TILES_URL = '/api/tiles';
    const WHOAMI_URL = '/api/whoami';
    const POLL_INTERVAL_MS = 2500;
    const TERMINAL_STATUSES = ['SUCCEEDED', 'FAILED', 'TIMED_OUT', 'CANCELLED'];

    async function fetchJson(url, options) {
        const response = await fetch(url, Object.assign({
            credentials: 'same-origin',
            headers: { Accept: 'application/json' }
        }, options || {}));
        if (!response.ok) {
            throw new Error(url + ' → HTTP ' + response.status);
        }
        return response.json();
    }

    function tileElements() {
        return Array.from(document.querySelectorAll('[data-tile-id]'));
    }

    function hideTile(element) {
        element.dataset.portalHidden = '1';
        element.style.display = 'none';
    }

    function showTile(element) {
        delete element.dataset.portalHidden;
        element.style.display = '';
    }

    function banner(text, isError) {
        let el = document.getElementById('portal-banner');
        if (!el) {
            el = document.createElement('div');
            el.id = 'portal-banner';
            el.style.cssText = 'position:fixed;top:0;left:0;right:0;z-index:9999;'
                + 'padding:10px 16px;font:14px/1.4 system-ui,sans-serif;text-align:center;';
            document.body.appendChild(el);
        }
        el.style.background = isError ? '#c0392b' : '#2c3e50';
        el.style.color = '#fff';
        el.textContent = text;
        el.style.display = '';
        if (!isError) {
            setTimeout(() => { el.style.display = 'none'; }, 6000);
        }
    }

    function statusBar(login, visibleCount) {
        let el = document.getElementById('portal-status');
        if (!el) {
            el = document.createElement('div');
            el.id = 'portal-status';
            el.style.cssText = 'position:fixed;bottom:8px;right:8px;z-index:9998;'
                + 'padding:4px 10px;border-radius:4px;background:rgba(44,62,80,.85);color:#fff;'
                + 'font:12px/1.4 system-ui,sans-serif;';
            document.body.appendChild(el);
        }
        el.textContent = login + ' · kafelki: ' + visibleCount;
        el.title = 'Diagnostyka: /api/whoami';
    }

    function wireTile(element, tile) {
        element.style.cursor = 'pointer';
        element.title = tile.description || tile.name;
        if (tile.tileType === 'SCRIPT' && !tile.canExecute) {
            element.style.opacity = '0.55';
            element.title += ' — brak uprawnień do uruchomienia';
        }
        element.addEventListener('click', function (event) {
            event.preventDefault();
            onTileClick(tile, element);
        });
    }

    function onTileClick(tile, element) {
        switch (tile.tileType) {
            case 'LINK':
                window.open(tile.actionRef, '_blank', 'noopener');
                break;
            case 'SCRIPT':
                if (!tile.canExecute) {
                    banner('Brak uprawnień do uruchomienia: ' + tile.name, true);
                    return;
                }
                runScript(tile, element);
                break;
            case 'REPORT':
                // Widok zbioru: Tabulator + edycja + import (dataset.html czyta ?code=)
                window.location.href = 'dataset.html?code=' + encodeURIComponent(tile.actionRef);
                break;
            default:
                console.warn('portal-bootstrap: nieznany tileType', tile);
        }
    }

    /** POST /run -> polling statusu do stanu terminalnego; kafelek zablokowany na czas biegu. */
    async function runScript(tile, element) {
        if (element.dataset.portalRunning) {
            banner('Zadanie „' + tile.name + '" już trwa — poczekaj na wynik.', false);
            return;
        }
        element.dataset.portalRunning = '1';
        element.style.outline = '2px solid #2980b9';
        try {
            const submitted = await fetchJson('/api/tiles/' + encodeURIComponent(tile.code) + '/run',
                { method: 'POST' });
            banner('Uruchomiono: ' + tile.name + ' (zadanie #' + submitted.taskId + ')', false);
            await pollUntilDone(tile, submitted.taskId);
        } catch (error) {
            console.error('portal-bootstrap:', error);
            banner('Nie udało się uruchomić „' + tile.name + '": ' + error.message, true);
        } finally {
            delete element.dataset.portalRunning;
            element.style.outline = '';
        }
    }

    function pollUntilDone(tile, taskId) {
        return new Promise(resolve => {
            const timer = setInterval(async () => {
                try {
                    const task = await fetchJson('/api/tasks/' + taskId);
                    if (!TERMINAL_STATUSES.includes(task.status)) {
                        return; // wciąż PENDING / IN_PROGRESS
                    }
                    clearInterval(timer);
                    await showResult(tile, task);
                    resolve();
                } catch (error) {
                    clearInterval(timer);
                    console.error('portal-bootstrap: polling', error);
                    banner('Utracono status zadania #' + taskId + ': ' + error.message, true);
                    resolve();
                }
            }, POLL_INTERVAL_MS);
        });
    }

    async function showResult(tile, task) {
        if (task.status === 'SUCCEEDED') {
            banner('✔ ' + tile.name + ' — zakończone poprawnie (zadanie #' + task.id + ')', false);
            return;
        }
        const label = task.status === 'TIMED_OUT'
            ? 'przekroczono limit czasu'
            : 'zakończone błędem' + (task.exitCode != null ? ' (kod ' + task.exitCode + ')' : '');
        banner('✖ ' + tile.name + ' — ' + label + '. Szczegóły w konsoli (F12).', true);
        try {
            const log = await fetchJson('/api/tasks/' + task.id + '/log');
            console.group('portal-bootstrap: log zadania #' + task.id + ' [' + task.status + ']');
            log.forEach(line => console.log('[' + line.stream + '] ' + line.line));
            console.groupEnd();
        } catch (error) {
            console.warn('portal-bootstrap: nie udało się pobrać logu zadania', error);
        }
    }

    async function init() {
        const elements = tileElements();
        if (elements.length === 0) {
            console.warn('portal-bootstrap: brak elementów [data-tile-id] — dodaj atrybuty do kafelków w index.html.');
        }
        elements.forEach(hideTile);
        const elementByCode = new Map(elements.map(el => [el.dataset.tileId, el]));

        try {
            const [tiles, whoami] = await Promise.all([fetchJson(TILES_URL), fetchJson(WHOAMI_URL)]);

            let visible = 0;
            for (const tile of tiles) {
                const element = elementByCode.get(tile.code);
                if (!element) {
                    console.warn('portal-bootstrap: API zwróciło kafelek bez elementu w HTML:', tile.code);
                    continue;
                }
                showTile(element);
                wireTile(element, tile);
                visible++;
            }
            for (const [code, element] of elementByCode) {
                if (element.dataset.portalHidden) {
                    console.info('portal-bootstrap: kafelek ukryty (brak uprawnień lub brak w API):', code);
                }
            }

            statusBar(whoami.login, visible);
            if (visible === 0) {
                banner('Nie masz uprawnień do żadnego kafelka — skontaktuj się z administratorem portalu.', true);
            }
        } catch (error) {
            console.error('portal-bootstrap:', error);
            banner('Nie udało się pobrać kafelków: ' + error.message, true);
        }
    }

    window.PortalBootstrap = { reload: init };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
