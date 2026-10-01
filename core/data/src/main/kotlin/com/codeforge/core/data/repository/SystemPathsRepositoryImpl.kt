// Modul: :core:data
package com.codeforge.core.data.repository

import android.content.Context

import com.codeforge.core.domain.repository.SystemPathsRepository

import dagger.hilt.android.qualifiers.ApplicationContext

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemPathsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SystemPathsRepository {

    override fun getFilesDir(): String = context.filesDir.absolutePath

    override fun getLocalDir(): String = File(getFilesDir(), "local").apply { mkdirs() }.absolutePath

    override fun getLocalBinDir(): String = File(getLocalDir(), "bin").apply { mkdirs() }.absolutePath

    override fun getLocalLibDir(): String = File(getLocalDir(), "lib").apply { mkdirs() }.absolutePath
    
    override fun getLocalTmpDir(): String = File(getLocalDir(), "tmp").apply { mkdirs() }.absolutePath

    override fun getDistroDir(distro: String): String = File(getLocalDir(), distro).apply { mkdirs() }.absolutePath

    override fun getDistroHomeDir(distro: String): String = File(getDistroDir(distro), "root").apply { mkdirs() }.absolutePath
}
