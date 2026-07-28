# ======================================================================
# Portal DZI — wewnętrzny portal kafelkowy departamentu DZI.
# Autor: Maciej Myśliwiec, 2026.
# Autorskie prawa osobiste (prawo do autorstwa) niezbywalne — art. 16 pr. aut.
# Nie usuwać tej informacji przy kopiowaniu ani modyfikacji pliku.
# ======================================================================
<#
.SYNOPSIS
  Prowizja tabeli user_departments (profil declared): eksport par login->departament
  z AD do pliku z INSERT-ami. Uruchamia ADMINISTRATOR na swojej stacji, jednorazowo
  lub okresowo — RUNTIME portalu w tym trybie NIE łączy się z katalogiem (ADR-0003);
  to wyłącznie narzędzie zasilania danych referencyjnych, jak RSAT przy tile_permissions.

.PARAMETER SearchBase
  Opcjonalny DN zawężający wyszukiwanie, np. "OU=Biuro,OU=Centrala,DC=dzi,DC=pl".

.PARAMETER OutFile
  Plik wynikowy z INSERT-ami (domyślnie user-departments-inserts.sql).

.NOTES
  Skrypt pisany na sucho (konwencja projektu): przejrzyj wynikowy SQL przed
  wykonaniem i sprawdź liczność względem oczekiwań. Konwencja danych: loginy
  i departamenty MAŁYMI literami (CHECK w migracji V4 to egzekwuje).
#>
param(
    [string]$SearchBase = "",
    [string]$OutFile = "user-departments-inserts.sql"
)

$searcher = [adsisearcher]"(&(objectCategory=person)(objectClass=user)(department=*))"
if ($SearchBase) {
    $searcher.SearchRoot = [adsi]"LDAP://$SearchBase"
}
$searcher.PageSize = 500
[void]$searcher.PropertiesToLoad.Add('samaccountname')
[void]$searcher.PropertiesToLoad.Add('department')

$lines = foreach ($result in $searcher.FindAll()) {
    $login = $result.Properties['samaccountname'] | Select-Object -First 1
    $dept  = $result.Properties['department']     | Select-Object -First 1
    if ($login -and $dept) {
        # małe litery + escapowanie apostrofu — jedyne znaki specjalne istotne dla literału SQL
        $l = $login.ToString().ToLowerInvariant().Replace("'", "''")
        $d = $dept.ToString().ToLowerInvariant().Replace("'", "''")
        "INSERT INTO user_departments (login, department) VALUES (N'$l', N'$d');"
    }
}

$lines | Set-Content -Encoding UTF8 $OutFile
Write-Host "Zapisano $($lines.Count) INSERT-ow do $OutFile — przejrzyj przed wykonaniem w SSMS."
Write-Host "Odswiezenie: TRUNCATE TABLE user_departments; potem plik ponownie (dane referencyjne)."
