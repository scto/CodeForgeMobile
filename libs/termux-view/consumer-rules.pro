# libs:termux-view — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Terminal-View greift per Name auf Emulator-Klassen zu.
-keep class com.codeforge.view.** { *; }
-keep class com.codeforge.terminal.** { *; }
