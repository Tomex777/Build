package com.night.cortex.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean

class NightCoreApi {
    private fun connection(url:String,method:String,token:String?=null):HttpURLConnection { val conn=URI(url).toURL().openConnection() as HttpURLConnection;conn.requestMethod=method;conn.connectTimeout=8000;conn.readTimeout=12000;conn.setRequestProperty("Accept","application/json");if(!token.isNullOrBlank())conn.setRequestProperty("Authorization","Bearer $token");return conn }
    fun health(baseUrl:String):CoreHealth=runCatching{val conn=connection("$baseUrl/health","GET");val body=readBody(conn);val json=JSONObject(body);CoreHealth(conn.responseCode in 200..299&&json.optBoolean("ok",false),json.optString("service","night-core"))}.getOrElse{CoreHealth(false,detail=it.message)}
    fun bootstrap(config:CoreConnection)=getObject(config,"/api/cortex/bootstrap").toCoreSnapshot()
    fun chats(config:CoreConnection,limit:Int=100)=getArray(config,"/api/cortex/inbox/chats?limit=${limit.coerceIn(1,500)}").toInboxChats()
    fun messages(config:CoreConnection,jid:String,limit:Int=100):List<InboxMessage>{val encoded=URLEncoder.encode(jid,StandardCharsets.UTF_8.toString()).replace("+","%20");return getArray(config,"/api/cortex/inbox/chats/$encoded/messages?limit=${limit.coerceIn(1,500)}").toInboxMessages()}
    fun sendText(config:CoreConnection,jid:String,text:String){val encoded=URLEncoder.encode(jid,StandardCharsets.UTF_8.toString()).replace("+","%20");postObject(config,"/api/cortex/inbox/chats/$encoded/messages",JSONObject().put("text",text))}
    fun streamEvents(config:CoreConnection,running:AtomicBoolean,onEvent:()->Unit){while(running.get()){try{val conn=connection("${config.baseUrl}/api/cortex/events","GET",config.token);conn.readTimeout=0;conn.setRequestProperty("Accept","text/event-stream");if(conn.responseCode !in 200..299){conn.disconnect();Thread.sleep(3000);continue};BufferedReader(InputStreamReader(conn.inputStream)).use{reader->while(running.get()){val line=reader.readLine()?:break;if(line.startsWith("data:"))onEvent()}};conn.disconnect()}catch(_:Throwable){if(running.get())Thread.sleep(3000)}}}
    private fun getObject(config:CoreConnection,path:String):JSONObject{val conn=connection(config.baseUrl+path,"GET",config.token);val body=readBody(conn);check(conn.responseCode in 200..299){"HTTP ${conn.responseCode}: $body"};return JSONObject(body)}
    private fun getArray(config:CoreConnection,path:String):JSONArray{val conn=connection(config.baseUrl+path,"GET",config.token);val body=readBody(conn);check(conn.responseCode in 200..299){"HTTP ${conn.responseCode}: $body"};return JSONArray(body)}
    private fun postObject(config:CoreConnection,path:String,body:JSONObject):JSONObject{val conn=connection(config.baseUrl+path,"POST",config.token);conn.doOutput=true;conn.setRequestProperty("Content-Type","application/json; charset=utf-8");conn.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))};val response=readBody(conn);check(conn.responseCode in 200..299){"HTTP ${conn.responseCode}: $response"};return JSONObject(response)}
    private fun readBody(conn:HttpURLConnection):String{val stream=if(conn.responseCode in 200..299)conn.inputStream else conn.errorStream;return stream?.bufferedReader()?.use{it.readText()}.orEmpty()}
}
