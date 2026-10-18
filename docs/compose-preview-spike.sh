#!/usr/bin/env bash
# Spike für Task 27a: Läuft der Kotlin-Compiler samt Compose-Plugin als eigener Prozess auf einer echten JVM?
# Gedacht für die Termux-Shell der App (Prefix com.codeforge.app). Auf einem Rechner lassen sich die Pfade per
# Umgebungsvariablen überschreiben. Aufruf:  scripts/compose-preview-spike.sh [compose|plain]
#   plain   = Datei ohne Compose (prüft nur Compiler/JVM)
#   compose = @Composable-Datei mit Compose-Plugin und den Jars aus Task 26 (Standard)
set -u
PREFIX="${PREFIX:-/data/data/com.codeforge.app/files/usr}"
KOTLIN_HOME="${KOTLIN_HOME:-$PREFIX/opt/kotlin}"
RUNTIME_DIR="${RUNTIME_DIR:-/data/data/com.codeforge.app/files/compiler-runtime}"
ANDROID_JAR="${ANDROID_JAR:-}"
MODE="${1:-compose}"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
fail=0
say() { printf '%s\n' "$*"; }
bad() { say "FEHLER: $*"; fail=1; }

say "== Umgebung =="
command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -v '^Picked up' | head -1 || bad "java nicht gefunden (pkg install openjdk-21)"
[ -x "$KOTLIN_HOME/bin/kotlinc" ] || { bad "kotlinc fehlt unter $KOTLIN_HOME (pkg install kotlin)"; exit 1; }
say "kotlinc: $(JAVA_TOOL_OPTIONS= "$KOTLIN_HOME/bin/kotlinc" -version 2>&1 | grep -v '^Picked up' | head -1)"
PLUGIN="$KOTLIN_HOME/lib/compose-compiler-plugin.jar"
[ -f "$PLUGIN" ] && say "Compose-Plugin: $PLUGIN" || bad "compose-compiler-plugin.jar fehlt in $KOTLIN_HOME/lib"
mem=$(awk '/MemAvailable/ {printf "%d", $2/1024}' /proc/meminfo 2>/dev/null || true)
say "Freier Speicher: ${mem:-unbekannt} MB"

CP="$KOTLIN_HOME/lib/kotlin-stdlib.jar"
args=(-no-stdlib -no-reflect)
if [ "$MODE" = plain ]; then
  cat > "$WORK/Sample.kt" <<'KT'
fun spikePlain(): String = "ok"
KT
else
  [ -d "$RUNTIME_DIR" ] || bad "RUNTIME_DIR fehlt: $RUNTIME_DIR (Task 26 liefert das Jar-Bundle)"
  for j in "$RUNTIME_DIR"/*.jar; do
    [ -f "$j" ] || continue
    case "$(basename "$j")" in kotlin-compose-compiler-plugin-embeddable.jar) continue ;; esac
    CP="$CP:$j"
  done
  if [ -z "$ANDROID_JAR" ]; then
    ANDROID_JAR="$(ls -d "${ANDROID_HOME:-$HOME/android-sdk}"/platforms/android-*/android.jar 2>/dev/null | sort -V | tail -1)"
  fi
  if [ -n "$ANDROID_JAR" ] && [ -f "$ANDROID_JAR" ]; then CP="$CP:$ANDROID_JAR"; say "android.jar: $ANDROID_JAR"; else bad "android.jar nicht gefunden (Plattform im SDK-Manager installieren oder ANDROID_JAR setzen)"; fi
  args+=("-Xplugin=$PLUGIN")
  cat > "$WORK/Sample.kt" <<'KT'
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun SpikePreview() {
    Text("Hallo")
}
KT
fi
[ "$fail" = 0 ] || { say "Abbruch wegen fehlender Voraussetzungen."; exit 1; }

say "== Kompilieren ($MODE) =="
start=$(date +%s)
JAVA_OPTS="${JAVA_OPTS:--Xmx768m}" JAVA_TOOL_OPTIONS= "$KOTLIN_HOME/bin/kotlinc" "${args[@]}" -classpath "$CP" "$WORK/Sample.kt" -d "$WORK/out" >"$WORK/log.txt" 2>&1
rc=$?
secs=$(( $(date +%s) - start ))
grep -v '^Picked up' "$WORK/log.txt" | head -15
say "Exit-Code: $rc · Dauer: ${secs}s"
if [ "$rc" -ne 0 ]; then
  if grep -q "Compose Runtime to be on the class path" "$WORK/log.txt"; then say "-> Compose-Runtime-Jars fehlen im Klassenpfad (Task 26)."; fi
  if grep -q "unresolved reference 'Composable'" "$WORK/log.txt"; then say "-> androidx.compose.runtime fehlt im Klassenpfad (Task 26: Jars unvollständig?)."; fi
  say "ERGEBNIS: FEHLGESCHLAGEN"; exit 1
fi
cls="$(find "$WORK/out" -name 'SampleKt.class' | head -1)"
[ -n "$cls" ] || { say "ERGEBNIS: FEHLGESCHLAGEN (keine SampleKt.class)"; exit 1; }
if [ "$MODE" = compose ] && command -v javap >/dev/null 2>&1; then
  sig="$(javap -cp "$WORK/out" SampleKt 2>/dev/null | grep SpikePreview)"
  say "Signatur: $sig"
  case "$sig" in *Composer*) say "-> Compose-Plugin hat die Funktion umgeschrieben (Composer-Parameter vorhanden)." ;; *) say "-> ACHTUNG: kein Composer-Parameter, Plugin wirkungslos?"; exit 1 ;; esac
fi
say "ERGEBNIS: OK (${secs}s)"
