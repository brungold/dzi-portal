# IIS — konfiguracja witryny portal.dzi.pl (Etap 1)

## Moduły
1. **URL Rewrite** i **ARR** (Application Request Routing) — instalacja offline (serwer bez internetu):
   pobierz instalatory MSI na stacji, przenieś zatwierdzonym kanałem.
2. W ARR: *Server Proxy Settings → Enable proxy* (bez tego `action type="Rewrite"` na inny host nie działa).

## Allowed Server Variables (krok, o którym wszyscy zapominają)
URL Rewrite → *View Server Variables* (poziom serwera lub witryny) → dodaj:
- `HTTP_X_AUTH_USER`
- `HTTP_X_FORWARDED_FOR`

Bez tego reguła z `<serverVariables>` zostanie odrzucona.

## Witryna
- Binding: `https / 443 / portal.dzi.pl`, certyfikat z wewnętrznego AD CS.
- Katalog fizyczny: statyczny frontend (`frontend/` z tego repo).
- **Authentication**: Anonymous **OFF**, Windows Authentication **ON**,
  Providers: `Negotiate` PRZED `NTLM`.
- Handlerów PHP/Python (preinstalowane na serwerze) **nie mapować** na tej witrynie.

## Checklist Kerberos (kolejność ma znaczenie)
1. DNS: rekord **A** `portal.dzi.pl → 10.0.22.150` (nie CNAME).
2. SPN (nazwa portalu ≠ nazwa hosta, więc SPN jest obowiązkowy):
   `setspn -S HTTP/portal.dzi.pl DZI-APP01V$`
3. SPN na koncie **komputera** + domyślna pula aplikacyjna ⇒ kernel-mode authentication
   zostawić włączony (default). Gdyby pula chodziła na koncie domenowym — SPN na to konto
   i `useAppPoolCredentials=true`.
4. GPO: `portal.dzi.pl` w strefie **Intranet lokalny** na stacjach (inaczej brak SSO / prompt).
5. Test **z innej maszyny niż serwer** — loopback na serwerze zawsze "działa" i zamazuje wynik.

## Weryfikacja
- Na stacji: `klist` → bilet `HTTP/portal.dzi.pl`.
- Log IIS: pole `cs-username` wypełnione (`DZI\login`).
- `https://portal.dzi.pl/api/whoami` → JSON z loginem i grupami.
- Próba spoofa: żądanie z nagłówkiem `X-Auth-User` od klienta → IIS nadpisuje wartość,
  a nawet gdyby reguła padła, aplikacja odrzuca nagłówek spoza loopbacku (401 + WARN w logu).

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
