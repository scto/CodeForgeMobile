# agent-tool – Werkzeuge für den KI-Agenten

Sideload-Plugin (Berechtigungen `agent` + `shell`; der Store lehnt es ab). Es stellt dem Agenten `plugin_com_example_agent_tools_count_lines` und `…_find_todos` bereit (`kind: read`, laufen ohne Rückfrage). Platzhalter siehe `docs/extensions.md` (Abschnitt „Beiträge für den KI-Agenten“).

Ein MCP-Server wird analog deklariert: `"mcpServers": [{ "name": "docs", "url": "https://…/mcp" }]` oder `{ "name": "local", "command": "node", "args": ["${pluginDir}/server.js"] }`; er bleibt bis zur Freigabe im Agent-Panel unvertraut.

## Bauen und testen

```bash
python3 templates/build.py agent-tool   # → templates/build/agent-tool.zip
```

ZIP auf das Gerät kopieren, in *Plugins* per Sideload installieren, im Agenten fragen: „Wie viele Zeilen hat README.md?“ bzw. „Welche TODOs gibt es?“ – die Werkzeugaufrufe erscheinen im Verlauf.
