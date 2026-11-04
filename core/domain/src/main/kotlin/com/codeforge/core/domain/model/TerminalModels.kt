// Modul: :core:domain
package com.codeforge.core.domain.model

/** Zustand einer Terminal-Sitzung. */
enum class TerminalSessionState { STOPPED, STARTING, RUNNING, EXITED, FAILED }
