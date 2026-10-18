/**
 * NIGHT Play original tactile puzzle sound design.
 * PlayJigsaw's production audio was used as an interaction/quality reference,
 * not copied, sampled, or redistributed. No background music.
 *
 * Small, carefully filtered transients emulate wood, cardboard and soft contact
 * instead of the piercing up/down sine chirps of the original demo.
 */
export class SoundSynth {
 constructor(){this.audioCtx=null;this.enabled=true;this.master=null;this.noise=null;this.lastPickup=0;this.lastDrop=0;}
 ensureContext(){
  if(!this.enabled)return null;
  try{
   if(!this.audioCtx){
    const Type=window.AudioContext||window.webkitAudioContext;if(!Type)return null;
    this.audioCtx=new Type({latencyHint:'interactive'});
    const dynamics=this.audioCtx.createDynamicsCompressor();
    dynamics.threshold.value=-21;dynamics.knee.value=14;dynamics.ratio.value=5;
    dynamics.attack.value=.002;dynamics.release.value=.13;
    this.master=this.audioCtx.createGain();this.master.gain.value=.48;
    this.master.connect(dynamics);dynamics.connect(this.audioCtx.destination);
    const n=this.audioCtx.createBuffer(1,Math.ceil(this.audioCtx.sampleRate*.22),this.audioCtx.sampleRate);
    const a=n.getChannelData(0);let seed=48123;
    for(let i=0;i<a.length;i++){seed=(Math.imul(seed,1664525)+1013904223)|0;a[i]=(seed/2147483648)*.84;}
    this.noise=n;
   }
   if(this.audioCtx.state==='suspended')this.audioCtx.resume()?.catch?.(()=>{});
   return this.audioCtx;
  }catch{return null;}
 }
 toggle(){this.enabled=!this.enabled;return this.enabled;}
 // All envelopes end at silence: each voice is disconnected automatically.
 tone(freq,{when=0,duration=.09,volume=.07,type='sine',ending=.8}={}){
  const ctx=this.audioCtx;if(!ctx||!this.master)return;
  const t=ctx.currentTime+when;
  const oscillator=ctx.createOscillator(),gain=ctx.createGain();
  oscillator.type=type;oscillator.frequency.setValueAtTime(freq,t);
  oscillator.frequency.exponentialRampToValueAtTime(Math.max(35,freq*ending),t+duration);
  gain.gain.setValueAtTime(.0001,t);
  gain.gain.exponentialRampToValueAtTime(Math.max(.001,volume),t+.004);
  gain.gain.exponentialRampToValueAtTime(.0001,t+duration);
  oscillator.connect(gain);gain.connect(this.master);
  oscillator.start(t);oscillator.stop(t+duration+.005);
  oscillator.onended=()=>{oscillator.disconnect();gain.disconnect();};
 }
 rustle({when=0,duration=.07,volume=.13,frequency=1200,quality=.75}={}){
  const ctx=this.audioCtx;if(!ctx||!this.master||!this.noise)return;
  const t=ctx.currentTime+when,source=ctx.createBufferSource(),filter=ctx.createBiquadFilter(),gain=ctx.createGain();
  source.buffer=this.noise;filter.type='bandpass';filter.frequency.value=frequency;filter.Q.value=quality;
  gain.gain.setValueAtTime(.0001,t);
  gain.gain.exponentialRampToValueAtTime(Math.max(.001,volume),t+.003);
  gain.gain.exponentialRampToValueAtTime(.0001,t+duration);
  source.connect(filter);filter.connect(gain);gain.connect(this.master);
  source.start(t,Math.random()*.08,duration+.003);
  source.stop(t+duration+.004);
  source.onended=()=>{source.disconnect();filter.disconnect();gain.disconnect();};
 }
 playPickup(){
  if(!this.ensureContext())return;
  const now=performance.now();if(now-this.lastPickup<65)return;this.lastPickup=now;
  const variation=.94+Math.random()*.12;
  this.rustle({duration:.054,volume:.075,frequency:1500*variation,quality:.72});
  this.tone(214*variation,{when:.007,duration:.064,volume:.038,ending:.72});
 }
 playDrop(){
  if(!this.ensureContext())return;
  const now=performance.now();if(now-this.lastDrop<54)return;this.lastDrop=now;
  const variation=.91+Math.random()*.18;
  this.rustle({duration:.06,volume:.095,frequency:640*variation,quality:.65});
  this.tone(153*variation,{when:.004,duration:.10,volume:.082,ending:.54,type:'triangle'});
 }
 playSnap({joined=false}={}){
  if(!this.ensureContext())return;
  const variation=.95+Math.random()*.11;
  // A two-part wooden click: firm initial contact followed by a short latch.
  this.rustle({duration:.035,volume:.16,frequency:1720*variation,quality:1.3});
  this.tone(380*variation,{duration:.07,volume:.065,ending:.67,type:'triangle'});
  this.rustle({when:.034,duration:.057,volume:.105,frequency:1150*variation,quality:1});
  this.tone(joined?445*variation:325*variation,{when:.036,duration:.11,volume:.048,ending:.81});
 }
 playVictory(){
  if(!this.ensureContext())return;
  // Short resolving confirmation, not an unrelated music track.
  this.playSnap({joined:true});
  [440,554.37,659.25,880].forEach((freq,i)=>{
   this.tone(freq,{when:.105+i*.092,duration:.22,volume:.075,type:'sine',ending:1.01});
  });
  this.rustle({when:.49,duration:.12,volume:.06,frequency:2150,quality:.55});
 }
}
