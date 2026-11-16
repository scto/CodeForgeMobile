/**
 * Modul: :feature:composepreview
 * @author Thomas Schmid
 */
package com.codeforge.feature.composepreview.interpreter

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import kotlin.metadata.ClassKind
import kotlin.metadata.KmClass
import kotlin.metadata.KmClassifier
import kotlin.metadata.KmFunction
import kotlin.metadata.KmProperty
import kotlin.metadata.KmType
import kotlin.metadata.KmValueParameter
import kotlin.metadata.declaresDefaultValue
import kotlin.metadata.isNullable
import kotlin.metadata.isSuspend
import kotlin.metadata.jvm.KotlinClassMetadata
import kotlin.metadata.jvm.getterSignature
import kotlin.metadata.jvm.signature
import kotlin.metadata.kind

/** Eine aufrufbare Kotlin-Funktion samt Ort (Datei-Klasse bzw. Klasse) und Metadaten. */
internal class FnSym(
    val name: String,
    val owner: Class<*>,
    val km: KmFunction?,
    /** Für Klassenmitglieder: Funktion braucht ein Objekt (Dispatch-Empfänger). */
    val isMember: Boolean,
    /** Nur Java-Methoden ohne Kotlin-Metadaten. */
    val javaMethod: Method? = null,
    /** Teil-Klasse einer Multifile-Fassade (enthält auch private `@InlineOnly`-Methoden). */
    val part: Class<*>? = null,
) {
    val isExtension: Boolean get() = km?.receiverParameterType != null
    val params: List<KmValueParameter> get() = km?.valueParameters.orEmpty()
    override fun toString() = "${owner.name}.$name"
}

/** Eine Property: Getter als Methode, Metadaten für Rückgabe-Wertklassen. */
internal class PropSym(val name: String, val owner: Class<*>, val km: KmProperty?, val getter: Method?, val field: java.lang.reflect.Field?, val isMember: Boolean) {
    val isExtension: Boolean get() = km?.receiverParameterType != null
}

/** Vorbereiteter JVM-Aufruf: Methode und wie die Argumente angeordnet werden. */
internal class JvmLayout(
    val method: Method,
    /** Anzahl führender Empfänger-Parameter (Dispatch bei statischen `$default`/DefaultImpls + Extension). */
    val receivers: Int,
    val composerIndex: Int,
    val changedCount: Int,
    /** Compose-Defaults (int-Masken, 31 Parameter je Maske) direkt nach den `$changed`-Werten. */
    val composeDefaultMasks: Int,
    /** Kotlin-`$default`-Masken (32 je Maske) plus abschließendes `Object`. */
    val kotlinDefaultMasks: Int,
    val staticWithDispatch: Boolean,
)

/** Liest Kotlin-Metadaten per Reflection und findet Funktionen, Properties und Klassen. */
internal class KotlinReflect(private val loader: ClassLoader, private val index: ClassIndex) {

    // --- Klassen ------------------------------------------------------------------------------------------------

    private val classCache = HashMap<String, Class<*>?>()

    /** Lädt `a.b.C.D` (verschachtelt → `a.b.C$D`). */
    fun classFor(fqName: String): Class<*>? = classCache.getOrPut(fqName) {
        val mapped = JAVA_MAPPED[fqName]
        if (mapped != null) return@getOrPut mapped
        var name = fqName
        while (true) {
            runCatching { return@getOrPut Class.forName(name, false, loader) }
            val i = name.lastIndexOf('.')
            if (i < 0) return@getOrPut null
            name = name.substring(0, i) + "$" + name.substring(i + 1)
        }
        @Suppress("UNREACHABLE_CODE") null
    }

    /** Klasse zu einem Metadaten-Klassifizierer (`androidx/compose/ui/unit/Dp`, `kotlin/String`). */
    fun classForKm(name: String): Class<*>? {
        val dotted = name.replace('/', '.')
        KOTLIN_TO_JAVA[dotted]?.let { return it }
        Regex("""kotlin\.(?:jvm\.functions\.)?Function(\d+)""").matchEntire(dotted)?.let { return classFor("kotlin.jvm.functions.Function${it.groupValues[1]}") }
        return classFor(dotted)
    }

    private val metaCache = HashMap<Class<*>, KotlinClassMetadata?>()

