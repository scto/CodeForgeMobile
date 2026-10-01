package com.codeforge.feature.editor.state

class EditorSessionStore {

    private val sessions = linkedMapOf<String, EditorSession>()

    fun get(path: String): EditorSession? = sessions[path]

    fun put(session: EditorSession) {
        sessions[session.filePath] = session
    }

    fun updateContent(path: String, content: String) {
        val current = sessions[path] ?: return
        sessions[path] = current.copy(content = content, dirty = true)
    }

    fun remove(path: String): EditorSession? = sessions.remove(path)

    fun getAll(): List<EditorSession> = sessions.values.toList()
}
