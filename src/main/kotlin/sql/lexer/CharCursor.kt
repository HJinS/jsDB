package sql.lexer


class CharCursor(private val input: String) {
    var pos = 0
        private set

    operator fun get(ahead: Int): Char = if (pos + ahead < input.length) input[pos + ahead] else '\u0000'

    val current: Char get() = this[0]
    
    val atEnd: Boolean get() = pos >= input.length
    
    fun advance(count: Int = 1) {
        pos += count
    }

    fun slice(start: Int): String = input.substring(start, pos)
}