    fun metadata(c: Class<*>): KotlinClassMetadata? = metaCache.getOrPut(c) {
        val ann = c.getAnnotation(Metadata::class.java) ?: return@getOrPut null
        runCatching { KotlinClassMetadata.readLenient(ann) }.getOrNull()
    }

    fun kmClass(c: Class<*>): KmClass? = (metadata(c) as? KotlinClassMetadata.Class)?.kmClass

    fun isObject(c: Class<*>): Boolean = kmClass(c)?.let { it.kind == ClassKind.OBJECT || it.kind == ClassKind.COMPANION_OBJECT } == true

    fun objectInstance(c: Class<*>): Any? = runCatching { c.getField("INSTANCE").get(null) }.getOrNull()

    fun companionInstance(c: Class<*>): Any? {
        val name = kmClass(c)?.companionObject ?: "Companion"
        return runCatching { c.getField(name).get(null) }.getOrNull()
    }

    /** Wertklasse (`@JvmInline value class`)? Erkennbar an der statischen `box-impl`. */
    fun isValueClass(c: Class<*>): Boolean = valueCache.getOrPut(c) { c.declaredMethods.any { it.name == "box-impl" && Modifier.isStatic(it.modifiers) } }
    private val valueCache = HashMap<Class<*>, Boolean>()

    fun box(valueClass: Class<*>, raw: Any?): Any? {
        if (raw == null || valueClass.isInstance(raw)) return raw
        val m = valueClass.declaredMethods.first { it.name == "box-impl" && Modifier.isStatic(it.modifiers) }
        return m.invoke(null, raw)
    }

    fun unbox(value: Any): Any? {
        val m = value.javaClass.getMethod("unbox-impl")
        return m.invoke(value)
    }

    // --- Top-Level ---------------------------------------------------------------------------------------------

    private class PackageSymbols(val functions: Map<String, List<FnSym>>, val properties: Map<String, List<PropSym>>)
    private val packageCache = HashMap<String, PackageSymbols>()

    private fun packageSymbols(pkg: String): PackageSymbols = packageCache.getOrPut(pkg) {
        val functions = HashMap<String, MutableList<FnSym>>()
        val properties = HashMap<String, MutableList<PropSym>>()
        for (className in index.classesIn(pkg)) {
            val c = runCatching { Class.forName(className, false, loader) }.getOrNull() ?: continue
            val (kmPkg, facade) = when (val md = metadata(c)) {
                is KotlinClassMetadata.FileFacade -> md.kmPackage to c
                is KotlinClassMetadata.MultiFileClassPart -> md.kmPackage to (classFor(md.facadeClassName.replace('/', '.')) ?: c)
                else -> continue
            }
            kmPkg.functions.forEach { f -> functions.getOrPut(f.name) { ArrayList() } += FnSym(f.name, facade, f, isMember = false, part = c.takeIf { it != facade }) }
            kmPkg.properties.forEach { pr ->
                val getterName = pr.getterSignature?.name
                val getter = getterName?.let { n -> facade.declaredMethods.firstOrNull { it.name == n } ?: c.declaredMethods.firstOrNull { it.name == n } }
                val field = if (getter == null) runCatching { facade.getDeclaredField(pr.name) }.getOrNull() else null
                properties.getOrPut(pr.name) { ArrayList() } += PropSym(pr.name, facade, pr, getter, field, isMember = false)
            }
        }
        PackageSymbols(functions, properties)
    }

    fun topLevelFunctions(pkg: String, name: String): List<FnSym> = packageSymbols(pkg).functions[name].orEmpty()
    fun topLevelProperties(pkg: String, name: String): List<PropSym> = packageSymbols(pkg).properties[name].orEmpty()

    // --- Klassenmitglieder -------------------------------------------------------------------------------------

    private val memberFnCache = HashMap<Pair<Class<*>, String>, List<FnSym>>()

