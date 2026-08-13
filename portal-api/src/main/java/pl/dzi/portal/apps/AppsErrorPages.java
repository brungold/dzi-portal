/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
package pl.dzi.portal.apps;

/**
 * Strony błędów strażnika. Moduły otwiera człowiek w przeglądarce, więc odmowa
 * musi być stroną, nie JSON-em (ten zostaje dla /api). Zero zasobów zewnętrznych
 * i zero treści od użytkownika w środku — stała, samowystarczalna odpowiedź.
 *
 * Świadomie: 404 nie zdradza, czy moduł istnieje, a użytkownik bez uprawnień
 * dostaje 403 z instrukcją "do kogo się zwrócić" — spójnie ze stopką strony głównej.
 */
final class AppsErrorPages {

    private AppsErrorPages() {
    }

    static String forbidden() {
        return page("Brak dostępu",
                "Nie masz uprawnień do tego modułu.",
                "Widzisz moduły przypisane Tobie lub Twojemu departamentowi. "
                        + "Potrzebujesz dostępu — napisz do administratora portalu.");
    }

    static String notFound() {
        return page("Nie znaleziono",
                "Taki moduł lub plik nie istnieje.",
                "Sprawdź adres albo wróć na stronę główną portalu.");
    }

    private static String page(String title, String heading, String detail) {
        return """
                <!doctype html>
                <html lang="pl">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s | Portal DZI</title>
                <style>
                body{margin:0;background:#f4f7f5;color:#16241e;font:15px/1.5 system-ui,"Segoe UI",Arial,sans-serif;
                     min-height:100vh;display:grid;place-items:center}
                .card{max-width:560px;margin:24px;padding:34px 38px;background:#fff;border:1px solid #e1eae5;
                      border-radius:12px;border-top:4px solid #00543d;
                      box-shadow:0 1px 2px rgba(16,40,30,.06),0 8px 28px rgba(16,40,30,.08)}
                h1{margin:0 0 8px;color:#00543d;font-size:24px}
                p{margin:8px 0;color:#5e6e67}
                a{color:#0f6b33;font-weight:650;text-decoration:none}
                a:hover{text-decoration:underline}
                </style>
                </head>
                <body>
                <div class="card">
                <h1>%s</h1>
                <p>%s</p>
                <p><a href="/">&larr; Strona główna Portalu DZI</a></p>
                </div>
                </body>
                </html>
                """.formatted(title, heading, detail);
    }
}
