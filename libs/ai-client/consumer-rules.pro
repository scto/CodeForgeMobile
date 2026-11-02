# libs:ai-client — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Die Provider-Clients (OpenAI, Anthropic, Gemini) (de)serialisieren ihre Nachrichten mit kotlinx.serialization;
# die Bibliothek bringt eigene Regeln mit, hier nur die Serializer-Klassen der eigenen @Serializable-Modelle sichern.
-keepclassmembers class com.codeforge.libs.ai_client.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.codeforge.libs.ai_client.**$$serializer { *; }
