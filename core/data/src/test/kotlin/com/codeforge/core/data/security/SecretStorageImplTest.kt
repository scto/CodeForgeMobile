// Modul: :core:data
package com.codeforge.core.data.security

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecretStorageImplTest {

    private class FakeKeyValueStore : KeyValueStore {
        private val map = mutableMapOf<String, String>()
        override fun get(key: String): String? = map[key]
        override fun put(key: String, value: String) { map[key] = value }
        override fun remove(key: String) { map.remove(key) }
    }

    private class FakeSecretBox : SecretBox {
        override fun encrypt(plain: String): String = "ENC:$plain"
        override fun decrypt(encoded: String): String? =
            if (encoded.startsWith("ENC:")) encoded.removePrefix("ENC:") else null
    }

    @Test
    fun storeRetrieveAndRemoveSecret() = runBlocking {
        val store = FakeKeyValueStore()
        val box = FakeSecretBox()
        val storage = SecretStorageImpl(store, box)

        assertNull(storage.getSecret("my_key"))

        storage.storeSecret("my_key", "super_secret_value")
        assertEquals("super_secret_value", storage.getSecret("my_key"))
        assertEquals("ENC:super_secret_value", store.get("my_key"))

        storage.removeSecret("my_key")
        assertNull(storage.getSecret("my_key"))
        assertNull(store.get("my_key"))
    }
}
