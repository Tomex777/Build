package com.night.keyboard.model

object KeyboardLayoutFactory {
    val letterRows: List<List<KeySpec>> = listOf(
        "qwertyuiop".map { letterKey(it) },
        "asdfghjkl".map { letterKey(it) },
        listOf(KeySpec("shift", "⇧", special = SpecialKey.SHIFT, weight = 1.35f)) +
            "zxcvbnm".map { letterKey(it) } +
            listOf(KeySpec("backspace", "⌫", special = SpecialKey.BACKSPACE, weight = 1.35f)),
        listOf(
            KeySpec("numbers", "123", special = SpecialKey.NUMBERS, weight = 1.08f),
            KeySpec("emoji", "☺", special = SpecialKey.EMOJI, weight = .95f),
            KeySpec("comma", ",", output = ",", secondary = "mic", special = SpecialKey.COMMA, weight = .9f),
            KeySpec("space", "", output = " ", special = SpecialKey.SPACE, weight = 4.4f),
            KeySpec("period", ".", output = ".", secondary = "!?", special = SpecialKey.PERIOD, weight = .9f),
            KeySpec("enter", "↵", special = SpecialKey.ENTER, weight = 1.18f),
        ),
    )

    val symbolRows: List<List<KeySpec>> = listOf(
        "1234567890".map { symbolKey(it) },
        listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/").map { symbolKey(it.singleOrNull(), it) },
        listOf("*", "\"", "'", ":", ";", "!", "?", "%").map { symbolKey(it.singleOrNull(), it) } +
            listOf(KeySpec("backspace", "⌫", special = SpecialKey.BACKSPACE, weight = 1.35f)),
        listOf(
            KeySpec("letters", "ABC", special = SpecialKey.LETTERS, weight = 1.15f),
            KeySpec("more_symbols", "=\\<", special = SpecialKey.MORE_SYMBOLS, weight = 1.05f),
            KeySpec("emoji", "☺", special = SpecialKey.EMOJI, weight = .95f),
            KeySpec("space", "", output = " ", special = SpecialKey.SPACE, weight = 4.2f),
            KeySpec("period", ".", output = ".", secondary = "!?", special = SpecialKey.PERIOD, weight = .9f),
            KeySpec("enter", "↵", special = SpecialKey.ENTER, weight = 1.18f),
        ),
    )

    val moreSymbolRows: List<List<KeySpec>> = listOf(
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆").map { symbolKey(it.singleOrNull(), it) },
        listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\").map { symbolKey(it.singleOrNull(), it) },
        listOf("%", "©", "®", "™", "✓", "[", "]", "<", ">").map { symbolKey(it.singleOrNull(), it) } +
            listOf(KeySpec("backspace", "⌫", special = SpecialKey.BACKSPACE, weight = 1.35f)),
        listOf(
            KeySpec("letters", "ABC", special = SpecialKey.LETTERS, weight = 1.15f),
            KeySpec("less_symbols", "123", special = SpecialKey.LESS_SYMBOLS, weight = 1.05f),
            KeySpec("emoji", "☺", special = SpecialKey.EMOJI, weight = .95f),
            KeySpec("space", "", output = " ", special = SpecialKey.SPACE, weight = 4.2f),
            KeySpec("period", ".", output = ".", secondary = "!?", special = SpecialKey.PERIOD, weight = .9f),
            KeySpec("enter", "↵", special = SpecialKey.ENTER, weight = 1.18f),
        ),
    )

    fun rows(layer: KeyboardLayer): List<List<KeySpec>> = when (layer) {
        KeyboardLayer.LETTERS -> letterRows
        KeyboardLayer.SYMBOLS -> symbolRows
        KeyboardLayer.SYMBOLS_MORE -> moreSymbolRows
    }

    private fun letterKey(c: Char): KeySpec {
        val secondary = mapOf(
            'q' to "1", 'w' to "2", 'e' to "3", 'r' to "4", 't' to "5",
            'y' to "6", 'u' to "7", 'i' to "8", 'o' to "9", 'p' to "0",
            'a' to "@", 's' to "#", 'd' to "$", 'f' to "_", 'g' to "&",
            'h' to "-", 'j' to "+", 'k' to "(", 'l' to ")",
            'z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";",
            'n' to "!", 'm' to "?",
        )[c]
        return KeySpec(id = c.toString(), label = c.toString(), output = c.toString(), secondary = secondary)
    }

    private fun symbolKey(char: Char?, label: String = char?.toString().orEmpty()): KeySpec = KeySpec(id = "symbol_$label", label = label, output = label)
}
