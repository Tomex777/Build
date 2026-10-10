package dev.velvet.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import org.json.JSONArray
import org.json.JSONObject

/** A deck remembers your place and bookmarked cards, even without internet. */
internal object VelvetQuestionProgress {
    val positions=mutableStateMapOf<String,Int>()
    val saved=mutableStateListOf<String>()
    fun load(context:Context) {
        val prefs=context.getSharedPreferences("velvet_question_progress_v1",Context.MODE_PRIVATE)
        positions.clear();saved.clear()
        runCatching{
            val json=JSONObject(prefs.getString("positions","{}"))
            val keys=json.keys()
            while(keys.hasNext()) {
                val key=keys.next();positions[key]=json.optInt(key,0)
            }
            val marks=JSONArray(prefs.getString("saved","[]"))
            repeat(marks.length()){saved.add(marks.getString(it))}
        }
    }
    fun save(context:Context) {
        val json=JSONObject()
        positions.forEach{(key,index)->json.put(key,index)}
        val marks=JSONArray();saved.forEach{marks.put(it)}
        context.getSharedPreferences("velvet_question_progress_v1",Context.MODE_PRIVATE)
            .edit().putString("positions",json.toString()).putString("saved",marks.toString()).apply()
    }
}
