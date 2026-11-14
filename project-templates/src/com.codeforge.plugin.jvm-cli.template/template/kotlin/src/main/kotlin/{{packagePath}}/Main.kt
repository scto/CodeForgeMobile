package {{packageName}}

/** Begrüßt den Namen aus dem ersten Argument. */
fun greet(name: String?): String = "Hallo, ${name?.takeIf { it.isNotBlank() } ?: "Welt"}!"

fun main(args: Array<String>) {
    println(greet(args.firstOrNull()))
}
