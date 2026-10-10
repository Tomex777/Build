package dev.velvet.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

/** Temporary, on-device persistence for the alpha. Supabase/Room will replace this with a sync layer. */
internal class MessageCache(context: Context): SQLiteOpenHelper(context, "velvet_messages_v1.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY, record TEXT NOT NULL)")
        db.execSQL("CREATE TABLE flags (name TEXT PRIMARY KEY, value TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) { /* Versioned migrations when required. */ }

    /** null means first launch, empty list means user intentionally deleted every message. */
    fun load():List<ChatMessage>? {
        val db=readableDatabase
        val initialized=db.rawQuery("SELECT value FROM flags WHERE name = ?",arrayOf("initialized")).use{it.moveToFirst()}
        if(!initialized)return null
        val messages=mutableListOf<ChatMessage>()
        db.rawQuery("SELECT record FROM messages ORDER BY id ASC", null).use {cursor->
            while(cursor.moveToNext()){
                try { messages.add(fromJson(JSONObject(cursor.getString(0)))) } catch(_:Exception) { /* Skip damaged entries. */ }
            }
        }
        return messages
    }

    fun save(all:List<ChatMessage>) {
        val db=writableDatabase
        db.beginTransaction()
        try {
            db.delete("messages",null,null)
            all.forEach {msg ->
                val row=ContentValues().apply {put("id",msg.id);put("record",toJson(msg).toString())}
                db.insertOrThrow("messages",null,row)
            }
            val flag=ContentValues().apply{put("name","initialized");put("value","1")}
            db.insertWithOnConflict("flags",null,flag,SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        }finally{db.endTransaction()}
    }

    private fun toJson(m:ChatMessage):JSONObject = JSONObject().apply {
        put("id",m.id);put("body",m.body);put("mine",m.mine);put("time",m.time)
        put("quoted",m.quoted ?: JSONObject.NULL);put("pinned",m.pinned);put("starred",m.starred)
        put("deleted",m.deleted);put("kind",m.kind.name)
        put("category",m.category ?: JSONObject.NULL);put("cardTone",m.cardTone)
        put("questionId",m.questionId ?: JSONObject.NULL);put("caption",m.caption ?: JSONObject.NULL)
        put("voicePath",m.voicePath ?: JSONObject.NULL)
        put("voiceBars",JSONArray(m.voiceBars));put("durationMs",m.durationMs)
        put("mediaPath",m.mediaPath ?: JSONObject.NULL);put("mediaMime",m.mediaMime ?: JSONObject.NULL)
    }
    private fun fromJson(o:JSONObject):ChatMessage {
        val arr=o.optJSONArray("voiceBars") ?: JSONArray()
        val samples=(0 until arr.length()).map{arr.optDouble(it,.03).toFloat()}
        val kind=runCatching{MessageKind.valueOf(o.optString("kind","TEXT"))}.getOrDefault(MessageKind.TEXT)
        return ChatMessage(
            id=o.getInt("id"),body=o.optString("body"),mine=o.optBoolean("mine"),time=o.optString("time"),
            quoted=o.optString("quoted").takeUnless{it.isBlank()||it=="null"},
            pinned=o.optBoolean("pinned"),starred=o.optBoolean("starred"),deleted=o.optBoolean("deleted"),
            kind=kind,category=o.optString("category").takeUnless{it.isBlank()||it=="null"},
            cardTone=o.optInt("cardTone"),questionId=o.optString("questionId").takeUnless{it.isBlank()||it=="null"},
            caption=o.optString("caption").takeUnless{it.isBlank()||it=="null"},
            voicePath=o.optString("voicePath").takeUnless{it.isBlank()||it=="null"},
            voiceBars=samples,durationMs=o.optLong("durationMs"),
            mediaPath=o.optString("mediaPath").takeUnless{it.isBlank()||it=="null"},
            mediaMime=o.optString("mediaMime").takeUnless{it.isBlank()||it=="null"}
        )
    }
}
