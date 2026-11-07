# NeoDhizuku

Eine Weiterentwicklung von [Dhizuku](https://github.com/iamr0s/Dhizuku), die Gerätebesitzer-Rechte (DeviceOwner) kontrolliert mit anderen Anwendungen teilt.

[简体中文](../README.md) | [English](README.en.md) | [日本語](README.ja.md) | [한국어](README.ko.md) | [繁體中文](README.zh-rTW.md)

---

## ⚠️ Unfertige Warnung

**Diese Version befindet sich in der Entwicklung – nicht auf dem Alltagsgerät oder in Produktion einsetzen.**

Der Paketname lautet jetzt `com.neodhizuku.more`. Die App ist **nicht zusammen mit der offiziellen Version installierbar**, und weil sie außerdem die offizielle ContentProvider-Authority registriert, **muss das offizielle Dhizuku vorher deinstalliert werden**. Die Daten sind nicht kompatibel – vor der Migration sichern.

---

## Funktionen

### Aktivierung

- **Aktivierung ohne Konto-Entfernung**: die Vorgabe des Originals „zuerst alle Konten entfernen" entfällt (Shizuku / root + Android 13+ erforderlich)
- **Geräteverwalter-Modus**: in den Systemeinstellungen vergebbar, mit der niedrigsten Hürde – Drittanbieter-Clients können sich jedoch nicht initialisieren (siehe Bekannte Einschränkungen)
- **Fehlermeldungen bei der Aktivierung**: englische Originalfehler werden auf Ursache + Handlungsempfehlung abgebildet, Diagnoseinformationen mit einem Tipp kopierbar

### Sicherheit

- **Die Autorisierung vertraut keiner vom Client gemeldeten UID mehr**: der Server ermittelt die Identität per `Binder.getCallingUid()` und stellt ein Einmal-Token aus (10 Minuten gültig)
- **Blockieren der Gerätebesitzer-Übertragung**: standardmäßig aus; bei Ablehnung wird benachrichtigt
- **Zwangbestätigung beim Löschen**: `wipeData` wird immer abgefangen und erfordert zwei Warnstufen plus eine Haftungs-Checkbox; die Freigabe gilt einmalig für 5 Minuten
- Provider-Authentifizierung, Audit von `remoteProcess`-Befehlen, Prüfung der eigenen Signatur beim Start

### Oberfläche

An das Layout des KernelSU-Managers angelehnt, mit Material3 umgesetzt (**kein miuix / coui**):

- **Schwebende Navigationsleiste**: von vier Seiten gemeinsam genutzt, Symbol + Text, keine Emojis; Seiten lassen sich wischen oder über die Leiste antippen; auf Unterseiten ausgeblendet
- **Statuskarte auf der Startseite**: nach der Aktivierung „Statuskarte links + zwei kleine Karten rechts" – aktiv + Identitätstyp + Version, oben rechts die Zahl autorisierter Apps, unten rechts die Laufzeit des Dienstes
- **App-Verwaltung**: alle relevanten Apps in einer Liste, Schalter zeigen den Autorisierungsstatus direkt; Apps, die ohne deklarierte Berechtigung aufgerufen haben, werden automatisch erfasst
- **Eigene Blacklist-Seite**: Einträge einzeln freigeben
- **Autorisierungsdialog**: ohne Fortschrittsbalken; „Erlauben" hellgrün, „Ablehnen" rot hervorgehoben, „Blacklist" schwarz; der Countdown steht klein unter der Buttonzeile
- **Aufrufprotokoll**: hält fest, welche Funktionen welche App genutzt hat, **in verständlicher Sprache und mit Argumenten**; 30 Tage gespeichert, jeder Eintrag enthält einen Ausschnitt des Serverlogs; zusätzlich ein Reiter „Laufzeitprotokoll"
- **Dienst starten/stoppen**: nach dem Stopp zeigt die Laufzeitkarte ganz in Rot „Dienst verweigert"; Autorisierungen und Aufrufprotokoll bleiben einsehbar
- **Themenseite**: vier Dunkelmodus-Stufen (System folgen / Hell / Dunkel / AMOLED-Schwarz) + dynamische Farben; bei ausgeschalteter dynamischer Farbe steht eine eingebaute Farbpalette bereit, außerdem ein Schalter für die Flüssigglas-Leiste
- **Über-Seite**: animiertes Symbol, Versions- und Geräteinformationen, GitHub-/QQ-Links, Update-Prüfung (mit Schalter für Vorabversionen)

### Dienst (Einstellungen → Dienst)

| Bereich | Inhalt |
|---|---|
| API-Dienst | Hauptschalter, Whitelist-Modus, alte kompatible Versionsnummer verwenden (standardmäßig an) |
| Verhalten bei ausgeschaltetem Dienst | Auswahl mit fünf Optionen; wirkt nur, wenn der Dienst gestoppt ist |
| Gerätebesitzer-Übertragung | Schalter für das oben genannte Blockieren |

„Alte kompatible Versionsnummer verwenden" bestimmt den an Clients zurückgegebenen Versionsnamen: an (Standard) liefert `2.12.0` des Originals – manche Clients prüfen die Version; aus liefert den eigenen Versionsnamen dieses Projekts.

Verhalten bei ausgeschaltetem Dienst:

| Option | Verhalten |
|---|---|
| Gefährlichen Dienst ablehnen und Fehler melden | Dienst ist auffindbar, alle nicht lesenden Operationen werden abgelehnt |
| Gefährlichen Dienst ablehnen und Erfolg melden | durchgelassen, aber nicht ausgeführt, nur Erfolg zurückgegeben |
| Dienst vollständig ablehnen und Fehler melden | gibt null zurück, andere Apps finden den Dienst nicht |
| Dienst vollständig ablehnen und Erfolg melden | gibt ein leeres Ergebnis zurück |
| Nichts tun | Anfrage wird angehalten, der Client wartet unbegrenzt |

„Nichts tun" wird durch **keinen Rückruf** umgesetzt, nicht durch Blockieren eines Threads, und beeinträchtigt daher keine anderen Aufrufe.

---

## Verwendung

### Aktivierung

Unterstützt werden Shizuku, ADB und der Modus ohne Konto-Entfernung. Sobald die App Gerätebesitzer ist, können Drittanbieter-Apps über die Dhizuku-API Gerätebesitzer-Funktionen anfordern.

### Aktivierung ohne Konto-Entfernung

Das Original empfiehlt die Einrichtung, **wenn keine Konten auf dem Gerät sind**; diese Variante umgeht das, indem sie mit der eigenen uid der App aufruft statt über den ADB-Zweig – das System prüft dann nur `hasUserSetupCompleted` und liest keine Konten.

**Voraussetzungen**: Shizuku oder Root-Rechte, Android 13 (API 33) oder höher, noch kein Gerätebesitzer.

**Nebenwirkung**: solange der Wert auf 0 steht, funktionieren bei der Drei-Tasten-Navigation Home und „Letzte Apps" nicht (Zurück bleibt nutzbar, Gestensteuerung ist nicht betroffen). Der Code stellt den Wert im `finally`-Block in jedem Fall wieder her.

### Autorisierungsverwaltung

- Beim ersten Aufruf einer Drittanbieter-App erscheint die Autorisierungsanfrage: erlauben, ablehnen oder auf die Blacklist setzen
- **Nach dem Blacklist-Eintrag werden Anfragen dieser App nicht mehr bearbeitet**
- In der App-Verwaltung lässt sich die Autorisierung jederzeit umschalten; die Blacklist-Seite hebt sie auf
- Im **Whitelist-Modus** dürfen nur aufgelistete Apps kritische Schnittstellen aufrufen

---

## Bekannte Einschränkungen

- Die Flüssigglas-Leiste ist eine halbtransparente Überlagerung, keine echte Hintergrundunschärfe (Compose bietet dafür keine native API)
- **Im Geräteverwalter-Modus können Drittanbieter-Apps sich nicht initialisieren**: `Dhizuku.init()` des Clients akzeptiert nur Gerätebesitzer und Profilbesitzer, ein Geräteverwalter scheitert sofort. Dieser Code läuft im Prozess der Drittanbieter-App und lässt sich von hier nicht beeinflussen
- Die Aktivierung ohne Konto-Entfernung erfordert Android 13+
- Bei eingeschaltetem USB-Debugging lassen sich manche Systemeinstellungsseiten nicht öffnen; nach dem Abschalten von adb funktionieren sie wieder
- Shizuku API 14 entfernt `newProcess`; die Aktivierung ohne Konto-Entfernung muss dann auf User Service umgestellt werden

---

## Build

Android Studio **Quail 4 (2026.1.4)** oder neuer (Mindestanforderung von AGP 9.4.1), JDK 21. Installiert sein müssen SDK Platform 37, Build Tools 37.0.0, NDK 29.0.14206865.

```
./gradlew assembleDebug      Debug-Build
./gradlew assembleRelease    Release-Build
```

Die Ausgabe liegt in `app/build/outputs/apk/debug/`.

Der Paketname ist auf `com.neodhizuku.more` festgelegt, zusätzlich wird die offizielle Authority `com.rosan.dhizuku.server.provider` registriert, damit Clients mit hartcodiertem offiziellem Paketnamen (amarok, 爱玩机工具箱 usw.) diese App erreichen.

Im Repository liegt ein Debug-Keystore (`keystore/`); vor der Veröffentlichung durch eine eigene Signierkonfiguration ersetzen.

---

## Weiteres Vorgehen

- Umbau des Autorisierungseinstiegs, damit alle Drittanbieter-Apps zuverlässig Autorisierung anfordern können
- Anzeige des Berechtigungsumfangs im Autorisierungsdialog
- Anpassung des Layouts für Tablets
- Eigener Benachrichtigungskanal für Löschvorgänge

---

## Lizenz

Dieses Projekt ist ein Derivat von Dhizuku (GPL-3.0) und wird insgesamt unter GPL-3.0 veröffentlicht; die vollständige Lizenz liegt als `LICENSE` im Wurzelverzeichnis.

| Ursprungsprojekt | Lizenz | Anmerkung |
|---|---|---|
| Dhizuku | GPL-3.0 | Direkter Vorgänger; dieses Projekt übernimmt dessen Lizenz |
| Dhizuku-API | MIT | Als lokales Submodul eingebunden |
| KernelSU | GPL-3.0-or-later | Nur Anregung für Oberfläche und Bedienung, kein Code übernommen |
| Shizuku API | Apache-2.0 | Maven-Abhängigkeit |

**Dank**: Dhizuku (iamr0s), Dhizuku-API (R0S), KernelSU, Shizuku (RikkaApps) sowie allen, die upstream Code oder Übersetzungen beigetragen haben.
