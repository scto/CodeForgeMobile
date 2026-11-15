package {{packageName}}.core.domain.model

/** Eine Notiz. [createdAt] in Millisekunden seit 1970. */
data class Note(
    val id: Long,
    val title: String,
    val body: String,
    val createdAt: Long
)
