/*
 * Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne — art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
using System;
using System.Security.Principal;
using System.Web;

namespace PortalAuthUserModule
{
    /// <summary>
    /// Modul IIS (potok zintegrowany): po uwierzytelnieniu wpisuje login zalogowanego
    /// uzytkownika do naglowka X-Auth-User i usuwa X-Auth-Dept przyslany przez klienta.
    /// Dzieki temu aplikacja za ARR dostaje tozsamosc USTALONA PRZEZ IIS, nie deklarowana.
    ///
    /// Wersja 2.0 (2026-08-11) — pierwsza produkcyjna, po potwierdzonym wdrozeniu.
    /// Historia (dla nastepnego czytelnika):
    ///  - reguly URL Rewrite NIE nadaja sie do tego zadania: dzialaja w BeginRequest,
    ///    przed uwierzytelnieniem, wiec {LOGON_USER} jest tam zawsze pusty (2026-08-06),
    ///  - modul MUSI byc zarejestrowany w <modules> w web.config witryny; brak wpisu
    ///    oznacza, ze IIS w ogole go nie laduje i login pozostaje pusty (2026-08-11),
    ///  - uwierzytelnianie w trybie jadra (useKernelMode="true") ma zostac WLACZONE:
    ///    jego wylaczenie zabija NTLM na tym serwerze (0x80090305 dla kazdego klienta).
    ///
    /// Wlasnosci bezpieczenstwa:
    ///  - Set() nadpisuje wartosc przyslana przez klienta — naglowkiem nie da sie podszyc
    ///    (zweryfikowane: zadanie z "X-Auth-User: abcde" zwrocilo login prawdziwego uzytkownika),
    ///  - X-Auth-Dept jest usuwany — cala tozsamosc pochodzi z IIS, nic od klienta,
    ///  - przy braku uwierzytelnienia naglowek dostaje pusta wartosc, ktora aplikacja
    ///    traktuje jak brak tozsamosci i odpowiada czystym 401 (poprawka D1 z paczki A).
    /// </summary>
    public class AuthUserHeaderModule : IHttpModule
    {
        private const string LoginHeader = "X-Auth-User";
        private const string DeptHeader = "X-Auth-Dept";

        public void Init(HttpApplication application)
        {
            // Dwa zaczepy: pierwszy ustala stan wyjsciowy i odcina naglowki klienta,
            // drugi uzupelnia login, jesli tozsamosc stala sie widoczna dopiero pozniej.
            application.AuthenticateRequest += OnAuthenticateRequest;
            application.PostAuthenticateRequest += OnPostAuthenticateRequest;
        }

        private static void OnAuthenticateRequest(object sender, EventArgs e)
        {
            HttpContext context = ((HttpApplication)sender).Context;
            context.Request.Headers.Set(LoginHeader, ResolveLogin(context)); // anty-spoof: zawsze nadpisz
            context.Request.Headers.Remove(DeptHeader);
        }

        private static void OnPostAuthenticateRequest(object sender, EventArgs e)
        {
            HttpContext context = ((HttpApplication)sender).Context;
            string login = ResolveLogin(context);
            if (login.Length > 0)
            {
                context.Request.Headers.Set(LoginHeader, login); // tylko ulepsza, nigdy nie czysci
            }
        }

        /// <summary>
        /// Tozsamosc w kolejnosci wiarygodnosci zrodel: natywny token IIS, zmienne
        /// serwerowe, na koncu zarzadzany context.User. Brak tozsamosci => pusty string.
        /// </summary>
        private static string ResolveLogin(HttpContext context)
        {
            string login = Odczytaj(delegate
            {
                WindowsIdentity native = context.Request.LogonUserIdentity;
                return (native != null && native.IsAuthenticated) ? native.Name : null;
            });
            if (login.Length > 0) return login;

            login = Odczytaj(delegate { return context.Request.ServerVariables["LOGON_USER"]; });
            if (login.Length > 0) return login;

            login = Odczytaj(delegate { return context.Request.ServerVariables["AUTH_USER"]; });
            if (login.Length > 0) return login;

            return Odczytaj(delegate
            {
                if (context.User == null || context.User.Identity == null) return null;
                return context.User.Identity.IsAuthenticated ? context.User.Identity.Name : null;
            });
        }

        /// <summary>Odczyt zrodla odporny na wyjatki i puste wartosci — zwraca "" zamiast null.</summary>
        private static string Odczytaj(Func<string> zrodlo)
        {
            try
            {
                string wartosc = zrodlo();
                return string.IsNullOrWhiteSpace(wartosc) ? "" : wartosc;
            }
            catch
            {
                return "";
            }
        }

        public void Dispose()
        {
            // Modul nie trzyma zadnych zasobow.
        }
    }
}
