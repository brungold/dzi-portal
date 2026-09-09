/*
 * Portal DZI - wewnętrzny portal kafelkowy departamentu DZI.
 * Autor: Maciej Myśliwiec, 2026.
 *
 * Autorskie prawa osobiste (w tym prawo do oznaczenia utworu nazwiskiem autora)
 * są niezbywalne - art. 16 ustawy z 4.02.1994 r. o prawie autorskim i prawach
 * pokrewnych. Zakres praw majątkowych regulują odrębne ustalenia z pracodawcą.
 * Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
 */
using System;
using System.Collections.Concurrent;
using System.DirectoryServices;
using System.Security.Principal;
using System.Web;

namespace PortalAuthUserModule
{
    /// <summary>
    /// Modul IIS (potok zintegrowany). Po uwierzytelnieniu wpisuje do naglowkow
    /// tozsamosc ustalona PRZEZ SERWER, nadpisujac cokolwiek przyslal klient:
    ///  - X-Auth-User: uwierzytelniony login (Windows Authentication / NTLM),
    ///  - X-Auth-Dept: departament odczytany z Active Directory.
    ///
    /// Wersja 3.0 (2026-08-12) - departament z OU.
    /// Podstawa decyzji: cztery probki distinguishedName kont z roznych komorek
    /// organizacyjnych - u wszystkich departament jest PIERWSZYM OU po CN:
    /// CN=...,OU=DZI,OU=Biuro,OU=Centrala,DC=zszik,DC=pl. Modul bierze pierwsze
    /// OU ze sciezki i normalizuje do malych liter ('dzi') - spojnie z konwencja
    /// tile_permissions.ad_group.
    ///
    /// Mechanika departamentu:
    ///  - zapytanie DirectorySearcher po sAMAccountName (konto procesu puli;
    ///    odczyt distinguishedName to atrybut publiczny w domenie),
    ///  - cache w pamieci per login: sukces 15 minut, porazka 60 sekund
    ///    (zeby awaria AD nie zasypywala kontrolera ponownymi probami),
    ///  - tryb awaryjny: AD nie odpowiada / brak wyniku => naglowek departamentu
    ///    NIE powstaje - kafelki departamentowe chwilowo znikaja, login i reszta
    ///    portalu dzialaja normalnie,
    ///  - anty-spoof: X-Auth-Dept od klienta jest ZAWSZE usuwany na pierwszym
    ///    zaczepie; wartosc moze pochodzic wylacznie z AD.
    ///
    /// Ustalenia twarde z wdrozenia 2026-08-10/11 (nie lamac):
    ///  - kernel-mode authentication zostaje WLACZONE (useKernelMode="true"),
    ///  - modul dziala tylko z wpisem w <modules> web.config witryny,
    ///  - kompilacja wymaga /reference:System.DirectoryServices.dll (od v3.0).
    /// </summary>
    public class AuthUserHeaderModule : IHttpModule
    {
        private const string LoginHeader = "X-Auth-User";
        private const string DeptHeader = "X-Auth-Dept";
        private static readonly TimeSpan CacheSukces = TimeSpan.FromMinutes(15);
        private static readonly TimeSpan CachePorazka = TimeSpan.FromSeconds(60);
        private static readonly ConcurrentDictionary<string, WpisCache> Cache =
            new ConcurrentDictionary<string, WpisCache>(StringComparer.OrdinalIgnoreCase);

        private sealed class WpisCache
        {
            public string Departament;
            public DateTime WygasaUtc;
        }

        public void Init(HttpApplication application)
        {
            application.AuthenticateRequest += OnAuthenticateRequest;
            application.PostAuthenticateRequest += OnPostAuthenticateRequest;
        }

        private static void OnAuthenticateRequest(object sender, EventArgs e)
        {
            HttpContext context = ((HttpApplication)sender).Context;
            // Anty-spoof: zawsze nadpisz login i wytnij departament klienta.
            context.Request.Headers.Set(LoginHeader, ResolveLogin(context));
            context.Request.Headers.Remove(DeptHeader);
        }

