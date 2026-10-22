/* NIGHT Play – sampled acoustic piano (not oscillators).
 * Real Yamaha C5 Salamander Grand Piano V3 recordings: Alexander Holm,
 * CC BY 3.0. See PIANO-SAMPLES-LICENSE.md. Samples served by NIGHT Play.
 *
 * All notes use one of 10 recordings, pitch-adjusted no more than a few
 * semitones. The per-note gain envelope follows piano decay naturally,
 * with proper overlapping voices and gentle key releases.
 */
(()=>{
'use strict';
const ROOT='/piano-tiles/samples/';
const MIDI={C3:48,Fs3:54,A3:57,C4:60,Ds4:63,Fs4:66,A4:69,C5:72,Ds5:75,Fs5:78};
const KEYS=Object.keys(MIDI);
let context=null,buffers=new Map(),loadPromise=null,master=null,compressor=null;
const active=new Set();
let lastPlayed=null,decodeCount=0;
function audio(){
 if(!context){const AC=window.AudioContext||window.webkitAudioContext;if(!AC)throw Error('Web Audio is unavailable');
  context=new AC({latencyHint:'interactive'});
  compressor=context.createDynamicsCompressor();
  compressor.threshold.value=-14;compressor.knee.value=14;compressor.ratio.value=2.2;
  compressor.attack.value=.007;compressor.release.value=.19;
  master=context.createGain();master.gain.value=.72;
  compressor.connect(master);master.connect(context.destination);
 }
 return context;
}
async function prepare(){
 const ctx=audio();
 if(ctx.state!=='running')await ctx.resume();
 if(buffers.size===KEYS.length)return;
 if(!loadPromise){
  loadPromise=Promise.all(KEYS.map(async key=>{
   const response=await fetch(ROOT+key+'.mp3',{cache:'force-cache'});
   if(!response.ok)throw Error('Grand piano sample '+key+' unavailable ('+response.status+')');
   const encoded=await response.arrayBuffer();
   const decoded=await ctx.decodeAudioData(encoded);
   if(decoded.duration<.25||!decoded.length)throw Error('Invalid grand piano recording: '+key);
   // The piano tile instrument releases every key within ~3 seconds.
   // Keeping the full 16-second source per pitch wastes ~55 MB decoded on
   // Android. Retain 3.6 seconds, including original attack/string decay.
   const length=Math.min(decoded.length,Math.floor(decoded.sampleRate*3.6));
   const compact=ctx.createBuffer(decoded.numberOfChannels,length,decoded.sampleRate);
   for(let channel=0;channel<decoded.numberOfChannels;channel++)
    compact.copyToChannel(decoded.getChannelData(channel).subarray(0,length),channel);
   buffers.set(key,compact);decodeCount++;
  })).catch(error=>{buffers.clear();loadPromise=null;throw error});
 }
 await loadPromise;
}
function nearest(midi){
 let key=KEYS[0],smallest=Infinity;
 for(const candidate of KEYS){const distance=Math.abs(MIDI[candidate]-midi);
  if(distance<smallest){key=candidate;smallest=distance}
 }
 return key;
}
function play(frequency,hold=false){
 if(!context||buffers.size!==KEYS.length||!Number.isFinite(frequency)||frequency<=0)throw Error('Grand piano not ready');
 if(context.state==='suspended')context.resume().catch(()=>{});
 // Polyphony guard prevents runaway memory/CPU on fast mobile taps.
 if(active.size>=28){const old=active.values().next().value;old.release(.045)}
 const now=context.currentTime;
 const midi=69+12*Math.log2(frequency/440),key=nearest(midi);
 const source=context.createBufferSource(),amp=context.createGain();
 source.buffer=buffers.get(key);
 source.playbackRate.value=Math.pow(2,(midi-MIDI[key])/12);
 // Slightly humanised velocity while respecting the recorded hammer attack.
 const velocity=(hold?.31:.36)*(.95+Math.random()*.10);
 amp.gain.setValueAtTime(.0001,now);
 amp.gain.exponentialRampToValueAtTime(velocity,now+.008);
 amp.gain.exponentialRampToValueAtTime(velocity*.62,now+.48);
 amp.gain.exponentialRampToValueAtTime(velocity*.24,now+1.6);
 source.connect(amp);amp.connect(compressor);
 let stopped=false,ended=false;
 const voice={
  key,frequency,hold,started:now,
  release(seconds=.27){
   if(stopped)return;stopped=true;
   const t=context.currentTime;
   if(typeof amp.gain.cancelAndHoldAtTime==='function'){
    amp.gain.cancelAndHoldAtTime(t);
   }else{
    const current=Math.max(.0001,amp.gain.value);
    amp.gain.cancelScheduledValues(t);
    amp.gain.setValueAtTime(current,t);
   }
   amp.gain.exponentialRampToValueAtTime(.0001,t+seconds);
   try{source.stop(t+seconds+.025)}catch{}
  },
  stop(){this.release(.07);}
 };
 source.onended=()=>{ended=true;active.delete(voice);source.disconnect();amp.disconnect()};
 source.start(now,0);
 // A tap is an actual piano strike, not a 300ms synth beep; let it
 // ring for a natural decay even after its tile disappears.
 if(!hold){
  amp.gain.exponentialRampToValueAtTime(.0001,now+2.7);
  source.stop(now+2.75);
 }
 active.add(voice);lastPlayed={key,frequency,playbackRate:source.playbackRate.value,hold,started:now};
 return voice;
}
function silence(){for(const voice of [...active])voice.stop()}
function inspect(){return {loaded:buffers.size,expected:KEYS.length,decoded:decodeCount,
 activeVoices:active.size,contextState:context?.state||'none',
 sampleType:'real recorded piano MP3',lastPlayed,
 durations:Object.fromEntries([...buffers].map(([k,v])=>[k,Math.round(v.duration*100)/100]))}}
window.NightPiano=Object.freeze({prepare,play,silence,inspect});
})();
