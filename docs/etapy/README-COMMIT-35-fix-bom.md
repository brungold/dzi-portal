# Commit 35 — POPRAWKA: brakujący BOM testcontainers (błąd w paczce)

`fix(build): wpięcie testcontainers-bom — Maven nie wczytywał portal-api i portal-worker`

## Objaw

Maven odmawia wczytania dwóch modułów:
```
'dependencies.dependency.version' for org.testcontainers:junit-jupiter:jar is missing.
'dependencies.dependency.version' for org.testcontainers:mssqlserver:jar is missing.
The build could not read 2 projects
```
Skutek w IDE: **`src/main/java` nie jest oznaczony jako katalog źródeł** →
„Java file is located outside of the module source root, so it won't be compiled" →
brak zielonej strzałki, brak run configuration, nie da się uruchomić aplikacji.

## Przyczyna

Moduły deklarowały `org.testcontainers:junit-jupiter` i `:mssqlserver` bez `<version>`,
z komentarzem „wersjami zarządza BOM Boota". **To było błędne założenie**: Spring Boot 4.1
(inaczej niż linia 3.x) nie zarządza już wersjami `org.testcontainers`, a BOM testcontainers
nigdy nie został wpięty do pomu nadrzędnego.

## Poprawka

`pom.xml` (nadrzędny): dodane `dependencyManagement` z `testcontainers-bom` (scope `import`)
+ property `testcontainers.version` = 1.20.4. Moduły zostają bez `<version>` — teraz
dziedziczą ją z BOM-a. Poprawiony też mylący komentarz w obu pomach modułów.

Jedno miejsce zmiany wersji zamiast czterech — tak, jak było w zamyśle.

## Pliki (3, same pomy — zero zmian w kodzie)

- `pom.xml` — BOM + property
- `portal-api/pom.xml` — poprawiony komentarz
- `portal-worker/pom.xml` — poprawiony komentarz

## Zastosowanie

Rozpakuj na katalog projektu (nadpisz 3 pomy) → w IntelliJ panel **Maven** → **↻ Reload**.
Folder `java` zrobi się niebieski, ostrzeżenie zniknie, pojawi się zielona strzałka przy `main`.

Jeśli wcześniej dopisałeś `<version>1.20.4</version>` ręcznie w 4 miejscach — ta paczka
przywraca czysty wariant z BOM-em i Twoje ręczne wpisy nie są już potrzebne.

---

*Autor: Maciej Myśliwiec, 2026. Autorskie prawa osobiste (prawo do autorstwa)
niezbywalne — art. 16 pr. aut. Szczegóły: `AUTORSTWO.md`.*
