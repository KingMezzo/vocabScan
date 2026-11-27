# Allgemeine Lernkarten

Listen haben den Typ SPRACHE oder LERNKARTEN. Room-Version 10 ergänzt nur die
Spalte decks.type mit dem Standard SPRACHE. Historische Karten, Richtungs-IDs,
Lernstände, Bewertungen, Ordner und Scanverweise werden nicht umgeschrieben.

Die vorhandene Entität Vocabulary bleibt die gemeinsame Karte. Ihre gespeicherten
Spalten term und translation entsprechen bei Lernkarten Vorderseite (Begriff)
und Rückseite (Erklärung). Diese Spaltennamen bleiben für bestehende Datenbanken
und Sicherungsdateien stabil. note ist das optionale Beispiel / die Eselsbrücke,
tags enthält die Tags. Bei Lernkarten bleibt Deck.language leer; es wird weder
eine Sprache ausgewählt noch eine Android-Sprachausgabe erzeugt.

FSRS, Tagesziel und Statistik bleiben gemeinsam. Die beiden gespeicherten
Lernrichtungen sind 0 = Vorderseite → Rückseite und 1 = Rückseite → Vorderseite.
Sie werden getrennt terminiert. Gewusst entspricht GOOD, Nicht gewusst entspricht
AGAIN; diese Karte wird in derselben Runde erneut vorgelegt. In gemischten Runden
bleibt eine gewählte Übersetzungseingabe auf Sprachkarten beschränkt.

Die Bibliothek zählt physische Karten bzw. Vokabeln je Listentyp. Lernrunden und
Fortschrittsstatistik zählen Lernrichtungen, das Tagesziel zählt Bewertungen.
Kopieren und Zusammenführen sind nur bei gleichem Typ und gleichem Sprachwert
möglich. Ein Ordner darf beliebige Typen enthalten.

## Import und Sicherung

Unter „Weitere Listenaktionen“ → „Text oder CSV importieren“ kann eine Lernkartenliste
aus einer Datei oder eingefügtem Text befüllt werden. Pro Datensatz:
Vorderseite;Rückseite;optionale Notiz;optionale Tags.
Semikolon, Komma, Tabulator und senkrechter Strich sind wählbar. Keine Kopfzeile.
Felder mit Trennzeichen, Anführungszeichen oder Zeilenumbrüchen werden wie in CSV
in Anführungszeichen gesetzt; Anführungszeichen im Feld werden verdoppelt.
Beispiel:

    Vertrag;"Erklärung, auch
    über mehrere Zeilen";Angebot und Annahme;Recht

Der Import prüft alle Zeilen vor dem Speichern. Identische Vorder-/Rückseitenpaare
werden übersprungen. Exportierte VocabScan-CSV-Dateien enthalten zusätzlich den
Listentyp und werden über Einstellungen → CSV importieren eingelesen.
QR und ZIP führen den Typ ebenfalls mit. Alte CSV-, QR- und ZIP-Dateien ohne Typ
werden als SPRACHE gelesen. OCR-Fotoimporte bleiben bei Sprachlisten.

## Prüfung

Unit-Tests prüfen beide Lernrichtungen, gemischte Runden, Fälligkeit,
Archivfilter, gemeinsame Tagesstatistik sowie CSV- und Textformate.
Android-Integrationstests prüfen echte Room-Migrationen bis Version 10 und den
Erhalt der verbundenen Tabellen. Weitere Tests prüfen QR-/CSV-/ZIP-Rundläufe,
unverträgliche Transfers und den vollständigen Ablauf von der Listenerstellung
über mehrere Karten bis zur bewerteten Lernrunde.

Manuell prüfen:
- Vorhandene Sprachlisten nach einem App-Update öffnen und Lernstand vergleichen.
- Lernkartenliste in einem Ordner mit Sprachlisten erstellen, umbenennen und archivieren.
- Begriff, mehrzeilige Erklärung, Notiz und Tags speichern; „Weitere Karte hinzufügen“
  einschalten und mehrere Karten hintereinander erfassen.
- Vorwärts, rückwärts und gemischt üben; Gewusst und Nicht gewusst bewerten.
- Tagesziel und Statistik nach einer Runde beider Typen prüfen.
- UTF-8-CSV mit verschiedenen Trennzeichen, leeren/ungültigen Zeilen und mehrzeiligen
  Feldern testen; einen QR-Transfer zwischen zwei Geräten durchführen.
- Hell/Dunkel, große Systemschrift, Tastatur und Rotation prüfen.

Multiple Choice und frei wählbare Listenfarben sind nicht enthalten. Jeder Typ
erhält ein festes Symbol aus dem vorhandenen Material-Iconset.

## Verifiziert

64 Unit-Tests und der vollständige Android-Testlauf mit 72 Tests sind erfolgreich.
Nach den abschließenden Änderungen wurden sieben Lernkarten-UI-Tests erneut
ausgeführt, einschließlich des kompletten App-Ablaufs und eines zusätzlichen Tests
mit doppelter Schriftgröße. Android Lint: 0 Fehler, 29 bereits dokumentierte
Warnungen. Debug-App und Test-APK wurden erfolgreich gebaut. Helle Listenansicht,
dunkler Lernmodus und dunkler Editor mit großer Schrift wurden per Screenshot geprüft.

Die Designprüfung ergibt für die neuen Ansichten eine Hauptaktion, sekundäre
Zusatzaktionen, Theme-Farben und -Typografie, 8-dp-Abstände, scrollbare Formulare
und erreichbare Aktionen. Bestehende Sprachwahl-Komponenten und die Regeln in
AGENTS.md wurden nicht verändert.
