package com.codeforge.feature.x2c.run

import com.codeforge.feature.x2c.X2cPhase

/** Für Tests und Umgebungen ohne Android-Framework. */
object NoopX2cRunNotifier : X2cRunNotifier {
    override fun started(onCancelRequested: () -> Unit) = Unit
    override fun update(phase: X2cPhase?, percent: Int?) = Unit
    override fun finished() = Unit
}
