/* NIGHT Play shared, gesture-unlocked, effects-only Web Audio engine.
   Original synthesized sounds; no third-party samples or background music. */
(()=>{
  "use strict";
  const KEY="night-play-sfx-enabled";
  const Context=window.AudioContext||window.webkitAudioContext;
  let enabled=true,ctx=null,master=null,noiseBuffer=null,unlocked=false;
  const last=new Map();
  try{enabled=localStorage.getItem(KEY)!=="false";}catch{}
  function unlock(){
    unlocked=true;
    if(!enabled||!Context)return;
    try{
      if(!ctx){
        ctx=new Context();
        master=ctx.createGain();master.gain.value=0.64;
        const limiter=ctx.createDynamicsCompressor();
        limiter.threshold.value=-14;limiter.knee.value=16;limiter.ratio.value=9;
        limiter.attack.value=0.002;limiter.release.value=0.12;
        master.connect(limiter);limiter.connect(ctx.destination);
        noiseBuffer=ctx.createBuffer(1,Math.ceil(ctx.sampleRate*.19),ctx.sampleRate);
        const data=noiseBuffer.getChannelData(0);
        for(let i=0;i<data.length;i++)data[i]=(Math.random()*2-1);
      }
      if(ctx.state==="suspended")ctx.resume().catch(()=>{});
    }catch{ctx=null;master=null;}
  }
  // Context creation must originate from a user gesture on mobile Safari/Chrome.
  document.addEventListener("pointerdown",unlock,{capture:true,passive:true});
  document.addEventListener("keydown",unlock,{capture:true});
  function tone(from,to=from,duration=.09,volume=.09,wave="sine",delay=0){
    const at=ctx.currentTime+delay;
    const oscillator=ctx.createOscillator(),gain=ctx.createGain();
    oscillator.type=wave;
    oscillator.frequency.setValueAtTime(from,at);
    oscillator.frequency.exponentialRampToValueAtTime(Math.max(28,to),at+duration);
    gain.gain.setValueAtTime(.0001,at);
    gain.gain.exponentialRampToValueAtTime(Math.max(.0002,volume),at+.005);
    gain.gain.exponentialRampToValueAtTime(.0001,at+duration);
    oscillator.connect(gain);gain.connect(master);
    oscillator.start(at);oscillator.stop(at+duration+.008);
  }
  function texture(duration=.06,volume=.06,freq=1100,delay=0){
    if(!noiseBuffer)return;
    const at=ctx.currentTime+delay;
    const source=ctx.createBufferSource(),filter=ctx.createBiquadFilter(),gain=ctx.createGain();
    source.buffer=noiseBuffer;
    filter.type="bandpass";filter.frequency.value=freq;filter.Q.value=.7;
    gain.gain.setValueAtTime(.0001,at);
    gain.gain.exponentialRampToValueAtTime(volume,at+.003);
    gain.gain.exponentialRampToValueAtTime(.0001,at+duration);
    source.connect(filter);filter.connect(gain);gain.connect(master);
    source.start(at);source.stop(at+duration+.004);
  }
  const tap=(v=.045)=>texture(.026,v,1800);
  function play(name,detail=0){
    if(!enabled||document.hidden||!unlocked||!ctx||ctx.state!=="running")return false;
    const now=performance.now(),spacing=name==="dot"?85:name==="move"?52:name==="deal"?70:38;
    if(now-(last.get(name)||-Infinity)<spacing)return false;
    last.set(name,now);
    try{
      switch(name){
        case "move": tap(.022);break;
        case "rotate":tone(410,560,.062,.042,"triangle");break;
        case "land":texture(.055,.07,480);tone(160,78,.105,.095,"triangle");break;
        case "drop":texture(.085,.09,410);tone(220,64,.16,.13,"triangle");break;
        case "clear": {
          const count=Math.min(4,Math.max(1,Number(detail)||1));
          for(let i=0;i<count;i++)tone(440+i*95,660+i*130,.13,.07,"sine",i*.07);
          break;
        }
        case "flap":texture(.063,.048,1200);tone(350,260,.09,.025,"sine");break;
        case "point":tone(650,890,.11,.068,"sine");break;
        case "eat":tone(610,920,.1,.075,"triangle");break;
        case "merge": {
          const val=Math.min(2048,Math.max(4,Number(detail)||4));
          const semis=Math.min(12,Math.floor(Math.log2(val))-2);
          const hz=290*Math.pow(2,semis/12);
          tone(hz,hz*1.3,.13,.072,"sine");break;
        }
        case "place":texture(.038,.068,670);tone(245,160,.07,.034,"triangle");break;
        case "capture":texture(.085,.11,440);tone(190,110,.12,.09,"triangle");break;
        case "king":tone(460,610,.14,.07,"sine");tone(690,820,.18,.068,"sine",.11);break;
        case "deal":texture(.045,.047,1650);break;
        case "card":texture(.082,.075,1100);tone(200,145,.065,.021,"triangle");break;
        case "draw":texture(.10,.052,1300);break;
        case "special":tone(490,590,.09,.065,"triangle");tone(690,740,.12,.05,"triangle",.085);break;
        case "dice":texture(.12,.075,690);tone(130,100,.1,.03,"triangle");break;
        case "ladder":tone(390,590,.14,.065,"sine");tone(630,880,.15,.053,"sine",.11);break;
        case "slide":tone(560,230,.2,.065,"triangle");break;
        case "reveal":tap(.04);break;
        case "flag":tone(385,330,.07,.04,"triangle");break;
        case "mine":texture(.17,.13,330);tone(125,48,.25,.14,"triangle");break;
        case "dot":tone(700,660,.035,.023,"sine");break;
        case "ghost":tone(280,170,.16,.085,"triangle");break;
        case "pickup":tone(310,410,.065,.04,"sine");break;
        case "snap":texture(.035,.045,850);tone(520,730,.1,.06,"sine");break;
        case "win": {
          [523,659,784].forEach((f,i)=>tone(f,f,.16,.065,"sine",i*.095));
          break;
        }
        case "lose":tone(400,280,.15,.065,"triangle");tone(235,165,.19,.056,"triangle",.11);break;
        default:return false;
      }
      return true;
    }catch{return false;}
  }
  function sync(){
    document.querySelectorAll("[data-night-sfx-toggle]").forEach(button=>{
      button.setAttribute("aria-checked",String(enabled));
      const value=button.querySelector("[data-night-sfx-value]");
      if(value)value.textContent=enabled?"On":"Off";
    });
  }
  function setEnabled(value){
    enabled=Boolean(value);
    try{localStorage.setItem(KEY,String(enabled));}catch{}
    sync();
    if(enabled){unlock();play("place");}
    return enabled;
  }
  function attachToggle(parent){
    if(!parent||parent.querySelector("[data-night-sfx-toggle]"))return;
    const row=document.createElement("div");row.className="night-sfx-setting";
    row.innerHTML='<span class="night-sfx-label">Sound effects</span><button class="night-sfx-switch" type="button" role="switch" aria-label="Sound effects" data-night-sfx-toggle aria-checked="true"><span class="night-sfx-track"><span class="night-sfx-knob"></span></span><span class="night-sfx-state" data-night-sfx-value>On</span></button>';
    row.querySelector("button").addEventListener("click",()=>setEnabled(!enabled));
    parent.appendChild(row);sync();return row;
  }
  addEventListener("storage",e=>{if(e.key===KEY){enabled=e.newValue!=="false";sync();}});
  addEventListener("pagehide",()=>{if(ctx&&ctx.state==="running")ctx.suspend().catch(()=>{});});
  window.NightAudio=Object.freeze({play,attachToggle,setEnabled,get enabled(){return enabled;}});
  // Tabletop/arcade settings have persistent DOM; main game's sheet is rebuilt
  // when opened, so its caller mounts the toggle after its settings controls.
  document.addEventListener("DOMContentLoaded",()=>{
    const sheet=document.querySelector("#settingsDialog .night-sheet-body");
    if(sheet)attachToggle(sheet);
  });
})();