    /** Mitgliedsfunktionen (auch geerbte, auch aus Interfaces) mit Kotlin-Metadaten; sonst Java-Methoden. */
    fun memberFunctions(c: Class<*>, name: String): List<FnSym> = memberFnCache.getOrPut(c to name) {
        val result = ArrayList<FnSym>()
        val byKey = LinkedHashMap<String, FnSym>()
        for (k in hierarchy(c)) {
            val km = kmClass(k)
            km?.functions?.filter { it.name == name }?.forEach { f ->
                val key = f.valueParameters.size.toString() + (f.receiverParameterType?.let { "r" } ?: "") + f.valueParameters.joinToString { (it.type.classifier as? KmClassifier.Class)?.name ?: "?" }
                val existing = byKey[key]
                // Defaults stehen an der ursprünglichen Deklaration (Interface/Basisklasse), nicht am Override.
                val defaults = f.valueParameters.count { it.declaresDefaultValue }
                val existingDefaults = existing?.km?.valueParameters?.count { it.declaresDefaultValue } ?: -1
                val publicOwner = Modifier.isPublic(k.modifiers)
                if (existing == null || defaults > existingDefaults || (defaults == existingDefaults && publicOwner && !Modifier.isPublic(existing.owner.modifiers))) {
                    byKey[key] = FnSym(name, k, f, isMember = true)
                }
            }
        }
        result += byKey.values
        if (result.isEmpty()) {
            // Java-Klassen (String, List, …): öffentliche Methoden
            c.methods.filter { it.name == name && !Modifier.isStatic(it.modifiers) }.map(::publicVersion).distinct()
                .forEach { result += FnSym(name, c, null, isMember = true, javaMethod = it) }
        }
        result
    }

    /** Mitglieds-Property (Kotlin) oder Java-Getter/-Feld. */
    fun memberProperty(c: Class<*>, name: String): PropSym? = memberPropCache.getOrPut(c to name) {
        for (k in hierarchy(c)) {
            val pr = kmClass(k)?.properties?.firstOrNull { it.name == name && it.receiverParameterType == null } ?: continue
            val gName = pr.getterSignature?.name
            val getter = gName?.let { n -> findMethod(c, n, 0) ?: findMethod(k, n, 0) }
            val field = if (getter == null) runCatching { k.getDeclaredField(name).apply { isAccessible = true } }.getOrNull() else null
            if (getter != null || field != null) return@getOrPut PropSym(name, k, pr, getter, field, isMember = true)
            // Getter mit Composer (z. B. MaterialTheme.colorScheme)
            val composableGetter = gName?.let { n -> c.methods.firstOrNull { it.name == n } }
            if (composableGetter != null) return@getOrPut PropSym(name, k, pr, composableGetter, null, isMember = true)
        }
        // Java-Konventionen
        val cap = name.replaceFirstChar { it.uppercaseChar() }
        val getter = findMethod(c, "get$cap", 0) ?: findMethod(c, "is$cap", 0) ?: findMethod(c, name, 0)
        if (getter != null) return@getOrPut PropSym(name, c, null, getter, null, isMember = true)
        val field = runCatching { c.getField(name) }.getOrNull()
        if (field != null && !Modifier.isStatic(field.modifiers)) PropSym(name, c, null, null, field, isMember = true) else null
    }
    private val memberPropCache = HashMap<Pair<Class<*>, String>, PropSym?>()

    /** Mitglieds-Extension-Properties (`val Foo.bar` in einer Klasse, z. B. in Scopes). */
    fun memberExtensionProperties(c: Class<*>, name: String): List<PropSym> = hierarchy(c).flatMap { k ->
        kmClass(k)?.properties?.filter { it.name == name && it.receiverParameterType != null }.orEmpty().mapNotNull { pr ->
            val g = pr.getterSignature?.name?.let { n -> c.methods.firstOrNull { it.name == n } } ?: return@mapNotNull null
            PropSym(name, k, pr, g, null, isMember = true)
        }
    }

    /** Gleiche Methode aus einem öffentlichen Typ (Methoden privater Implementierungsklassen sind nicht aufrufbar). */
    fun publicVersion(m: Method): Method {
        if (Modifier.isPublic(m.declaringClass.modifiers)) return m
        for (k in hierarchy(m.declaringClass)) {
            if (!Modifier.isPublic(k.modifiers)) continue
            runCatching { return k.getMethod(m.name, *m.parameterTypes) }
        }
        return m
    }

    private fun findMethod(c: Class<*>, name: String, params: Int): Method? =
        c.methods.firstOrNull { it.name == name && it.parameterCount == params }?.let(::publicVersion)
            ?: generateSequence(c) { it.superclass }.flatMap { it.declaredMethods.asSequence() }
                .firstOrNull { it.name == name && it.parameterCount == params }?.apply { isAccessible = true }

