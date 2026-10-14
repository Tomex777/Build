/* Three.js dice for NIGHT Ludo. The renderer is loaded only after Ludo opens. */
(function(){
"use strict";
let loading=null,THREE=null,RoundedBoxGeometry=null,renderer=null,scene=null,camera=null,canvas=null,root=null,resizeObserver=null,frame=0,lastRolling=false,ready=false;
const dice=[],pips={1:[[0,0]],2:[[-1,-1],[1,1]],3:[[-1,-1],[0,0],[1,1]],4:[[-1,-1],[1,-1],[-1,1],[1,1]],5:[[-1,-1],[1,-1],[0,0],[-1,1],[1,1]],6:[[-1,-1],[1,-1],[-1,0],[1,0],[-1,1],[1,1]]};
const faceLayout=[['front',1],['back',6],['right',3],['left',4],['top',2],['bottom',5]];
function pickRoot(){return document.querySelector('.nj-dice-center');}
function values(){return [...(root?.querySelectorAll('.nj-die[data-center-die]')||[])].map(el=>Number(el.dataset.diceValue)||1);}
function reduced(){return matchMedia('(prefers-reduced-motion: reduce)').matches;}
function cube(value,index){
 const group=new THREE.Group(),box=new THREE.Mesh(new RoundedBoxGeometry(1.08,1.08,1.08,4,.095),new THREE.MeshStandardMaterial({color:0xf5f0e6,roughness:.3,metalness:.025}));
 box.castShadow=true;box.receiveShadow=true;group.add(box);
 const pipMat=new THREE.MeshStandardMaterial({color:0x741d25,roughness:.24,metalness:.02});
 for(const [side,n] of faceLayout){
  const face=new THREE.Group();
  if(side==='back')face.rotation.y=Math.PI;
  if(side==='right')face.rotation.y=Math.PI/2;
  if(side==='left')face.rotation.y=-Math.PI/2;
  if(side==='top')face.rotation.x=-Math.PI/2;
  if(side==='bottom')face.rotation.x=Math.PI/2;
  for(const [x,y] of pips[n]){const dot=new THREE.Mesh(new THREE.SphereGeometry(.073,14,10),pipMat);dot.position.set(x*.22,y*.22,.543);dot.castShadow=true;face.add(dot);}
  group.add(face);
 }
 group.position.x=index? .64:-.64;group.position.y=-.12;group.userData={index,value,shadow:null,animation:null};
 const shadow=new THREE.Mesh(new THREE.CircleGeometry(.64,32),new THREE.MeshBasicMaterial({color:0x06110f,transparent:true,opacity:.35,depthWrite:false}));
 shadow.rotation.x=-Math.PI/2;shadow.position.set(group.position.x,-.7,0);scene.add(shadow);group.userData.shadow=shadow;
 return group;
}
function targetRotation(value){
 const angles={1:[0,0,0],2:[Math.PI/2,0,0],3:[0,-Math.PI/2,0],4:[0,Math.PI/2,0],5:[-Math.PI/2,0,0],6:[0,Math.PI,0]};
 return angles[value]||angles[1];
}
function render(){if(!renderer||!root?.isConnected)return;renderer.render(scene,camera);}
function startFrame(){if(!frame)frame=requestAnimationFrame(tick);}
function tick(now){frame=0;if(!renderer||!root?.isConnected)return;let active=false;
 for(const die of dice){const a=die.userData.animation;if(!a)continue;const raw=Math.min(1,(now-a.start)/a.duration),ease=1-Math.pow(1-raw,4);die.rotation.set(a.from[0]+(a.to[0]-a.from[0])*ease,a.from[1]+(a.to[1]-a.from[1])*ease,a.from[2]+(a.to[2]-a.from[2])*ease);
  const hop=Math.sin(raw*Math.PI)*a.lift,arc=ease;die.position.set(a.fromX+(a.x-a.fromX)*arc,a.fromY+(a.y-a.fromY)*arc+hop,a.fromZ+(a.z-a.fromZ)*arc);die.scale.setScalar(1+Math.sin(raw*Math.PI)*.09);die.userData.shadow.material.opacity=.34-hop*.1;die.userData.shadow.scale.setScalar(1+hop*.45);
  if(raw>=1){die.rotation.set(...a.target);die.position.set(a.x,a.y,a.z);die.scale.setScalar(1);die.userData.shadow.material.opacity=.34;die.userData.shadow.scale.setScalar(1);die.userData.animation=null;}else active=true;
 }
 render();if(active)startFrame();
}
function moveDie(die,value,rolling,duration=680){
 const target=targetRotation(value),current=[die.rotation.x,die.rotation.y,die.rotation.z],index=die.userData.index;
 die.userData.value=value;
 if(reduced()||!rolling){die.rotation.set(...target);die.position.set(index? .64:-.64,-.12,0);die.scale.setScalar(1);die.userData.animation=null;return;}
 const spins=index?[-1,2,-2]:[2,-2,2];
 die.userData.animation={start:performance.now(),duration,from:current,to:target.map((v,i)=>v+spins[i]*Math.PI*2),target,x:index? .64:-.64,y:-.12,z:0,fromX:index? .95:-.95,fromY:.25,fromZ:.28,lift:index?.95:1.18};
 die.position.set(index? .95:-.95,.25,.28);die.scale.setScalar(.82);startFrame();
}
function resize(){if(!renderer||!root)return;const box=root.getBoundingClientRect(),width=Math.max(1,box.width),height=Math.max(1,box.height);renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.5));renderer.setSize(width,height,false);camera.aspect=width/height;camera.updateProjectionMatrix();render();}
function setup(){
 try{
  renderer=new THREE.WebGLRenderer({alpha:true,antialias:true,powerPreference:'low-power',stencil:false,depth:true});renderer.setClearColor(0x000000,0);renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.15;canvas=renderer.domElement;canvas.className='nj-three-dice-layer';canvas.setAttribute('aria-hidden','true');
  scene=new THREE.Scene();camera=new THREE.PerspectiveCamera(31,1,.1,30);camera.position.set(0,1.25,6.1);camera.lookAt(0,.05,0);
  scene.add(new THREE.HemisphereLight(0xfff5df,0x25352f,2.15));
  const key=new THREE.DirectionalLight(0xfff4d7,3.2);key.position.set(-3,5,5);scene.add(key);
  const rim=new THREE.DirectionalLight(0xe88966,1.15);rim.position.set(4,2,-3);scene.add(rim);
  for(let i=0;i<2;i++){const die=cube(i?2:1,i);scene.add(die);dice.push(die);}
  canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();fallback();});
  resizeObserver=new ResizeObserver(resize);ready=true;sync();
 }catch{fallback();}
}
function fallback(){ready=false;if(frame)cancelAnimationFrame(frame);frame=0;resizeObserver?.disconnect();resizeObserver=null;if(renderer){renderer.dispose();renderer.forceContextLoss();renderer=null;}canvas?.remove();canvas=null;root?.removeAttribute('data-three-ready');}
function sync(){
 const next=pickRoot();if(root!==next){if(root)resizeObserver?.unobserve(root);root=next;if(root&&ready){root.appendChild(canvas);root.dataset.threeReady='true';resizeObserver?.observe(root);resize();}else if(canvas?.parentNode)canvas.remove();}
 if(!root||!ready)return;
 const vals=values(),rolling=root.classList.contains('is-rolling');dice.forEach((die,i)=>{die.visible=i<vals.length;die.userData.shadow.visible=i<vals.length;});
 if(rolling&&!lastRolling){vals.forEach((v,i)=>{if(dice[i])moveDie(dice[i],v,true,680);});}
 else if(!rolling&&lastRolling){vals.forEach((v,i)=>{if(dice[i])moveDie(dice[i],v,true,250);});}
 else if(!rolling){vals.forEach((v,i)=>{if(dice[i]&&dice[i].userData.value!==v)moveDie(dice[i],v,false);});}
 lastRolling=rolling;render();
}
const observer=new MutationObserver(sync);observer.observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['class','data-dice-value']});
async function warmup(){if(loading)return loading;loading=Promise.all([import('three'),import('three/addons/geometries/RoundedBoxGeometry.js')]).then(([three,rounded])=>{THREE=three;RoundedBoxGeometry=rounded.RoundedBoxGeometry;setup();return ready;}).catch(()=>false);return loading;}
window.NightLudo3D=Object.freeze({warmup,get ready(){return ready;},sync});
})();
