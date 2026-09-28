package app.yomi.reader.local

object NaturalOrder : Comparator<String> {
    private val tokenRegex = Regex("""\d+|\D+""")

    override fun compare(left: String, right: String): Int {
        if (left === right) return 0

        val a = tokenRegex.findAll(left).map { it.value }.toList()
        val b = tokenRegex.findAll(right).map { it.value }.toList()
        val count = minOf(a.size, b.size)

        for (index in 0 until count) {
            val x = a[index]
            val y = b[index]
            val xNumber = x.all(Char::isDigit)
            val yNumber = y.all(Char::isDigit)

            val result = when {
                xNumber && yNumber -> compareNumbers(x, y)
                else -> x.compareTo(y, ignoreCase = true)
            }
            if (result != 0) return result
        }

        return when {
            a.size != b.size -> a.size.compareTo(b.size)
            else -> left.compareTo(right, ignoreCase = false)
        }
    }

    private fun compareNumbers(left: String, right: String): Int {
        val normalizedLeft = left.trimStart('0').ifEmpty { "0" }
        val normalizedRight = right.trimStart('0').ifEmpty { "0" }

        if (normalizedLeft.length != normalizedRight.length) {
            return normalizedLeft.length.compareTo(normalizedRight.length)
        }

        val valueResult = normalizedLeft.compareTo(normalizedRight)
        if (valueResult != 0) return valueResult

        return left.length.compareTo(right.length)
    }
}
