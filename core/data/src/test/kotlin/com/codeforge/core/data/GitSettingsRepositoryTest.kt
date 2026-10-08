package com.codeforge.core.data

import com.codeforge.core.data.repository.GitSettingsRepositoryImpl
import com.codeforge.core.data.security.KeyValueStore
import com.codeforge.core.data.security.SecretBox
import com.codeforge.core.domain.model.GitIdentity
import com.codeforge.core.testing.TestRes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitSettingsRepositoryTest {

    init { TestRes.install() }

    private class MapStore : KeyValueStore {
        val map = LinkedHashMap<String, String>()
        override fun get(key: String) = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private class FakeBox(var broken: Boolean = false) : SecretBox {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(encoded: String) = if (broken || !encoded.startsWith("enc:")) null else encoded.removePrefix("enc:").reversed()
    }

    private val store = MapStore()
    private val box = FakeBox()
    private fun repo() = GitSettingsRepositoryImpl(store, box)

    @Test fun identityIsTrimmedPersistedAndObservable() = runBlocking {
        val r = repo()
        r.setIdentity(GitIdentity("  Thomas ", " t@x.org "))
        assertEquals(GitIdentity("Thomas", "t@x.org"), r.identity.first())
        assertEquals(GitIdentity("Thomas", "t@x.org"), repo().currentIdentity()) // neu geladen
    }

    @Test fun tokenIsNeverStoredInPlaintext() = runBlocking {
        val r = repo()
        r.saveCredential("https://GitHub.com/foo", "thomas", "ghp_secret")
        assertTrue(store.map.values.none { it.contains("ghp_secret") })
        assertEquals("ghp_secret", r.credentialFor("https://github.com/x/y.git")!!.token)
        assertEquals("thomas", r.credentialFor("https://github.com/x/y.git")!!.username)
        assertEquals(listOf("github.com"), r.credentials.first().map { it.host })
    }

    @Test fun credentialOnlyForMatchingHttpHost() = runBlocking {
        val r = repo()
        r.saveCredential("github.com", "u", "t")
        assertNull(r.credentialFor("https://gitlab.com/x/y"))
        assertNull(r.credentialFor("git@github.com:x/y.git"))
        assertNull(r.credentialFor("/local/path"))
    }

    @Test fun replaceAndRemove() = runBlocking {
        val r = repo()
        r.saveCredential("github.com", "u1", "t1")
        r.saveCredential("github.com", "u2", "t2")
        r.saveCredential("gitlab.com", "g", "gt")
        assertEquals(listOf("github.com", "gitlab.com"), r.credentials.first().map { it.host })
        assertEquals("u2", r.credentialFor("https://github.com/a")!!.username)
        r.removeCredential("github.com")
        assertNull(r.credentialFor("https://github.com/a"))
        assertEquals(listOf("gitlab.com"), r.credentials.first().map { it.host })
        assertFalse(store.map.keys.any { it.startsWith("cred.github.com") })
    }

    @Test fun undecryptableTokenYieldsNull() = runBlocking {
        val r = repo()
        r.saveCredential("github.com", "u", "t")
        box.broken = true
        assertNull(r.credentialFor("https://github.com/a"))
    }

    @Test fun validation() = runBlocking {
        val r = repo()
        for (args in listOf(Triple("", "u", "t"), Triple("h.com", " ", "t"), Triple("h.com", "u", ""))) {
            val failed = runCatching { r.saveCredential(args.first, args.second, args.third) }.isFailure
            assertTrue(failed)
        }
    }
}