    fun hierarchy(c: Class<*>): List<Class<*>> {
        val out = LinkedHashSet<Class<*>>()
        fun visit(k: Class<*>?) {
            if (k == null || !out.add(k)) return
            visit(k.superclass)
            k.interfaces.forEach { visit(it) }
        }
        visit(c)
        return out.toList()
    }

    /** Konstruktoren einer Klasse mit Metadaten (für Named Arguments und Defaults). */
    fun constructors(c: Class<*>): List<Pair<kotlin.metadata.KmConstructor?, java.lang.reflect.Constructor<*>?>> {
        val km = kmClass(c)
        if (km == null) return c.constructors.map { null to it }
        return km.constructors.map { k -> k to null }
    }

    // --- JVM-Aufruf-Layout -------------------------------------------------------------------------------------

    private val layoutCache = HashMap<FnSym, List<JvmLayout>>()

    /** Mögliche JVM-Methoden für [fn]: direkte Methode und `$default`-Variante, inkl. Compose-Parametern. */
    fun layouts(fn: FnSym): List<JvmLayout> = layoutCache.getOrPut(fn) {
        fn.javaMethod?.let { return@getOrPut listOf(JvmLayout(it, 0, -1, 0, 0, 0, false)) }
        val km = fn.km ?: return@getOrPut emptyList()
        val jvmName = km.signature?.name ?: km.name
        val n = km.valueParameters.size
        val hasExt = km.receiverParameterType != null
        val hasDefaults = km.valueParameters.any { it.declaresDefaultValue }
        val owners = buildList {
            add(fn.owner)
            fn.part?.let { add(it) }
            if (fn.owner.isInterface) runCatching { Class.forName(fn.owner.name + "\$DefaultImpls", false, fn.owner.classLoader) }.getOrNull()?.let { add(it) }
            if (isValueClass(fn.owner)) add(fn.owner)
        }
        val methods = owners.flatMap { o -> (o.declaredMethods.asList() + o.methods.asList()).distinct() }
            .filter { it.name == jvmName || it.name == "$jvmName\$default" || (isValueClass(fn.owner) && it.name == "$jvmName-impl") }
        // Überladungen gleichen (gemangelten) Namens anhand der Parametertypen aus den Metadaten unterscheiden
        val sigTypes = km.signature?.descriptor?.let { parseDescriptorParams(it) }
        val ownParams = (if (hasExt) 1 else 0) + n
        methods.distinct().mapNotNull { m ->
            val types = m.parameterTypes
            val static = Modifier.isStatic(m.modifiers)
            val staticWithDispatch = fn.isMember && static
            val receivers = (if (staticWithDispatch) 1 else 0) + (if (hasExt) 1 else 0)
            val fixed = receivers + n
            if (types.size < fixed) return@mapNotNull null
            if (sigTypes != null && sigTypes.size >= ownParams) {
                val offset = if (staticWithDispatch && !(isValueClass(fn.owner) && sigTypes.size > ownParams && m.name == jvmName)) 1 else 0
                val actual = types.drop(offset).take(ownParams).map { descriptorOf(it) }
                val expected = sigTypes.take(ownParams)
                val valueClassMember = isValueClass(fn.owner) && staticWithDispatch
                if (!valueClassMember && actual != expected) return@mapNotNull null
            }
            val rest = types.drop(fixed)
            val composerIdx = rest.indexOfFirst { it.name == COMPOSER }
            val isKotlinDefault = m.name.endsWith("\$default")
            if (isKotlinDefault) {
                if (rest.lastOrNull() != Any::class.java) return@mapNotNull null
                val masks = (n + 31) / 32
                val changed = if (composerIdx >= 0) rest.size - 1 - masks - 1 - composerIdx else 0
                if (composerIdx < 0 && rest.size != masks + 1) return@mapNotNull null
                JvmLayout(m.apply { isAccessible = true }, receivers, if (composerIdx >= 0) fixed + composerIdx else -1, changed, 0, masks, staticWithDispatch)
            } else {
                if (composerIdx < 0) {
                    if (rest.isNotEmpty()) return@mapNotNull null
                    JvmLayout(m.apply { isAccessible = true }, receivers, -1, 0, 0, 0, staticWithDispatch)
                } else {
                    val ints = rest.size - composerIdx - 1
                    if (rest.drop(composerIdx + 1).any { it != Int::class.javaPrimitiveType }) return@mapNotNull null
                    val defaultMasks = if (hasDefaults) (n + 30) / 31 else 0
                    val changed = ints - defaultMasks
                    if (changed < 1) return@mapNotNull null
                    JvmLayout(m.apply { isAccessible = true }, receivers, fixed + composerIdx, changed, defaultMasks, 0, staticWithDispatch)
                }
            }
        }.sortedBy { if (it.kotlinDefaultMasks > 0) 1 else 0 }
    }

