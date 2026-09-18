package com.night.homira.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LocalCallHistoryRecord(
    val id: String,
    val peerUserId: String,
    val peerName: String,
    val peerNumber: String,
    val direction: String,
    val mediaType: String,
    val outcome: String,
    val startedAt: Long,
    val answeredAt: Long? = null,
    val endedAt: Long? = null
) {
    val durationSeconds: Long?
        get() {
            val answered = answeredAt ?: return null
            val ended = endedAt ?: return null
            return ((ended - answered) / 1000L).coerceAtLeast(0L)
        }
}

class HomiraCallHistoryStore(context: Context) {
    private val helper = Helper(context.applicationContext)

    suspend fun listRecent(limit: Int = 250): List<LocalCallHistoryRecord> =
        withContext(Dispatchers.IO) {
            helper.readableDatabase.query(
                TABLE,
                COLUMNS,
                null,
                null,
                null,
                null,
                "$COL_STARTED_AT DESC",
                limit.coerceIn(1, 1000).toString()
            ).use { cursor ->
                buildList {
                    val idIndex = cursor.getColumnIndexOrThrow(COL_ID)
                    val peerUserIdIndex = cursor.getColumnIndexOrThrow(COL_PEER_USER_ID)
                    val peerNameIndex = cursor.getColumnIndexOrThrow(COL_PEER_NAME)
                    val peerNumberIndex = cursor.getColumnIndexOrThrow(COL_PEER_NUMBER)
                    val directionIndex = cursor.getColumnIndexOrThrow(COL_DIRECTION)
                    val mediaTypeIndex = cursor.getColumnIndexOrThrow(COL_MEDIA_TYPE)
                    val outcomeIndex = cursor.getColumnIndexOrThrow(COL_OUTCOME)
                    val startedAtIndex = cursor.getColumnIndexOrThrow(COL_STARTED_AT)
                    val answeredAtIndex = cursor.getColumnIndexOrThrow(COL_ANSWERED_AT)
                    val endedAtIndex = cursor.getColumnIndexOrThrow(COL_ENDED_AT)

                    while (cursor.moveToNext()) {
                        add(
                            LocalCallHistoryRecord(
                                id = cursor.getString(idIndex),
                                peerUserId = cursor.getString(peerUserIdIndex),
                                peerName = cursor.getString(peerNameIndex),
                                peerNumber = cursor.getString(peerNumberIndex),
                                direction = cursor.getString(directionIndex),
                                mediaType = cursor.getString(mediaTypeIndex),
                                outcome = cursor.getString(outcomeIndex),
                                startedAt = cursor.getLong(startedAtIndex),
                                answeredAt = if (cursor.isNull(answeredAtIndex)) {
                                    null
                                } else {
                                    cursor.getLong(answeredAtIndex)
                                },
                                endedAt = if (cursor.isNull(endedAtIndex)) {
                                    null
                                } else {
                                    cursor.getLong(endedAtIndex)
                                }
                            )
                        )
                    }
                }
            }
        }

    suspend fun recordRinging(
        id: String,
        peerUserId: String,
        peerName: String,
        peerNumber: String,
        direction: String,
        mediaType: String,
        startedAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val existing = find(id)
        if (existing != null) return@withContext

        val values = ContentValues().apply {
            put(COL_ID, id)
            put(COL_PEER_USER_ID, peerUserId)
            put(COL_PEER_NAME, peerName)
            put(COL_PEER_NUMBER, peerNumber)
            put(COL_DIRECTION, direction)
            put(COL_MEDIA_TYPE, mediaType)
            put(COL_OUTCOME, OUTCOME_RINGING)
            put(COL_STARTED_AT, startedAt)
        }
        helper.writableDatabase.insertOrThrow(TABLE, null, values)
    }