        private static void OnPostAuthenticateRequest(object sender, EventArgs e)
        {
            HttpContext context = ((HttpApplication)sender).Context;
            string login = ResolveLogin(context);
            if (login.Length == 0)
            {
                return; // brak tozsamosci - aplikacja odpowie czystym 401
            }
            context.Request.Headers.Set(LoginHeader, login);

            string departament = ResolveDepartament(login);
            if (departament.Length > 0)
            {
                context.Request.Headers.Set(DeptHeader, departament);
            }
        }

        // ----------------------------- LOGIN (jak w wersji 2.0) -----------------------------

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

        // ----------------------------- DEPARTAMENT (nowosc 3.0) -----------------------------

        /// <summary>Departament dla loginu (format DOMENA\login lub sam login), z cache.</summary>
        private static string ResolveDepartament(string loginZDomena)
        {
            try
            {
                string sam = BezDomeny(loginZDomena);
                if (!LoginBezpieczny(sam))
                {
                    return ""; // nietypowe znaki - nie budujemy z tego filtra LDAP
                }

                WpisCache wpis;
                if (Cache.TryGetValue(sam, out wpis) && wpis.WygasaUtc > DateTime.UtcNow)
                {
                    return wpis.Departament;
                }

                string departament = "";
                bool sukces = false;
                try
                {
                    departament = PierwszeOu(SzukajDnWAd(sam));
                    sukces = true;
                }
                catch
                {
                    // AD niedostepne - tryb awaryjny, krotki cache porazki ponizej.
                }

                WpisCache nowy = new WpisCache();
                nowy.Departament = departament;
                nowy.WygasaUtc = DateTime.UtcNow + (sukces ? CacheSukces : CachePorazka);
                Cache[sam] = nowy;

                return departament;
            }
            catch
            {
                return ""; // zadna awaria departamentu nie moze zepsuc zadania
            }
        }

        private static string BezDomeny(string login)
        {
            int backslash = login.LastIndexOf('\\');
            string sam = backslash >= 0 ? login.Substring(backslash + 1) : login;
            return sam.Trim().ToLowerInvariant();
        }

        /// <summary>Tylko male litery, cyfry, kropka, myslnik, podkreslenie - bezpieczne w filtrze LDAP.</summary>
        private static bool LoginBezpieczny(string sam)
        {
            if (sam.Length == 0 || sam.Length > 64) return false;
            foreach (char c in sam)
            {
                bool ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_';
                if (!ok) return false;
            }
            return true;
        }

        /// <summary>distinguishedName uzytkownika z domeny procesu (konto puli).</summary>
        private static string SzukajDnWAd(string sam)
        {
            using (DirectoryEntry katalog = new DirectoryEntry())
            using (DirectorySearcher szukacz = new DirectorySearcher(katalog))
            {
                szukacz.Filter = "(&(objectCategory=person)(objectClass=user)(sAMAccountName=" + sam + "))";
                szukacz.PropertiesToLoad.Add("distinguishedName");
                szukacz.ClientTimeout = TimeSpan.FromSeconds(3);
                SearchResult wynik = szukacz.FindOne();
                if (wynik == null || wynik.Properties["distinguishedName"].Count == 0)
                {
                    return "";
                }
                return wynik.Properties["distinguishedName"][0] as string;
            }
        }

        /// <summary>
        /// Pierwsze OU ze sciezki DN, malymi literami. Dla
        /// CN=Jan Kowalski,OU=DZI,OU=Biuro,OU=Centrala,DC=zszik,DC=pl -> "dzi".
        /// Odporny na przecinki w CN (fragmenty po rozcieciu nie zaczynaja sie od OU=).
        /// </summary>
        private static string PierwszeOu(string dn)
        {
            if (string.IsNullOrEmpty(dn)) return "";
            string[] czesci = dn.Split(',');
            foreach (string czesc in czesci)
            {
                string t = czesc.Trim();
                if (t.StartsWith("OU=", StringComparison.OrdinalIgnoreCase))
                {
                    return t.Substring(3).Trim().ToLowerInvariant();
                }
            }
            return "";
        }

        public void Dispose()
        {
            // Modul nie trzyma zadnych zasobow.
        }
    }
}
