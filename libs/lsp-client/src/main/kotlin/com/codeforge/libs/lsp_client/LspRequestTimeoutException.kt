// Modul: :libs:lsp-client
package com.codeforge.libs.lsp_client

/** Der Server hat auf [method] nicht innerhalb von [timeoutMs] geantwortet; die Anfrage wurde abgebrochen. */
class LspRequestTimeoutException(val method: String, val timeoutMs: Long) :
    java.io.IOException("Keine Antwort auf $method nach ${timeoutMs / 1000} s")