    suspend fun markAnswered(
        id: String,
        answeredAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(COL_OUTCOME, OUTCOME_ANSWERED)
            put(COL_ANSWERED_AT, answeredAt)
        }
        helper.writableDatabase.update(
            TABLE,
            values,
            "$COL_ID = ?",
            arrayOf(id)
        )
    }

    suspend fun markTerminal(
        id: String,
        outcome: String,
        endedAt: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val safeOutcome = when (outcome) {
            OUTCOME_ANSWERED,
            OUTCOME_MISSED,
            OUTCOME_DECLINED,
            OUTCOME_CANCELLED,
            OUTCOME_FAILED -> outcome
            else -> OUTCOME_FAILED
        }
        val existing = find(id)
        val finalOutcome = if (
            safeOutcome == OUTCOME_ANSWERED ||
            existing?.answeredAt != null
        ) {
            OUTCOME_ANSWERED
        } else {
            safeOutcome
        }

        val values = ContentValues().apply {
            put(COL_OUTCOME, finalOutcome)
            put(COL_ENDED_AT, endedAt)
        }
        helper.writableDatabase.update(
            TABLE,
            values,
            "$COL_ID = ?",
            arrayOf(id)
        )
    }

    suspend fun normalizeInterruptedRinging(
        staleAfterMs: Long = 15 * 60 * 1000L
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cutoff = now - staleAfterMs.coerceAtLeast(60_000L)

        val values = ContentValues().apply {
            put(COL_OUTCOME, OUTCOME_MISSED)
            put(COL_ENDED_AT, now)
        }
        helper.writableDatabase.update(
            TABLE,
            values,
            "$COL_OUTCOME = ? AND $COL_DIRECTION = ? AND $COL_STARTED_AT < ?",
            arrayOf(
                OUTCOME_RINGING,
                DIRECTION_INCOMING,
                cutoff.toString()
            )
        )

        values.clear()
        values.put(COL_OUTCOME, OUTCOME_CANCELLED)
        values.put(COL_ENDED_AT, now)
        helper.writableDatabase.update(
            TABLE,
            values,
            "$COL_OUTCOME = ? AND $COL_DIRECTION = ? AND $COL_STARTED_AT < ?",
            arrayOf(
                OUTCOME_RINGING,
                DIRECTION_OUTGOING,
                cutoff.toString()
            )
        )
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, null, null)
    }

    private fun find(id: String): LocalCallHistoryRecord? =
        helper.readableDatabase.query(
            TABLE,
            COLUMNS,
            "$COL_ID = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null

            LocalCallHistoryRecord(
                id = cursor.getString(cursor.getColumnIndexOrThrow(COL_ID)),
                peerUserId = cursor.getString(cursor.getColumnIndexOrThrow(COL_PEER_USER_ID)),
                peerName = cursor.getString(cursor.getColumnIndexOrThrow(COL_PEER_NAME)),
                peerNumber = cursor.getString(cursor.getColumnIndexOrThrow(COL_PEER_NUMBER)),
                direction = cursor.getString(cursor.getColumnIndexOrThrow(COL_DIRECTION)),
                mediaType = cursor.getString(cursor.getColumnIndexOrThrow(COL_MEDIA_TYPE)),
                outcome = cursor.getString(cursor.getColumnIndexOrThrow(COL_OUTCOME)),
                startedAt = cursor.getLong(cursor.getColumnIndexOrThrow(COL_STARTED_AT)),
                answeredAt = cursor.getColumnIndexOrThrow(COL_ANSWERED_AT).let { index ->
                    if (cursor.isNull(index)) null else cursor.getLong(index)
                },
                endedAt = cursor.getColumnIndexOrThrow(COL_ENDED_AT).let { index ->
                    if (cursor.isNull(index)) null else cursor.getLong(index)
                }
            )
        }

    private class Helper(context: Context) :
        SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    $COL_ID TEXT PRIMARY KEY NOT NULL,
                    $COL_PEER_USER_ID TEXT NOT NULL,
                    $COL_PEER_NAME TEXT NOT NULL,
                    $COL_PEER_NUMBER TEXT NOT NULL DEFAULT '',
                    $COL_DIRECTION TEXT NOT NULL,
                    $COL_MEDIA_TYPE TEXT NOT NULL,
                    $COL_OUTCOME TEXT NOT NULL,
                    $COL_STARTED_AT INTEGER NOT NULL,
                    $COL_ANSWERED_AT INTEGER,
                    $COL_ENDED_AT INTEGER
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX idx_homira_call_history_started_at ON $TABLE($COL_STARTED_AT DESC)"
            )
        }

        override fun onUpgrade(
            db: SQLiteDatabase,
            oldVersion: Int,
            newVersion: Int
        ) {
            // Version 1 is the first local-only Homira call-history schema.
        }
    }

    companion object {
        const val DIRECTION_INCOMING = "incoming"
        const val DIRECTION_OUTGOING = "outgoing"

        const val OUTCOME_RINGING = "ringing"
        const val OUTCOME_ANSWERED = "answered"
        const val OUTCOME_MISSED = "missed"
        const val OUTCOME_DECLINED = "declined"
        const val OUTCOME_CANCELLED = "cancelled"
        const val OUTCOME_FAILED = "failed"

        private const val DB_NAME = "homira_calls.db"
        private const val DB_VERSION = 1
        private const val TABLE = "call_history"

        private const val COL_ID = "id"
        private const val COL_PEER_USER_ID = "peer_user_id"
        private const val COL_PEER_NAME = "peer_name"
        private const val COL_PEER_NUMBER = "peer_number"
        private const val COL_DIRECTION = "direction"
        private const val COL_MEDIA_TYPE = "media_type"
        private const val COL_OUTCOME = "outcome"
        private const val COL_STARTED_AT = "started_at"
        private const val COL_ANSWERED_AT = "answered_at"
        private const val COL_ENDED_AT = "ended_at"

        private val COLUMNS = arrayOf(
            COL_ID,
            COL_PEER_USER_ID,
            COL_PEER_NAME,
            COL_PEER_NUMBER,
            COL_DIRECTION,
            COL_MEDIA_TYPE,
            COL_OUTCOME,
            COL_STARTED_AT,
            COL_ANSWERED_AT,
            COL_ENDED_AT
        )
    }
}
