# VocabScan: Informationsarchitektur ab 0.13.0

## Analyse und Screenplan

Die bisherige Startseite vereinte Lernrunde, Tagesziel, Gesamtzahlen, Listen, Ordnerfilter, Archiv und QR-Import. Navigation und Dialoge wurden zentral in `VocabApp.kt` gesteuert. `HomeScreen` in `Screens.kt` verwaltete Listenfilter lokal. Ein Ordner war lediglich `Deck.folder`, also ein Gruppierungsname ohne eigene Identität oder Möglichkeit, leer zu existieren.

Die neue Struktur:

```text
Lernen (Start)
  Jetzt lernen → bestehende Lernrunde → Abschluss
  ⋮ Lernrunde anpassen → bestehende Lernwahl mit allen Modi und Filtern
  Nichts fällig → Frei üben
  Keine aktiven Wörter → Zur Bibliothek
Bibliothek
  Ordner → eigene Ordneransicht → Liste → Vokabeldetails / Editor
  Lose Liste → dieselbe Listenansicht
  + → Neue Liste / Neuer Ordner / Per QR-Code importieren
  Suche → Ordner, Listen und Vokabeln einschließlich Archiv
  ⋮ → Archiv anzeigen; innerhalb eines Ordners auch Umbenennen / Löschen
Statistik → bisherige Auswertungen, Kalender und Wochenfortschritt
Einstellungen → Design, Tagesziel, Erinnerungen, Sicherung, CSV, Einführung
```

Auf Tablets bleibt die bestehende seitliche Navigation erhalten, jetzt mit denselben vier Zielen. Tab, geöffneter Ordner und Archivansicht überstehen eine Bildschirmdrehung. Zurück führt von einer Liste in ihren Ordner, vom Ordner zur Bibliotheksübersicht und danach zu Lernen.

## Hierarchie und Aktionen

- Lernen zeigt nur die tatsächlich nächste Runde: bis zu zehn fällige Karten. Keine zusätzliche Gesamtzahl, Listen, Importaktionen oder sichtbaren Filter. Die kompakte Lernkarte ist bei normaler Schrift etwa 136 dp hoch; sie darf für große Systemschrift wachsen. Das Tagesziel steht als schmale Anzeige darunter.
- Die Bibliothek zeigt erst Ordner, dann Listen ohne Ordner. Ordnerzahlen beziehen sich auf den sichtbaren Bereich, also aktive oder archivierte Listen. Der einzelne FAB öffnet die Auswahl; 112 dp Abstand unter der Liste halten den letzten Eintrag erreichbar.
- Die vorhandene Listenansicht behält Lernrichtungen, Scan, Bilder, Teilen, Filter und Wortübertragung. Ihre Lernbuttons sind sekundär; der FAB zum Hinzufügen einer Vokabel bleibt ihre Hauptaktion.
- Material-3-Farbpaare ersetzen die fest eingefärbte Lernkarte. Neue Karten verwenden höchstens zwei Textgrößen und Abstände im 8-dp-Raster. Leere Zustände nennen ausdrücklich die nächste Aktion.
- Vorschauen liegen in `LearnScreen.kt` und `LibraryScreen.kt`, einschließlich Dunkelmodus und großer Schrift.

## Daten und Kompatibilität

Room 8 → 9 ergänzt `folders(id, name, createdAt)` und den indizierten, optionalen Fremdschlüssel `decks.folderId`. Bisherige Namen werden unverändert übernommen; mehrere Listen mit demselben Namen teilen sich eine Ordner-ID. Die Migration ändert keine Wörter, Lernstände, Bewertungen, Scanbilder oder Archivmarkierungen. Alle älteren Migrationspfade bleiben registriert.

`folderId` definiert die Zuordnung. Das bisherige Textfeld `folder` bleibt als synchronisierter Kompatibilitätsname für ältere CSV-/ZIP-Leser erhalten. Anlegen, Verschieben, Umbenennen, Löschen und Wiederherstellen laufen in Transaktionen. Der Fremdschlüssel verwendet `SET NULL`, damit eine Ordnerlöschung keine Listen löscht. Die Repository-Operation leert gleichzeitig den Kompatibilitätsnamen, auch für archivierte Listen.

ZIP-Sicherungen enthalten Ordner separat und erhalten damit auch leere Ordner sowie stabile IDs. Alte Archive ohne Ordnerentitäten erzeugen diese beim Wiederherstellen aus den Namen. Das CSV-Format bleibt kompatibel und stellt die Ordnerzuordnung exportierter Listen wieder her; leere Ordner gehören zur vollständigen ZIP-Sicherung. QR-Importe werden im geöffneten Ordner angelegt. QR-Freigabe überträgt weiterhin nur Listeninhalte und Favoriten.

## Manuell prüfen

1. Bestehende Installation aktualisieren: Listen in Ordnern, Archiv, Favoriten, Lernstände und Originalseiten mit dem vorherigen Stand vergleichen.
2. Lernen: Anzeige mit weniger als zehn, mehr als zehn und keinen fälligen Karten; direkte Runde sowie beide Modi über das Menü; Tagesziel nach Bewertungen und Tageswechsel prüfen.
3. Bibliothek: leeren Ordner anlegen, Liste darin erstellen, bestehende Liste hinein- und herausverschieben, Ordner umbenennen und löschen. Auch archivierte Listen müssen erhalten bleiben. Zurück-Navigation und Drehung prüfen.
4. QR-Import aus der Übersicht und aus einem Ordner mit zwei echten Geräten testen, auch mehrteilige Codes. Dokumentenscanner und Bildimport auf einem physischen Gerät prüfen.
5. ZIP mit leerem Ordner exportieren und wiederherstellen; zusätzlich eine ältere ZIP- oder CSV-Datei importieren.
6. Hell-/Dunkelmodus, große Systemschrift, TalkBack, kleines Display und Tablet prüfen. Die letzte Liste muss über den FAB hinaus scrollbar bleiben.

Automatisierte Prüfungen umfassen Unit-Tests, Android Lint, Room-Migrationen ab Version 1, Ordnerlebenszyklus und ZIP-Kompatibilität sowie Compose-Abläufe für Bibliothek, Archiv, Lernmodi und Scannerkorrektur. Kamera und geräteübergreifendes Scannen benötigen die obigen manuellen Prüfungen.

## Verifiziert am 11. November 2026

`assembleDebug testDebugUnitTest lintDebug connectedDebugAndroidTest` ist erfolgreich durchgelaufen: 55 Unit-Tests und 55 Gerätetests auf Android 15 / API 35, keine fehlgeschlagenen oder übersprungenen Tests. Der endgültige Lint-Bericht enthält 0 Fehler und 29 Warnungen zu bestehenden Stellen und verfügbaren Abhängigkeitsupdates, unter anderem EXIF und Backup-Konfiguration. Ein früherer Bericht enthielt 19 Warnungen; der vollständige Lauf hat zusätzliche Versionshinweise ermittelt.

Geprüft wurden auch die Wiederherstellung der geöffneten Ordner-/Archivansicht, das Erstellen einer Liste im geöffneten Ordner und die Großschrift-Lernaktion bei 320 dp und Schriftfaktor 2. Telefonansichten in Hell/Dunkel, Systemschriftfaktor 2 und Tablet-Navigation wurden visuell kontrolliert. Die neuen Text-Farbpaare erreichen mindestens 5,5:1 Kontrast. Die Debug-APK liegt unter `app/build/outputs/apk/debug/app-debug.apk`.
