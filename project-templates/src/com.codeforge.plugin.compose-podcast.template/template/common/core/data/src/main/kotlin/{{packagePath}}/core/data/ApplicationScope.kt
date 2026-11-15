package {{packageName}}.core.data

import javax.inject.Qualifier

/** Kennzeichnet den CoroutineScope, der so lange wie die App lebt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
