/**
 * Modul: :libs:command-palette-impl
 *
 * Hilt: bindet [CommandPalette] und meldet die eingebauten Anbieter an. Weitere Module ergänzen eigene Anbieter mit
 * `@Provides @IntoSet fun …(): PaletteProvider`.
 */
package com.codeforge.libs.command_palette_impl

import com.codeforge.libs.command_palette_api.CommandPalette
import com.codeforge.libs.command_palette_api.PaletteProvider
import com.codeforge.libs.command_palette_impl.providers.BuiltinPaletteProviders
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import javax.inject.Singleton

/** Hilt-Modul der Befehlspalette. */
@Module
@InstallIn(SingletonComponent::class)
abstract class CommandPaletteModule {
    /** [CommandPaletteImpl] als [CommandPalette]. */
    @Binds
    @Singleton
    abstract fun bindCommandPalette(impl: CommandPaletteImpl): CommandPalette

    companion object {
        /** Eingebaute Anbieter (ein Bereich je Anbieter). */
        @Provides
        @ElementsIntoSet
        fun builtinProviders(): Set<PaletteProvider> = BuiltinPaletteProviders.ALL.toSet()
    }
}
