package com.night.cortex.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.night.cortex.data.*
import com.night.cortex.hosting.HostingProviderId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

data class CortexUiState(val connection:CoreConnection=CoreConnection(),val health:CoreHealth?=null,val snapshot:CoreSnapshot?=null,val chats:List<InboxChat> = emptyList(),val messages:List<InboxMessage> = emptyList(),val activeChat:InboxChat?=null,val provider:HostingProviderId=HostingProviderId.AZURE,val loading:Boolean=false,val error:String?=null,val localFileCount:Int=0)

class CortexViewModel(application: Application) : AndroidViewModel(application) {
    private val repo=CortexRepository(application)
    private val _state=MutableStateFlow(CortexUiState(connection=CoreConnection(baseUrl=repo.rememberedBaseUrl()),provider=repo.provider()))
    val state:StateFlow<CortexUiState> = _state.asStateFlow()
    private var eventsJob:Job?=null
    private var eventsRunning:AtomicBoolean?=null
    init{refreshLocalFiles();if(_state.value.connection.configured)connect()}
    fun saveConnection(baseUrl:String,token:String){repo.rememberBaseUrl(baseUrl);_state.value=_state.value.copy(connection=CoreConnection(baseUrl.trim().removeSuffix("/"),token.trim()),error=null);connect()}
    fun setProvider(provider:HostingProviderId){repo.setProvider(provider);_state.value=_state.value.copy(provider=provider)}
    fun connect(){val config=_state.value.connection;if(!config.configured)return;viewModelScope.launch{_state.value=_state.value.copy(loading=true,error=null);val result=runCatching{withContext(Dispatchers.IO){Triple(repo.health(config),repo.bootstrap(config),repo.chats(config))}};result.onSuccess{(health,snapshot,chats)->_state.value=_state.value.copy(health=health,snapshot=snapshot,chats=chats,loading=false,error=null);startEvents()}.onFailure{_state.value=_state.value.copy(health=CoreHealth(false,detail=it.message),loading=false,error=it.message?:"Connection failed")}}}
    fun refresh(){val config=_state.value.connection;if(!config.configured)return;viewModelScope.launch{runCatching{withContext(Dispatchers.IO){repo.bootstrap(config) to repo.chats(config)}}.onSuccess{(snapshot,chats)->_state.value=_state.value.copy(snapshot=snapshot,chats=chats,error=null)}}}
    fun openChat(chat:InboxChat){val config=_state.value.connection;_state.value=_state.value.copy(activeChat=chat,messages=emptyList());if(!config.configured)return;viewModelScope.launch{runCatching{withContext(Dispatchers.IO){repo.messages(config,chat.jid)}}.onSuccess{_state.value=_state.value.copy(messages=it)}.onFailure{_state.value=_state.value.copy(error=it.message)}}}
    fun closeChat(){_state.value=_state.value.copy(activeChat=null,messages=emptyList())}
    fun send(text:String){val config=_state.value.connection;val chat=_state.value.activeChat?:return;if(text.isBlank()||!config.configured)return;viewModelScope.launch{runCatching{withContext(Dispatchers.IO){repo.sendText(config,chat.jid,text)}}.onSuccess{delay(250);openChat(chat);refresh()}.onFailure{_state.value=_state.value.copy(error=it.message)}}}
    private fun refreshLocalFiles(){viewModelScope.launch(Dispatchers.IO){val count=repo.localFiles().size;withContext(Dispatchers.Main){_state.value=_state.value.copy(localFileCount=count)}}}
    private fun startEvents(){eventsRunning?.set(false);eventsJob?.cancel();val config=_state.value.connection;if(!config.configured)return;val running=AtomicBoolean(true);eventsRunning=running;eventsJob=viewModelScope.launch(Dispatchers.IO){repo.streamEvents(config,running){viewModelScope.launch{refresh()}}}}
    override fun onCleared(){eventsRunning?.set(false);super.onCleared()}
}
