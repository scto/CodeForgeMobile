# feature:editor — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Keine nötig (kein Reflection-/JNI-Zugriff von außen).
# Sora-Editor-Komponenten und Tree-sitter-Sprachen werden per Class.forName/Reflection geladen
# (SoraCodeEditor: BracketPairs, EditorStickyScroll, EditorMinimap; TreeSitterLanguageSupport: TSLanguage*.getInstance()).
-keep class io.github.rosemoe.sora.widget.component.** { *; }
-keep class com.itsaky.androidide.treesitter.** { *; }
-dontwarn io.github.rosemoe.sora.**
