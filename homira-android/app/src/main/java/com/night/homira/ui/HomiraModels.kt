package com.night.homira.ui

import androidx.compose.ui.graphics.Color

internal enum class HomiraTab { Keypad, Recents, Contacts, Me }
internal enum class HomiraOverlay { None, Settings, EditProfile }

internal data class PersonCard(
    val name: String,
    val marker: String,
    val accent: Color,
    val subtitle: String,
    val number: String
)

internal data class RecentCall(
    val person: PersonCard,
    val whenText: String,
    val duration: String,
    val incoming: Boolean,
    val video: Boolean,
    val missed: Boolean = false
)

internal val mimi = PersonCard("MiMi", "✿", HomiraPink, "Homira", "+234 803 124 5678")
internal val hex = PersonCard("Hex", "⚡", HomiraBlue, "Homira", "+234 806 734 2011")
internal val ada = PersonCard("Ada", "A", HomiraGreen, "Homira", "+234 802 440 1812")
internal val tobi = PersonCard("Tobi", "T", Color(0xFFFFC46B), "Homira", "+234 809 220 4300")

internal val homiraPeople = listOf(mimi, hex, ada, tobi)

internal val homiraRecentCalls = listOf(
    RecentCall(mimi, "Today, 04:31", "28m", incoming = true, video = false),
    RecentCall(hex, "Yesterday, 22:18", "1h 12m", incoming = false, video = true),
    RecentCall(mimi, "Monday, 19:46", "Missed", incoming = true, video = true, missed = true),
    RecentCall(ada, "Sunday, 13:03", "46m", incoming = true, video = false),
    RecentCall(tobi, "Saturday, 21:11", "9m", incoming = false, video = false)
)

internal fun digitsOnly(value: String): String = value.filter { it.isDigit() }

internal fun formatDialNumber(value: String): String {
    val digits = digitsOnly(value)
    return when {
        digits.length <= 4 -> digits
        digits.length <= 7 -> "${digits.take(4)} ${digits.drop(4)}"
        digits.length <= 11 -> "${digits.take(4)} ${digits.drop(4).take(3)} ${digits.drop(7)}"
        else -> value
    }
}

internal fun formatDuration(seconds: Int): String = "%02d:%02d".format(seconds / 60, seconds % 60)
