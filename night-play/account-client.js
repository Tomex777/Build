(() => {
  const dialog=document.getElementById('infoDialog')
  let profile=null, timer=null, expiryTimer=null, requestId=0, busy=false, statusGeneration=0
  const api=async (action='status', extra={}) => {
    const response=await fetch('/api/account',{
      method:action==='status'?'GET':'POST',credentials:'same-origin',cache:'no-store',
      headers:action==='status'?{}:{'content-type':'application/json'},
      body:action==='status'?undefined:JSON.stringify({action,...extra}),signal:AbortSignal.timeout(15_000),
    })
    const data=await response.json()
    if(!response.ok)throw new Error(data.error||'NIGHT could not connect. Please try again.')
    return data
  }
  const element=(tag,text,className) => {const node=document.createElement(tag);if(text)node.textContent=text;if(className)node.className=className;return node}
  const stop=()=>{clearTimeout(timer);clearInterval(expiryTimer);timer=null;expiryTimer=null;requestId++}
  const update=next => {
    profile=next||null
    window.NightAccount.profile=profile
    document.documentElement.dataset.nightAccount=profile?'linked':'guest'
    // Never show 'Connect WhatsApp' until the authoritative status is known.
    for(const button of document.querySelectorAll('[data-dialog="connect"]')){
      button.textContent='Connect WhatsApp'
      button.hidden=Boolean(profile)
    }
    const caption=document.getElementById('profileIdentityCaption')
    caption.hidden=true
    caption.textContent=''
    document.getElementById('profileAccountMenu').hidden=!profile
    const avatar=document.getElementById('profileAvatar')
    const fallback=document.getElementById('profileAvatarFallback')
    const source=typeof profile?.avatarData==='string' && /^data:image\/(?:jpeg|png);base64,[A-Za-z0-9+/=]+$/.test(profile.avatarData) && profile.avatarData.length<370000 ? profile.avatarData : ''
    avatar.hidden=!source
    fallback.hidden=Boolean(source)
    if(source){avatar.onerror=()=>{avatar.hidden=true;fallback.hidden=false};avatar.src=source}
    else avatar.removeAttribute('src')
    document.getElementById('playerNameForm').querySelector('small').textContent=profile?'Saved to your NIGHT Play profile.':'Saved on this device.'
    if(typeof syncPlayerName==='function')syncPlayerName()
    if(typeof updateGreeting==='function')updateGreeting()
  }
  const show=title=>{stop();document.getElementById('infoTitle').textContent=title;const body=document.getElementById('infoBody');body.replaceChildren();if(!dialog.open)dialog.showModal();return body}
  const button=(label,fn,style='btn')=>{const el=element('button',label,style);el.type='button';el.addEventListener('click',fn);return el}
  const errorView=(message,retry)=>{const body=show('Connect WhatsApp');const note=element('p',message,'modalnote');note.setAttribute('role','status');body.append(note,button('Try again',retry))}
  const connected=()=>{
    const body=show('Your NIGHT account')
    body.append(element('p',profile.displayName,'account-name'),element('p','Connected through WhatsApp. You can use this profile on other devices by linking each browser.','modalnote'))
    const status=element('p','','modalnote');status.setAttribute('role','status')
    body.append(button('Disconnect this browser',async event=>{
      event.currentTarget.disabled=true
      try{await api('logout');statusGeneration++;update(null);dialog.close()}catch(error){status.textContent=error.message;event.currentTarget.disabled=false}
    }),button('Remove NIGHT Play account',()=>{
      const body=show('Remove NIGHT Play account?')
      body.append(element('p','This removes your website profile and signs out every linked browser. Game saves on this device remain available.','modalnote'))
      const result=element('p','','modalnote');result.setAttribute('role','status')
      body.append(button('Remove account',async event=>{
        event.currentTarget.disabled=true
        try{await api('delete');statusGeneration++;update(null);dialog.close()}catch(error){result.textContent=error.message;event.currentTarget.disabled=false}
      }),button('Keep account',connected,'btn light'),result)
    },'btn outline'),status)
  }
  const begin=async()=>{
    const body=show('Connect WhatsApp')
    body.append(element('p','Generating your code…','modalnote'))
    const id=requestId
    try{
      const data=await api('link')
      if(id!==requestId||!dialog.open)return
      body.replaceChildren()
      body.append(element('p','Send this command privately to NIGHT on WhatsApp. Use the browser that generated this code.','modalnote'))
      const command=element('code',data.command,'account-command')
      const copy=button('Copy command',async()=>{
        try{await navigator.clipboard.writeText(data.command);copy.textContent='Copied'}catch{const range=document.createRange();range.selectNodeContents(command);getSelection().removeAllRanges();getSelection().addRange(range);copy.textContent='Select and copy the command'}
      })
      const pending=element('p','Waiting for NIGHT to verify…','modalnote');pending.setAttribute('role','status');pending.setAttribute('aria-live','polite')
      const expires=element('p','','account-expiry')
      body.append(command,copy,pending,expires)
      const tick=()=>{
        const seconds=Math.max(0,Math.ceil((data.expiresAt-Date.now())/1000))
        expires.textContent='Code expires in '+Math.floor(seconds/60)+':'+String(seconds%60).padStart(2,'0')
        if(!seconds){stop();pending.textContent='This code has expired.';copy.disabled=true;body.append(button('Generate a new code',begin))}
      }
      tick();expiryTimer=setInterval(tick,1000)
      const poll=async()=>{
        if(id!==requestId||!dialog.open)return
        try{
          const state=await api()
          if(id!==requestId)return
          if(state.linked){statusGeneration++;update(state.profile);if(!state.profile?.avatarData)setTimeout(()=>api().then(v=>{if(v.linked)update(v.profile)}).catch(()=>{}),3000);const body=show('You’re connected');body.append(element('p',state.profile.displayName,'account-name'),element('p','NIGHT verified your WhatsApp account.','modalnote'),button('Continue',()=>dialog.close()));return}
          pending.textContent='Waiting for NIGHT to verify…'
        }catch(error){if(id!==requestId)return;pending.textContent=error.message}
        if(id===requestId)timer=setTimeout(poll,4000)
      }
      timer=setTimeout(poll,2000)
    }catch(error){if(id===requestId)errorView(error.message,begin)}
  }
  const checkSession=async (useBoot=false)=>{
    const version=++statusGeneration
    try {
      // Head started this same-origin fetch before the DOM finished parsing.
      // Reuse it rather than issuing a second competing status request.
      const result=useBoot&&window.__nightAccountBoot
        ? await window.__nightAccountBoot
        : {data:await api()}
      if(version!==statusGeneration)return
      if(result.error)throw new Error(result.error)
      update(result.data?.linked?result.data.profile:null)
    } catch(error) {
      if(version!==statusGeneration)return
      // A timeout or backend outage is NOT proof that the user signed out.
      // Preserve the last confirmed profile and offer a neutral retry.
      document.documentElement.dataset.nightAccount='unavailable'
      if(!profile)for(const button of document.querySelectorAll('[data-dialog="connect"]')){
        button.textContent='Check connection'
        button.hidden=false
      }
    }
  }
  window.NightAccount={profile:null,open:async()=>{
    if(busy)return;busy=true
    const body=show('Connect WhatsApp');body.append(element('p','Checking your connection…','modalnote'))
    const id=requestId
    try{const state=await api();if(id!==requestId)return;statusGeneration++;update(state.profile);if(profile)connected();else await begin()}
    catch(error){if(id===requestId)errorView(error.message,()=>window.NightAccount.open())}
    finally{busy=false}
  },saveName:async displayName=>{const data=await api('profile',{displayName});statusGeneration++;update(data.profile)}}
  document.getElementById('profileAccountMenu').addEventListener('click',()=>window.NightAccount.open())
  dialog.addEventListener('close',stop)
  document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&!dialog.open)checkSession()})
  window.addEventListener('pageshow',event=>{if(event.persisted&&!dialog.open)checkSession()})
  checkSession(true)
})()
