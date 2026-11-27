# VocabScan – Projektregeln

## Stack
Android, Kotlin, Jetpack Compose, Material 3. Keine neuen Bibliotheken ohne Rückfrage.

## Design-Regeln (verbindlich für jeden Screen)
1. Pro Screen genau EINE Hauptaktion (ein Primär-Button). Alles andere ist sekundär (Text-Button, Menü, Bottom Sheet).
2. Keine überladenen Screens: Maximal 3 Informationsblöcke sichtbar, ohne zu scrollen. Seltene Funktionen ins Overflow-Menü.
3. Keine Dropdowns oder Filter-Chips, wo echte Navigation sinnvoll ist (z. B. Ordner = eigene Ansicht, kein Filter).
4. Höchstens ein FAB pro Screen, er darf nie Inhalte verdecken (Bottom-Padding in Listen).
5. Zahlen und Begriffe konsistent: nie zwei verschiedene Zählweisen gleichzeitig zeigen (z. B. Wörter vs. Karten).
6. Abstände im 8-dp-Raster, mindestens 48 dp Touch-Fläche, nur Material-3-Farben und -Typografie aus dem Theme, keine hartcodierten Werte.
7. Jeder Screen braucht gestaltete Zustände: leer, lädt, Fehler.
8. Dark Mode und Light Mode müssen beide funktionieren, Kontraste prüfen.
9. Bestehende Komponenten wiederverwenden (ui/components), keine Duplikate bauen.
10. Sprachauswahl als dezente Auswahlfelder mit ausgeschriebenem Sprachnamen und eindeutigem Auswahlzustand gestalten, nicht als gefüllte Aktionsbuttons. Flaggen ergänzen den Namen; ihre Originalfarben bleiben auf die Grafik begrenzt, Bedienelemente verwenden das Theme.
11. Import- und Exportmethoden in Bibliothek und Listen unter „Importieren“ bzw. „Liste exportieren“ bündeln; die Methode wird anschließend in einem einheitlichen Auswahlblatt gewählt. Der Einstellungsbildschirm bleibt davon ausgenommen und unverändert.
12. Sprachlisten haben zwei unabhängig wählbare Sprachen, auch gleiche Kombinationen sind erlaubt. Deutsch ist keine feste Gegenseite. Bei gleicher Sprache die Seiten als Begriff und Erklärung / Synonym unterscheiden; Lernrichtung, Aussprache, Scan und Datenaustausch berücksichtigen beide Seiten.
13. Updates direkt in der App herunterladen, mit Fortschrittsbalken im Theme und gestalteten Fehler-/Installationszuständen. Keine GitHub- oder Browser-Schaltfläche für Updates. Automatische Release-Prüfung in den Einstellungen abschaltbar; manuelle Prüfung bleibt verfügbar. Androids Installationsbestätigung und nötige Quellenfreigabe berücksichtigen.

## Navigation
Bottom Navigation: Lernen, Bibliothek, Statistik, Einstellungen. Neue Screens müssen sich hier einordnen.

## Arbeitsweise
- Vor einem neuen Screen kurz beschreiben, was oben, in der Mitte und unten steht und was die Hauptaktion ist.
- Compose-Previews für jeden neuen Screen (hell, dunkel, leer).
- Nach der Änderung kurz prüfen: Verletzt der Screen eine der Design-Regeln oben?
- Wenn ich einen Designfehler kritisiere, ergänze die passende Regel in dieser Datei.
