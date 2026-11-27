# VocabScan bauen und veröffentlichen

Das Repository enthält das Android-Projekt und die vorhandene Dokumentation.
Die GitHub-Historie und `.github/workflows` bleiben erhalten. Lokale Sicherungen
unter `.git-backup`, Signierschlüssel, Zugangsdaten und Build-Ausgaben gehören
nicht in Git. Der originale Keystore und vorhandene Release-Pakete bleiben lokal erhalten.

## Lokal prüfen

Voraussetzungen: JDK 17 und Android SDK mit `platforms;android-36` sowie
`build-tools;36.0.0`. `ANDROID_HOME` oder eine lokale, nicht eingecheckte
`local.properties` muss auf das SDK zeigen. Immer den eingecheckten Gradle Wrapper verwenden.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
.\gradlew.bat assembleRelease -PunsignedRelease=true --no-daemon --no-build-cache
```

Unter Linux/macOS entsprechend `./gradlew` verwenden. Optional auf einem laufenden
Emulator oder Gerät: `./gradlew connectedDebugAndroidTest`.

- Debug: `app/build/outputs/apk/debug/app-debug.apk`, Application-ID
  `de.vocabscan.debug`, Versionssuffix `-dev`, App-Name **VocabScan DEV**.
  Die automatisch erzeugte Android-Debug-Signatur ist nur für Entwicklung gedacht.
  Automatische und manuelle Release-Updateprüfungen sind deaktiviert.
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`, Application-ID
  `de.vocabscan`, ohne Versionssuffix, App-Name **VocabScan**, nicht debuggable.
  `-PunsignedRelease=true` ist erforderlich. Gradle liest keine lokalen
  Signierpasswörter oder Keystores; die APK wird erst in GitHub Actions signiert.

Beide Varianten sind gleichzeitig installierbar und haben getrennte App-Daten und
FileProvider. Das bestehende Release wird dadurch nicht ersetzt.

## GitHub Actions und Signierung

Die vorhandene **Android CI** prüft Pull Requests auf `main` mit Unit-Tests,
`lintDebug` und `assembleDebug` und lädt die Debug-APK und Prüfberichte als Artefakte hoch.

Die vorhandene **Android Release** wird manuell über `workflow_dispatch` auf `main`
gestartet. Der Build-Job baut die unsignierte APK mit `-PunsignedRelease=true` ohne
Signierschlüssel. Der getrennte Signierjob richtet sie mit `zipalign` aus, signiert
sie mit den bereits eingerichteten Repository Secrets `KEYSTORE_BASE64`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` und `KEY_PASSWORD`, prüft das Zertifikat und erzeugt
das Artefakt `vocabScan-release` mit `vocabscan.apk` und `vocabscan.apk.sha256`.
Der Workflow veröffentlicht selbst kein GitHub Release.

Den **bisherigen Release-Schlüssel** weiterhin verwenden. Keinen Ersatzschlüssel
erzeugen: Ein anderes Zertifikat verhindert Updates bestehender Installationen.
Der originale Keystore `.signing/vocabscan-release.p12` und die archivierte lokale
Zugangskonfiguration `.signing/keystore.properties.backup` bleiben ausgeschlossen.
Sie werden vom Build nicht benötigt. Keystore und Passwort weiterhin sicher und
getrennt sichern; keine dieser Dateien oder ihrer Inhalte hochladen.

## Eine neue Version veröffentlichen

1. `versionCode` in `app/build.gradle.kts` strikt erhöhen und `versionName`
   aktualisieren. Der aktuelle Projektstand ist 18 / 1.0.2; für das nächste Release
   gegenüber bereits installierten Versionen wieder erhöhen.
2. `app/src/main/assets/changelog.json` mit denselben Versionswerten und kurzen
   deutschen Änderungen aktualisieren. Die Datei liefert „Was ist neu“ ohne Netzwerk.
3. Änderungen über Git einchecken und prüfen. **Android Release** auf dem gewünschten
   Stand von `main` starten und das signierte Artefakt herunterladen. Die signierte
   APK vor Veröffentlichung mit `apksigner verify --verbose --print-certs` prüfen
   und das Zertifikat mit einer bisherigen Release-APK vergleichen.
4. Auf einem Testgerät Neuinstallation und Update über eine ältere, mit demselben
   Schlüssel signierte Release-Version prüfen. App-Daten müssen erhalten bleiben.
5. Ein reguläres GitHub Release mit passendem numerischem Tag anlegen, z. B.
   `v1.0.3` für `1.0.3`. Änderungen in die Beschreibung schreiben und exakt
   `vocabscan.apk` sowie die Prüfsumme aus dem signierten Artefakt anhängen.
   Als „Latest“ veröffentlichen, nicht als Entwurf oder Vorabversion.

APKs sind Release-Assets, keine Quelldateien. Lokale Pakete unter `releases/` und
wichtige Sicherungen werden weder eingecheckt noch gelöscht.

## Update-Kompatibilität prüfen

Die Release-App liest weiterhin ausschließlich den öffentlichen Endpunkt
<https://api.github.com/repos/KingMezzo/vocabScan/releases/latest>, ohne Token.
Die bestehende `version.json` bleibt als Übergang für ältere Installationen erhalten;
aktuelle Apps verwenden GitHub Releases und rufen sie nicht ab.

- Updates benötigen die gleiche Application-ID `de.vocabscan`, das gleiche
  Signierzertifikat, einen höheren Android-`versionCode` und einen neueren numerischen
  Release-Tag. `vocabscan.apk` muss vollständig hochgeladen sein.
- Automatische Prüfungen sind in Release standardmäßig aktiv, in den Einstellungen
  abschaltbar und auf höchstens einen Versuch pro 24 Stunden begrenzt.
  Manuelle Prüfung bleibt auch bei abgeschalteter Automatik verfügbar.
- Herunterladen erfolgt direkt in der App mit Fortschritt, Abbruch und Fehlerzustand.
  Paketname, Version, Zertifikat, Größe und vorhandener SHA-256-Digest werden geprüft.
- Installation erfordert Androids Bestätigung und gegebenenfalls die Freigabe für
  unbekannte Quellen. Der Workflow und die App öffnen dafür keine GitHub-/Browserseite.
- Hell-/Dunkelmodus, große Schrift, Downloadfehler, verweigerte Installationsfreigabe
  und „Was ist neu“ nach einem Update auf dem Gerät prüfen.