    /** Parametertypen eines JVM-Deskriptors als Einzel-Deskriptoren. */
    private fun parseDescriptorParams(desc: String): List<String> {
        val result = ArrayList<String>()
        var i = desc.indexOf('(') + 1
        while (i < desc.length && desc[i] != ')') {
            val start = i
            while (desc[i] == '[') i++
            if (desc[i] == 'L') i = desc.indexOf(';', i)
            i++
            result += desc.substring(start, i)
        }
        return result
    }

    private fun descriptorOf(c: Class<*>): String = when {
        c.isArray -> "[" + descriptorOf(c.componentType!!)
        c == Int::class.javaPrimitiveType -> "I"
        c == Long::class.javaPrimitiveType -> "J"
        c == Float::class.javaPrimitiveType -> "F"
        c == Double::class.javaPrimitiveType -> "D"
        c == Boolean::class.javaPrimitiveType -> "Z"
        c == Char::class.javaPrimitiveType -> "C"
        c == Short::class.javaPrimitiveType -> "S"
        c == Byte::class.javaPrimitiveType -> "B"
        c == Void.TYPE -> "V"
        else -> "L" + c.name.replace('.', '/') + ";"
    }

    // --- Typen -------------------------------------------------------------------------------------------------

    /** Passt ein Wert zu einem Kotlin-Typ (grob: Klassenzugehörigkeit, Nullbarkeit, Funktionstypen)? */
    fun accepts(type: KmType?, value: Any?): Boolean {
        if (type == null) return true
        if (value == null) return type.isNullable || type.classifier is KmClassifier.TypeParameter
        val cls = when (val c = type.classifier) {
            is KmClassifier.Class -> classForKm(c.name) ?: return false
            is KmClassifier.TypeParameter -> return true
            is KmClassifier.TypeAlias -> return true
        }
        if (value is CallableValue) return isFunctionLike(cls)
        if (value is ClassRefValue) return true
        val boxed = PRIMITIVE_BOXES[cls] ?: cls
        if (boxed.isInstance(value)) return true
        // Zahlen: Int-Literal für Long/Float/Double/Short/Byte
        if (value is Int && (boxed == java.lang.Long::class.java || boxed == java.lang.Short::class.java || boxed == java.lang.Byte::class.java)) return true
        return false
    }

    fun isFunctionLike(cls: Class<*>): Boolean =
        cls.isInterface && (cls.name.startsWith("kotlin.jvm.functions.Function") || cls.name == "kotlin.Function" || singleAbstractMethod(cls) != null)

    fun singleAbstractMethod(iface: Class<*>): Method? {
        if (!iface.isInterface) return null
        val abstract = iface.methods.filter { Modifier.isAbstract(it.modifiers) && it.declaringClass != Any::class.java }
            .distinctBy { it.name + it.parameterCount }
        return abstract.singleOrNull()
    }

    fun isSuspendType(type: KmType?): Boolean = type?.isSuspend == true

    /** Anzahl Parameter eines Kotlin-Funktionstyps (Empfänger mitgezählt). */
    fun functionArity(type: KmType?): Int? {
        val c = type?.classifier as? KmClassifier.Class ?: return null
        val m = Regex("""kotlin/(?:reflect/K)?Function(\d+)""").matchEntire(c.name) ?: return null
        return m.groupValues[1].toInt()
    }

    /** Rückgabe/Property-Typ als Wertklasse? Dann muss der rohe JVM-Wert geboxt werden. */
    fun valueClassOf(type: KmType?): Class<*>? {
        val c = type?.classifier as? KmClassifier.Class ?: return null
        val cls = classForKm(c.name) ?: return null
        return if (isValueClass(cls)) cls else null
    }

