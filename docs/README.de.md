# NeoDhizuku

Ein Fork von [Dhizuku](https://github.com/iamr0s/Dhizuku), der Gerätebesitzer-Rechte (DeviceOwner) kontrolliert mit anderen Anwendungen teilt.

---

## ⚠️ Warnhinweis: Unfertig

**Diese Version befindet sich in der Entwicklung – nicht auf dem Alltagsgerät oder in der Produktion verwenden.**
Einige Funktionen sind gerade erst entstanden, andere wurden auf realen Geräten noch nicht ausreichend geprüft. Der Paketname wurde zu `com.neodhizuku.more` geändert und kann mit der offiziellen Version **nicht koexistieren** (sie würden unterschiedliche Gerätebesitzer). Bitte zuerst auf einem Testgerät prüfen. Es besteht keine Datenkompatibilität zur offiziellen Version – vor der Migration sichern.

---

## 1. Änderungen und neue Funktionen

Alle Änderungen hängen am Hauptschalter `NeoDhizuku.UseNeoDhizuku` (`app/src/main/java/com/neodhizuku/more/NeoDhizuku.kt`).

> Bei `false` werden alle Teilfunktionen unwirksam und das Verhalten **fällt vollständig auf die ursprüngliche Upstream-Implementierung zurück**. Einzelne Teilfunktionen lassen sich ebenfalls separat abschalten, was den Vergleich beim Debuggen erleichtert.

### Grundlegende Kennungen

| Element | Upstream | Dieses Projekt |
|---|---|---|
| Paketname | `com.rosan.dhizuku` | `com.neodhizuku.more` |
| App-Name | Dhizuku | NeoDhizuku |
| Android-Unterstützung | Android 8.0+ (kompiliert gegen API 37) | wie links |

> Der API-Berechtigungsname und der AIDL-Paketname im Manifest bleiben **unverändert**, damit bestehende Drittanbieter-Clients diese App ohne Anpassung erkennen.

### Sicherheitshärtung

- **Der Autorisierungseinstieg vertraut nicht mehr der vom Client selbst gemeldeten UID**: Der Server ermittelt die wirkliche Identität per `Binder.getCallingUid()` und stellt ein Einmal-Token aus (10 Minuten gültig). Die UID wird erst anerkannt, nachdem die Activity das Token geprüft hat. Ältere Clients nutzen einen Kompatibilitätspfad und überschreiben bestehende Berechtigungen nicht.
- Provider-Authentifizierung, Audit von `remoteProcess`-Befehlen, Prüfung der eigenen Signatur beim Start.
- Die Signatur-Whitelist wird **zur Build-Zeit** eingespeist und bei fehlender Konfiguration automatisch übersprungen, damit man ungehindert selbst bauen und debuggen kann.

### Oberfläche neu gestaltet

Angelehnt an die Layout-Ideen der KernelSU-Verwaltungs-App, umgesetzt mit Material3 (**miuix / coui werden nicht verwendet**):

- **Schwebende untere Navigationsleiste**: von vier Seiten gemeinsam genutzt, Symbol + Text, **keine Emojis**. Ohne Aktivierung werden nur „Start / Einstellungen" angezeigt; Unterseiten blenden die Leiste aus.
- **Statuskarte auf der Startseite**: nach der Aktivierung „Statuskarte links + zwei kleine Karten rechts übereinander" – Häkchen-Symbol + aktiviert + Besitzertyp + Versionsnummer (Tippen öffnet die Aktivierungsseite), oben rechts die Zahl der autorisierten Apps, unten rechts die Laufzeit des Dienstes (`HH:MM:SS`, bei Neustart des Dienstes zurückgesetzt). **Ohne Aktivierung bleibt das ursprüngliche Upstream-Design erhalten.**
- **App-Verwaltung**: keine Tabs, alle mit Dhizuku verbundenen Apps stehen in einer einzigen Liste, der Schalter zeigt den Autorisierungsstatus direkt. Apps, die die Berechtigung nicht im Manifest deklarieren, aber Aufrufe getätigt haben (z. B. manche Installer), werden automatisch aufgenommen und können direkt autorisiert werden.
- **Eigene Seite für die Sperrliste**: aus der App-Verwaltung ausgegliedert; Einträge einzeln entsperrbar.
- **Autorisierungsdialog**: keine Fortschrittsanzeige; „Erlauben" hellgrün, „Ablehnen" rot hervorgehoben, „Zur Sperrliste hinzufügen" schwarz, alles reiner Text ohne Hintergrundrahmen; der Countdown steht in kleiner Schrift unter der Button-Zeile.
- **Aufrufprotokoll**: hält fest, welche Fähigkeiten die einzelnen Apps aufgerufen haben, alles in kurzen chinesischen Sätzen beschrieben. **Dauerhaft gespeichert, standardmäßig 30 Tage aufbewahrt**; jeder Eintrag enthält den Ausschnitt des Server-Laufzeitprotokolls von diesem Zeitpunkt. Zusätzlich gibt es den Tab „Laufzeitprotokoll" für das eigene Protokoll des Servers, **auch ohne Aktivierung einsehbar**.
- **Dienst starten/stoppen**: Die Schaltfläche oben rechts zeigt im Betrieb ein Kreuz und wird nach dem Stopp zum Neustart-Symbol; die Laufzeitkarte wird beim Stopp vollständig rot und zeigt „Dienst verweigert". Nach dem Stopp bleiben das Ansehen von Berechtigungen und des Aufrufprotokolls möglich, alle übrigen Operationen werden abgelehnt.
- **Abfangen der Gerätebesitzer-Übertragung**: Eine autorisierte Drittanbieter-App kann über `binderWrapper` als Gerätebesitzer beliebige DPM-Methoden aufrufen; bietet sie keine Möglichkeit zur Rückgabe, ist der Besitzer nach der Übertragung nicht mehr zurückzuholen. Die Einstellung „Drittanbieter-Apps die Übertragung des Gerätebesitzers erlauben" ist **standardmäßig aus**; ist sie aus, wird direkt abgelehnt und eine Benachrichtigung gesendet.
- **Seite „Dienstrichtlinie"**: ein Untermenü in den Einstellungen, das den Hauptschalter des API-Dienstes, den Whitelist-Modus, die Vorgehensweise nach dem Stopp des Dienstes sowie den Schalter für die Gerätebesitzer-Übertragung zentral bündelt.
- **Temporäre Aktivierung ohne Kontolöschung**: entfällt die Upstream-Vorgabe, zuerst alle Konten zu entfernen. Siehe den eigenen Abschnitt weiter unten.
- **Themenseite**: vier Stufen für den Dunkelmodus (System folgen / Hell / Dunkel / reines Schwarz AMOLED) plus Schalter für dynamische Farbentnahme; der Dunkelmodus nutzt ein klappbares Auswahlfeld.
- **Einstellungsseite**: neuer Eintrag für den Bereich „Verwaltung", **ohne Aktivierung wird der gesamte Bereich ausgeblendet**.
- **Fehlermeldungen bei der Aktivierung**: englische Rohfehler werden auf eine chinesische Ursache + Handlungsempfehlung + kopierbare Diagnoseinformationen abgebildet.

### Debug-Protokoll

Einheitlicher TAG `NeoDhizuku` mit Kategorie-Untertags (`Nav` / `Theme` / `Perm` / `Home` / `Apps` / `Daemon` / `Server` / `Activate` / `UI` / `Boot`). Schalter `NeoDhizuku.DebugLog`; ausgeschaltet herrscht völlige Stille und es entstehen keine Kosten für Zeichenkettenverkettung.

```bash
adb logcat -s NeoDhizuku:V              # alles
adb logcat -s NeoDhizuku:V | grep Nav   # nur Navigation
```

---

## 2. Hinweise und Verwendung

### Aktivierung

Wie beim Upstream: Shizuku, ADB und der Modus ohne Kontolöschung können die App zum Gerätebesitzer machen.
**Hinweis des Upstream beachten: Die Einrichtung wird auf einem Gerät ohne jegliche Konten empfohlen.** Sind bereits Konten vorhanden, müssen diese eventuell vorher entfernt werden.

Sobald die App Gerätebesitzer ist, können Drittanbieter-Apps über die Dhizuku-API die Nutzung der Gerätebesitzer-Rechte beantragen.

### Temporäre Aktivierung ohne Kontolöschung

Der Upstream empfiehlt die Einrichtung **ohne jegliche Konten**, andernfalls müssen sie vorher entfernt werden. Dieser Ansatz umgeht die Einschränkung.

**Prinzip**: In `DevicePolicyManagerService` wird die Kontoprüfung `hasIncompatibleAccountsOrNonAdb` nur innerhalb des `isAdb`-Zweigs gelesen. Das Original geht über ADB (`dpm set-device-owner`), also `isAdb=true`, und alle drei Schranken werden geprüft. Dieser Ansatz ruft direkt mit **der eigenen uid der App** auf, also `isAdb=false`, und es wird nur `hasUserSetupCompleted` geprüft – **Konten werden überhaupt nicht gelesen**.

**Sieben Schritte**:

| Schritt | Ausführender | Inhalt |
|---|---|---|
| ① | Shell | `cmd role set-bypassing-role-qualification true` |
| ② | Shell | `cmd role add-role-holder --user 0 android.app.role.DEVICE_POLICY_MANAGEMENT <pkg>` |
| ③ | Shell | `settings put secure user_setup_complete 0` |
| ④ | App selbst | per Reflexion `forceUpdateUserSetupComplete(0)`, um den Wert aus ③ in den Speicherzustand von DPM zu übernehmen |
| ⑤ | App selbst | per Reflexion `setActiveAdmin` |
| ⑥ | App selbst | per Reflexion `setDeviceOwner` |
| ⑦ | Shell | `settings put secure user_setup_complete 1` |

③ und ④ **müssen paarweise erfolgen**: Nur ③ wirkt nicht, der Speicherzustand ändert sich nicht und die Schranke bleibt geschlossen. Der Parameter von ④ ist eine userId, kein Statuswert; er kopiert den echten Wert aus Settings bidirektional in den Speicher, daher darf ③ nicht entfallen.

**Drei Voraussetzungen**:

- Shizuku oder Root-Rechte (zum Ausführen der Shell-Befehle in ①③⑦; eine normale App erhält `WRITE_SECURE_SETTINGS` nicht)
- Android 13 (API 33) oder höher (die Rolle `DEVICE_POLICY_MANAGEMENT` wurde mit dieser Version eingeführt)
- noch nicht Gerätebesitzer sein

**Nebenwirkung**: Während der Wert auf 0 steht, funktionieren bei der Drei-Tasten-Navigation die Tasten Start und Letzte Apps nicht (Zurück funktioniert, Gestensteuerung ist nicht betroffen). Der Code stellt den Wert in einem `finally`-Block immer wieder her, das Gerät bleibt also nie im Zustand „Einrichtung unvollständig".

**Fehlersuche**: Liefert der Rollenbefehl `RC=0`, die Berechtigung bleibt aber DENIED, fehlt meistens im Manifest die Deklaration von `MANAGE_DEVICE_ADMINS` / `MANAGE_PROFILE_AND_DEVICE_OWNERS` – dies sind Berechtigungen der Stufe signature und müssen deklariert sein, damit das System sie bei der Rollenzuteilung mit gewährt. Die Seite gibt diesen Hinweis direkt aus.

Wurde der Einrichtungsassistent nicht wiederhergestellt, zeigt die Seite einen roten Hinweis und deaktiviert die ersten beiden Schritte; nur „Manuell wiederherstellen" bleibt verfügbar.

### Verwaltung der Berechtigungen

- Beim ersten Aufruf einer Drittanbieter-App erscheint die Autorisierungsanfrage; man kann erlauben, ablehnen oder zur Sperrliste hinzufügen.
- **Nach dem Eintrag in die Sperrliste werden Autorisierungsanfragen dieser App nicht mehr bearbeitet.**
- In der „App-Verwaltung" kann die Berechtigung jederzeit umgeschaltet werden; auf der Seite „Sperrliste" lässt sich die Sperre aufheben.
- Ist der **Whitelist-Modus** aktiv, dürfen nur Apps auf der Whitelist riskante Schnittstellen aufrufen.

### Verwendung der Schalter

`NeoDhizuku.kt` bearbeiten:

```kotlin
const val UseNeoDhizuku: Boolean = true   // Hauptschalter, false = vollständig zurück zum Upstream-Verhalten
const val UseThemePage: Boolean = true    // Teilfunktion: Themenseite
const val DebugLog: Boolean = true        // Teilfunktion: Debug-Protokoll
```

Empfehlung zur Fehlersuche: zuerst mit `UseNeoDhizuku = false` eine Referenzversion bauen, dann auf `true` umstellen und vergleichen; verursacht eine Teilfunktion Probleme, nur diese abschalten, um sie einzugrenzen.

### Dienstrichtlinie

Einstellungen → „Dienstrichtlinie", drei Bereiche:

**① API-Dienst**: Hauptschalter (anderen Apps die Nutzung des API-Dienstes erlauben) und Whitelist-Modus (nur Apps auf der Whitelist die Nutzung des API-Dienstes erlauben).

**② Vorgehensweise nach dem Stopp des Dienstes** (klappbares Auswahlfeld, wirkt nur, nachdem der Dienst gestoppt wurde):

| Option | Verhalten |
|---|---|
| Gefährliche Dienste ablehnen und Fehler zurückgeben | Der Dienst bleibt auffindbar, alle Nicht-Lesezugriffe werden abgelehnt |
| Gefährliche Dienste ablehnen und Erfolg zurückgeben | Durchgelassen, aber nicht ausgeführt; es wird nur Erfolg gemeldet |
| Dienst vollständig ablehnen und Fehler zurückgeben | Gibt null zurück, andere Apps finden den Dienst nicht |
| Dienst vollständig ablehnen und Erfolg zurückgeben | Gibt ein leeres Ergebnis zurück |
| Nichts unternehmen | Die Autorisierungsanfrage bleibt hängen, der Client wartet endlos |

„Nichts unternehmen" wird umgesetzt, indem der **Rückruf nicht aufgerufen** wird, nicht indem ein Thread blockiert wird; die Bearbeitung anderer Aufrufe bleibt also unberührt.

**③ Gerätebesitzer-Übertragung**: der oben unter „Abfangen der Gerätebesitzer-Übertragung" beschriebene Schalter.

### Bekannte Einschränkungen

- Die schwebende untere Leiste wurde nicht in eine wischbare Pager-Struktur umgewandelt; Seiten lassen sich nur per Tippen wechseln.
- Für das Design wird das native `dynamicColorScheme` von Material3 verwendet (Systemunterstützung ab Android 12+); es wird keine Palettenbibliothek eines Drittanbieters eingesetzt.
- **Bei Koexistenz mit dem offiziellen Dhizuku**: Clients, die die Authority des offiziellen Pakets hart kodieren (z. B. amarok, 爱玩机工具箱), verbinden sich mit der offiziellen App statt mit dieser – es tritt das Phänomen „offizieller Dialog erscheint, diese App reagiert nicht" auf. Die Authority eines ContentProvider ist global eindeutig und lässt sich nicht an sich reißen; der Ausweg ist, die offizielle App zu deinstallieren und die Variante `neoCompat` oder `official` zu verwenden.
- **Die Aktivierung ohne Kontolöschung setzt Android 13+ voraus**; auf niedrigeren Versionen zeigt die Seite „nicht unterstützt" und blendet die Schritte aus.
- Shizuku API 14 wird `newProcess` entfernen; dann muss die in diesem Aktivierungspfad darauf beruhende Shell-Ausführung auf einen User Service umgestellt werden.

---

## 3. Erstellen

### Voraussetzungen

Android Studio **Quail 4 (2026.1.4)** oder höher (Mindestanforderung von AGP 9.4.1), JDK 21.
Im SDK Manager müssen SDK Platform 37, Build Tools 37.0.0 und NDK 29.0.14206865 installiert sein.

### Erstellen

Das Projekt hat drei Varianten (product flavor). `namespace` und Quellpaketname sind auf `com.neodhizuku.more` festgelegt;
nur `applicationId` und die Authorities des Providers unterscheiden sich je Variante:

| Variante | applicationId | Registriert offizielle Authority | Mit offiziellem Dhizuku koexistierbar | Kompatibel mit Clients, die das offizielle Paket hart kodieren |
|---|---|---|---|---|
| `neo` | `com.neodhizuku.more` | Nein | **Ja** | Nein |
| `neoCompat` | `com.neodhizuku.more` | Ja | Nein | **Ja** |
| `official` | `com.rosan.dhizuku` | – ist sie selbst | Nein (gleiches Paket) | **Ja** |

`neo` und `neoCompat` haben dieselbe applicationId, es kann also nur eine von beiden installiert werden – zum Vergleich kann man aber beide bauen.

```
./gradlew assembleNeoDebug        Koexistenz-Variante (für den Alltag empfohlen)
./gradlew assembleNeoCompatDebug  Übernahme-Variante (vorher offizielles Dhizuku deinstallieren)
./gradlew assembleOfficialDebug   Variante mit offiziellem Paket (vorher offizielles Dhizuku deinstallieren)

./gradlew assembleBothDebug       baut neo + neoCompat in einem Durchgang
./gradlew assembleAllDebug        baut alle drei in einem Durchgang
```

Für Release-Builds `Debug` durch `Release` ersetzen (es gibt außerdem `assembleAllRelease` für alle drei).

> **Wichtig**: Der Gerätebesitzer kann immer nur zu einem Paket gehören. Sowohl `neoCompat` als auch `official`
> erfordern, dass **das offizielle Dhizuku zuerst deinstalliert** wird, sonst kollidiert die Authority des Providers
> und die Installation schlägt fehl (`INSTALL_FAILED_CONFLICTING_PROVIDER`). Danach muss erneut aktiviert werden.

Im Repository ist standardmäßig eine Debug-Signierung hinterlegt (`keystore/`); vor der Weitergabe durch die eigene Signierkonfiguration ersetzen.

---

## 4. Weitere Planung (eine Release-Version wird nach Abschluss und Tests offiziell veröffentlicht)

- [-] **Umbau des Autorisierungseinstiegs**: dafür sorgen, dass alle Drittanbieter-Apps zuverlässig eine Autorisierungsanfrage stellen können. Derzeit zeigen manche Clients (überwiegend solche, die von der alten API abhängen) weder einen Dialog an noch senden sie eine Anfrage; der versuchte serverseitige Übernahme-Ansatz war in Tests wirkungslos, daher muss zuerst der tatsächliche Fehlerpunkt gefunden werden (ob der Client `requestPermission` aufruft, ob der Listener den Rückruf erhält, ob die Benachrichtigung wirklich gesendet wird), bevor neu entworfen wird.
- [√] ~~Aktivierung als Gerätebesitzer ohne Kontolöschung~~ — umgesetzt (siehe „Temporäre Aktivierung ohne Kontolöschung")
- [-] Tatsächliche Ausnutzbarkeit der „selbst gemeldeten UID" am Autorisierungseinstieg mit einer Demo praktisch prüfen (statisch besteht eine Angriffsfläche, die tatsächliche Auswirkung ist unbestätigt).
- [-] Anzeige des Berechtigungsumfangs im Autorisierungsdialog.
- [-] Konfigurationseintrag für delegierte Bereiche (delegated scopes) — von der API unterstützt, aber ohne Eintrag in der Oberfläche.

---

## Open-Source-Lizenz

Dieses Projekt ist ein abgeleitetes Werk von Dhizuku (GPL-3.0) und wird insgesamt weiterhin unter GPL-3.0 verbreitet; die vollständige Lizenz liegt im Wurzelverzeichnis des Repositorys als `LICENSE`.

Dieses Projekt basiert auf [Dhizuku](https://github.com/iamr0s/Dhizuku) und ändert daran Paketname, Autorisierungs- und Aktivierungsablauf, Sicherheitshärtung und Oberflächenimplementierung. Die vollständige Änderungshistorie findet sich in den Commits des Repositorys.

| Upstream-Projekt | Lizenz | Anmerkung |
|---|---|---|
| Dhizuku | GPL-3.0 | direkter Upstream; dieses Projekt übernimmt dessen Lizenz |
| Dhizuku-API | MIT | als lokales Submodul eingebunden; Urheberrechts- und Lizenzvermerke bleiben in `Dhizuku-API/LICENSE` erhalten |
| KernelSU | GPL-3.0-or-later (`kernel/` ist GPL-2.0-only) | liefert nur Ideen für Oberfläche und Interaktion, kein Code wiederverwendet |
| Shizuku API | Apache-2.0 | Maven-Abhängigkeit |

**Dank**: Dhizuku (iamr0s), Dhizuku-API (R0S), KernelSU, Shizuku (RikkaApps) sowie alle, die upstream Code und Übersetzungen beigetragen haben.
