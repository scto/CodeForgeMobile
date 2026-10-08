# libs:lsp-client — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# LSP-JSON-Nachrichten werden per Reflection (de)serialisiert.
-keepclassmembers class com.codeforge.libs.lsp_client.** { <fields>; }
