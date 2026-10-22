package com.khumam.gbaduo.companion

/** Context for a `list` widget's template: a path resolves against [item] first, then [root]. */
data class ScopedValues(val item: Any?, val index: Int, val root: Any?)

/**
 * Resolves `"party[0].name"`-style paths over a value tree (`Map<String, Any?>` /
 * `List<Any?>` / scalars, as produced by [JsonTree] or [ValuesReader]). Inside a
 * `list` widget's template, [context] is a [ScopedValues] — `@index` and item keys
 * resolve first, falling back to the root snapshot for anything the item doesn't have.
 */
object Binding {

    private val TOKEN = Regex("\\{([^{}]+)\\}")
    private val SEGMENT = Regex("([a-zA-Z0-9_]+)|\\[(\\d+)\\]")

    fun resolve(path: String, context: Any?): Any? {
        if (context is ScopedValues) {
            if (path == "@index") return context.index.toLong()
            resolvePlain(path, context.item)?.let { return it }
            return resolvePlain(path, context.root)
        }
        return resolvePlain(path, context)
    }

    private val paths = java.util.concurrent.ConcurrentHashMap<String, List<Any>>()
    private val templates = java.util.concurrent.ConcurrentHashMap<String, List<Pair<String, Boolean>>>()

    private fun resolvePlain(path: String, root: Any?): Any? {
        val segments = paths.getOrPut(path) {
            SEGMENT.findAll(path).map { match ->
                match.groupValues[1].takeIf { it.isNotEmpty() } ?: match.groupValues[2].toInt()
            }.toList()
        }
        var current = root
        for (segment in segments) {
            current = if (segment is String) (current as? Map<*, *>)?.get(segment)
                else (current as? List<*>)?.getOrNull(segment as Int)
            if (current == null) return null
        }
        return current
    }

    fun interpolate(template: String, context: Any?): String {
        val pieces = templates.getOrPut(template) {
            val result = mutableListOf<Pair<String, Boolean>>()
            var offset = 0
            for (match in TOKEN.findAll(template)) {
                if (match.range.first > offset) result += template.substring(offset, match.range.first) to false
                result += match.groupValues[1] to true
                offset = match.range.last + 1
            }
            if (offset < template.length) result += template.substring(offset) to false
            result
        }
        if (pieces.size == 1 && !pieces[0].second) return template
        return buildString { for ((value, bound) in pieces) append(if (bound) format(resolve(value, context)) else value) }
    }

    private fun format(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    fun isTruthy(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is Long -> value != 0L
        is Double -> value != 0.0
        is String -> value.isNotEmpty()
        is List<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        else -> true
    }

    fun asLong(value: Any?): Long = when (value) {
        is Long -> value
        is Int -> value.toLong()
        is Double -> value.toLong()
        is String -> value.toLongOrNull() ?: 0L
        else -> 0L
    }

    fun asDouble(value: Any?): Double = when (value) {
        is Double -> value
        is Float -> value.toDouble()
        is Long -> value.toDouble()
        is Int -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    @Suppress("UNCHECKED_CAST")
    fun asList(value: Any?): List<Any?> = value as? List<Any?> ?: emptyList()
}
