# feature:editor — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Keine nötig (kein Reflection-/JNI-Zugriff von außen).
# Sora-Editor-Komponenten und Tree-sitter-Sprachen werden per Class.forName/Reflection geladen
# (SoraCodeEditor: BracketPairs, EditorStickyScroll, EditorMinimap; TreeSitterLanguageSupport: TSLanguage*.getInstance()).
-keep class io.github.rosemoe.sora.widget.component.** { *; }
-keep class com.itsaky.androidide.treesitter.** { *; }
-dontwarn io.github.rosemoe.sora.**

# TextMate: sora-editor liest languages.json per Gson-Reflection (LanguageDefinitionReader$LanguageDefinitionList,
# TypeAdapter für GrammarDefinition) und tm4e instanziiert Modellklassen reflektiv. Mit R8-Umbenennung scheitert
# das ("Abstract classes can't be instantiated … Class name: de.b") und der Editor fällt auf Klartext zurück.
-keep class io.github.rosemoe.sora.langs.textmate.** { *; }
-keep class org.eclipse.tm4e.** { *; }
-keep class org.jcodings.** { *; }
-keep class org.joni.** { *; }
-dontwarn org.eclipse.tm4e.**
-dontwarn org.joni.**
-dontwarn org.jcodings.**
