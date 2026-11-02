# DAP-Adapter-Test (Host-JDK)

Prüft `libs/debug-client/src/main/assets/dap/CodeForgeDapAdapter.java` gegen ein Beispielprogramm.

```
javac --release 17 -d out libs/debug-client/src/main/assets/dap/CodeForgeDapAdapter.java
mkdir -p t/demo && cp scripts/dap_adapter/App.java t/demo/ && javac -g -d t t/demo/App.java
python3 -I scripts/dap_adapter/test_launch.py   # Pfade im Skript anpassen (Arbeitsverzeichnis mit out/ und t/)
```
`test_attach.py`: Programm vorher mit `-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:5077` starten.