    companion object {
        const val COMPOSER = "androidx.compose.runtime.Composer"

        val KOTLIN_TO_JAVA: Map<String, Class<*>> = mapOf(
            "kotlin.Any" to Any::class.java,
            "kotlin.String" to String::class.java,
            "kotlin.CharSequence" to CharSequence::class.java,
            "kotlin.Int" to Int::class.javaObjectType,
            "kotlin.Long" to Long::class.javaObjectType,
            "kotlin.Float" to Float::class.javaObjectType,
            "kotlin.Double" to Double::class.javaObjectType,
            "kotlin.Boolean" to Boolean::class.javaObjectType,
            "kotlin.Char" to Char::class.javaObjectType,
            "kotlin.Short" to Short::class.javaObjectType,
            "kotlin.Byte" to Byte::class.javaObjectType,
            "kotlin.Number" to Number::class.java,
            "kotlin.Unit" to Unit::class.java,
            "kotlin.Throwable" to Throwable::class.java,
            "kotlin.Comparable" to Comparable::class.java,
            "kotlin.Enum" to Enum::class.java,
            "kotlin.Array" to Array<Any?>::class.java,
            "kotlin.IntArray" to IntArray::class.java,
            "kotlin.LongArray" to LongArray::class.java,
            "kotlin.FloatArray" to FloatArray::class.java,
            "kotlin.DoubleArray" to DoubleArray::class.java,
            "kotlin.BooleanArray" to BooleanArray::class.java,
            "kotlin.CharArray" to CharArray::class.java,
            "kotlin.ShortArray" to ShortArray::class.java,
            "kotlin.ByteArray" to ByteArray::class.java,
            "kotlin.Cloneable" to Cloneable::class.java,
            "kotlin.Annotation" to Annotation::class.java,
            "kotlin.Function" to kotlin.Function::class.java,
            "kotlin.Comparator" to Comparator::class.java,
            "kotlin.collections.MutableCollection" to Collection::class.java,
            "kotlin.collections.MutableIterable" to Iterable::class.java,
            "kotlin.collections.ListIterator" to ListIterator::class.java,
            "kotlin.collections.MutableIterator" to Iterator::class.java,
            "kotlin.collections.MutableListIterator" to ListIterator::class.java,
            "kotlin.collections.MutableMap.MutableEntry" to Map.Entry::class.java,
            "kotlin.collections.List" to List::class.java,
            "kotlin.collections.MutableList" to List::class.java,
            "kotlin.collections.Collection" to Collection::class.java,
            "kotlin.collections.Iterable" to Iterable::class.java,
            "kotlin.collections.Set" to Set::class.java,
            "kotlin.collections.MutableSet" to Set::class.java,
            "kotlin.collections.Map" to Map::class.java,
            "kotlin.collections.MutableMap" to Map::class.java,
            "kotlin.collections.Map.Entry" to Map.Entry::class.java,
            "kotlin.collections.Iterator" to Iterator::class.java,
        )
        val JAVA_MAPPED: Map<String, Class<*>> = KOTLIN_TO_JAVA
        val PRIMITIVE_BOXES: Map<Class<*>, Class<*>> = mapOf(
            Int::class.javaPrimitiveType!! to Int::class.javaObjectType,
            Long::class.javaPrimitiveType!! to Long::class.javaObjectType,
            Float::class.javaPrimitiveType!! to Float::class.javaObjectType,
            Double::class.javaPrimitiveType!! to Double::class.javaObjectType,
            Boolean::class.javaPrimitiveType!! to Boolean::class.javaObjectType,
            Char::class.javaPrimitiveType!! to Char::class.javaObjectType,
            Short::class.javaPrimitiveType!! to Short::class.javaObjectType,
            Byte::class.javaPrimitiveType!! to Byte::class.javaObjectType,
        )
    }
}

/** Werte, die aufgerufen werden können (interpretierte Lambdas und Funktionsreferenzen). */
internal interface CallableValue

/** Verweis auf eine Klasse als Ausdruck (`Color`, `Icons.Filled`, `Category`). */
internal data class ClassRefValue(val cls: Class<*>)
