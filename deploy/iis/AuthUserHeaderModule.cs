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
using System.Web;

namespace PortalAuthUserModule
{
    /// <summary>
    /// Moduł IIS (potok zintegrowany): po etapie uwierzytelnienia wpisuje login
    /// zalogowanego użytkownika do nagłówka X-Auth-User i usuwa X-Auth-Dept.
    ///
    /// Dlaczego moduł, a nie reguła URL Rewrite: reguły rewrite działają w etapie
    /// BeginRequest, PRZED Windows Authentication, więc {LOGON_USER} jest tam
    /// zawsze pusty (diagnoza 2026-08-06). PostAuthenticateRequest to pierwszy
    /// etap potoku, w którym tożsamość już istnieje.
    ///
    /// Własności bezpieczeństwa:
    ///  - Set() nadpisuje wartość przysłaną przez klienta — nagłówkiem nie da się podszyć,
    ///  - X-Auth-Dept jest usuwany — cała tożsamość pochodzi z IIS, nic od klienta,
    ///  - przy braku uwierzytelnienia nagłówek dostaje pustą wartość, którą aplikacja
    ///    (po paczce A) traktuje jak brak tożsamości, czyli czyste 401.
    /// </summary>
    public class AuthUserHeaderModule : IHttpModule
    {
        private const string LoginHeader = "X-Auth-User";
        private const string DeptHeader = "X-Auth-Dept";

        public void Init(HttpApplication application)
        {
            application.PostAuthenticateRequest += OnPostAuthenticateRequest;
        }

        private static void OnPostAuthenticateRequest(object sender, EventArgs e)
        {
            HttpContext context = ((HttpApplication)sender).Context;
            string login = (context.User != null && context.User.Identity.IsAuthenticated)
                    ? context.User.Identity.Name
                    : string.Empty;

            context.Request.Headers.Set(LoginHeader, login);
            context.Request.Headers.Remove(DeptHeader);
        }

        public void Dispose()
        {
            // Moduł nie trzyma żadnych zasobów.
        }
    }
}
