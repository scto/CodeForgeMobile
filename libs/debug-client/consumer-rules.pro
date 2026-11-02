# libs:debug-client — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# JDWP-Nachrichten werden mit kotlinx.serialization (de)serialisiert; Serializer-Klassen der eigenen @Serializable-Modelle sichern.
-keepclassmembers class com.codeforge.libs.debug_client.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.codeforge.libs.debug_client.**$$serializer { *; }
