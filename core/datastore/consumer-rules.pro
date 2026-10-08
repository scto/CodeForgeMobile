# core:datastore — Consumer-R8-Regeln: werden automatisch in jede App übernommen, die dieses Modul einbindet.
# Proto-DataStore: generierte Lite-Nachrichten dürfen nicht verkleinert/umbenannt werden.
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
