package dev.sharingan.ui

/** Token classes the JSON body view colors differently. */
internal enum class JsonTokenType { KEY, STRING, NUMBER, LITERAL, PUNCT, WS }

internal data class JsonToken(val type: JsonTokenType, val text: String)

/**
 * Parses [raw] and re-emits it as a pretty-printed (2-space indented) token
 * stream for syntax coloring. Returns `null` when [raw] is not valid JSON so
 * callers can fall back to plain text.
 */
internal fun prettyJsonTokens(raw: String): List<JsonToken>? = try {
    val parser = JsonParser(raw)
    parser.parseValue(indent = 0)
    parser.skipWhitespace()
    if (parser.position < raw.length) null else parser.tokens
} catch (_: JsonParseException) {
    null
}

/** Pretty-printed JSON text, or `null` when [raw] is not valid JSON. */
internal fun prettyJson(raw: String): String? =
    prettyJsonTokens(raw)?.joinToString("") { it.text }

private class JsonParseException : Exception() {
    companion object {
        // Stackless singleton: parse failure is an expected, hot path.
        val INSTANCE = JsonParseException()
    }
}

private class JsonParser(private val source: String) {
    var position: Int = 0
        private set
    val tokens = mutableListOf<JsonToken>()

    private fun fail(): Nothing = throw JsonParseException.INSTANCE

    private fun peek(): Char = if (position < source.length) source[position] else fail()

    fun skipWhitespace() {
        while (position < source.length && source[position].isWhitespace()) position++
    }

    private fun emit(type: JsonTokenType, text: String) {
        tokens += JsonToken(type, text)
    }

    private fun newline(indent: Int) = emit(JsonTokenType.WS, "\n" + "  ".repeat(indent))

    fun parseValue(indent: Int) {
        skipWhitespace()
        when (peek()) {
            '{' -> parseObject(indent)
            '[' -> parseArray(indent)
            '"' -> emit(JsonTokenType.STRING, scanString())
            't' -> emit(JsonTokenType.LITERAL, expect("true"))
            'f' -> emit(JsonTokenType.LITERAL, expect("false"))
            'n' -> emit(JsonTokenType.LITERAL, expect("null"))
            else -> emit(JsonTokenType.NUMBER, scanNumber())
        }
    }

    private fun parseObject(indent: Int) {
        position++ // {
        skipWhitespace()
        if (peek() == '}') {
            position++
            emit(JsonTokenType.PUNCT, "{}")
            return
        }
        emit(JsonTokenType.PUNCT, "{")
        while (true) {
            newline(indent + 1)
            skipWhitespace()
            if (peek() != '"') fail()
            emit(JsonTokenType.KEY, scanString())
            skipWhitespace()
            if (peek() != ':') fail()
            position++
            emit(JsonTokenType.PUNCT, ": ")
            parseValue(indent + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    position++
                    emit(JsonTokenType.PUNCT, ",")
                }
                '}' -> {
                    position++
                    newline(indent)
                    emit(JsonTokenType.PUNCT, "}")
                    return
                }
                else -> fail()
            }
        }
    }

    private fun parseArray(indent: Int) {
        position++ // [
        skipWhitespace()
        if (peek() == ']') {
            position++
            emit(JsonTokenType.PUNCT, "[]")
            return
        }
        emit(JsonTokenType.PUNCT, "[")
        while (true) {
            newline(indent + 1)
            parseValue(indent + 1)
            skipWhitespace()
            when (peek()) {
                ',' -> {
                    position++
                    emit(JsonTokenType.PUNCT, ",")
                }
                ']' -> {
                    position++
                    newline(indent)
                    emit(JsonTokenType.PUNCT, "]")
                    return
                }
                else -> fail()
            }
        }
    }

    private fun scanString(): String {
        val start = position
        position++ // opening quote
        while (true) {
            if (position >= source.length) fail()
            when (source[position]) {
                '\\' -> {
                    if (position + 1 >= source.length) fail()
                    position += 2
                }
                '"' -> {
                    position++
                    return source.substring(start, position)
                }
                else -> position++
            }
        }
    }

    private fun scanNumber(): String {
        val start = position
        if (peek() == '-') position++
        if (position >= source.length || !source[position].isDigit()) fail()
        while (position < source.length && source[position].isDigit()) position++
        if (position < source.length && source[position] == '.') {
            position++
            if (position >= source.length || !source[position].isDigit()) fail()
            while (position < source.length && source[position].isDigit()) position++
        }
        if (position < source.length && (source[position] == 'e' || source[position] == 'E')) {
            position++
            if (position < source.length && (source[position] == '+' || source[position] == '-')) position++
            if (position >= source.length || !source[position].isDigit()) fail()
            while (position < source.length && source[position].isDigit()) position++
        }
        return source.substring(start, position)
    }

    private fun expect(literal: String): String {
        if (!source.startsWith(literal, position)) fail()
        position += literal.length
        return literal
    }
}
