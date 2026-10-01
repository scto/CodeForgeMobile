package com.codeforge.core.domain.repository

interface SystemPathsRepository {
    fun getFilesDir(): String
    fun getLocalDir(): String
    fun getLocalBinDir(): String
    fun getLocalLibDir(): String
    fun getLocalTmpDir(): String
    fun getDistroDir(distro: String): String
    fun getDistroHomeDir(distro: String): String
}
