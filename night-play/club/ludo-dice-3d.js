/* Three.js dice for NIGHT Ludo. The renderer is loaded only after Ludo opens. */
(function(){
"use strict";
let loading=null,THREE=null,RoundedBoxGeometry=null,renderer=null,scene=null,camera=null,canvas=null,root=null,resizeObserver=null,frame=0,lastRolling=false,ready=false,controlRenderer=null,controlScene=null,controlCamera=null,controlCanvas=null,controlRoot=null,controlResizeObserver=null,controlDice=[],controlRolling=false;
const dice=[],pips={1:[[0,0]],2:[[-1,-1],[1,1]],3:[[-1,-1],[0,0],[1,1]],4:[[-1,-1],[1,-1],[-1,1],[1,1]],5:[[-1,-1],[1,-1],[0,0],[-1,1],[1,1]],6:[[-1,-1],[1,-1],[-1,0],[1,0],[-1,1],[1,1]]};
const faceLayout=[['front',1],['back',6],['right',3],['left',4],['top',2],['bottom',5]];
function pickRoot(){return document.querySelector('.nj-dice-center');}
function values(){return [...(root?.querySelectorAll('.nj-die[data-center-die]')||[])].map(el=>Number(el.dataset.diceValue)||1);}
function reduced(){return matchMedia('(prefers-reduced-motion: reduce)').matches;}
function cube(targetScene,index){
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
 group.position.x=index? .64:-.64;group.position.y=-.12;group.userData={index,value:1,shadow:null,animation:null};
 const shadow=new THREE.Mesh(new THREE.CircleGeometry(.64,32),new THREE.MeshBasicMaterial({color:0x06110f,transparent:true,opacity:.35,depthWrite:false}));
 shadow.rotation.x=-Math.PI/2;shadow.position.set(group.position.x,-.7,0);targetScene.add(shadow);group.userData.shadow=shadow;
 targetScene.add(group);
 return group;
}
function targetRotation(value){
 const angles={1:[0,0,0],2:[Math.PI/2,0,0],3:[0,-Math.PI/2,0],4:[0,Math.PI/2,0],5:[-Math.PI/2,0,0],6:[0,Math.PI,0]};
 return angles[value]||angles[1];
}
function render(){if(renderer&&root?.isConnected)renderer.render(scene,camera);if(controlRenderer&&controlRoot?.isConnected)controlRenderer.render(controlScene,controlCamera);}
function startFrame(){if(!frame)frame=requestAnimationFrame(tick);}
function animateDice(list,now){let active=false;for(const die of list){const a=die.userData.animation;if(!a)continue;const raw=Math.min(1,(now-a.start)/a.duration),ease=1-Math.pow(1-raw,4);die.rotation.set(a.from[0]+(a.to[0]-a.from[0])*ease,a.from[1]+(a.to[1]-a.from[1])*ease,a.from[2]+(a.to[2]-a.from[2])*ease);
  const hop=Math.sin(raw*Math.PI)*a.lift,arc=ease;die.position.set(a.fromX+(a.x-a.fromX)*arc,a.fromY+(a.y-a.fromY)*arc+hop,a.fromZ+(a.z-a.fromZ)*arc);die.scale.setScalar(1+Math.sin(raw*Math.PI)*.09);die.userData.shadow.material.opacity=.34-hop*.1;die.userData.shadow.scale.setScalar(1+hop*.45);
  if(raw>=1){die.rotation.set(...a.target);die.position.set(a.x,a.y,a.z);die.scale.setScalar(1);die.userData.shadow.material.opacity=.34;die.userData.shadow.scale.setScalar(1);die.userData.animation=null;}else active=true;
 }return active;}
function tick(now){frame=0;if(!renderer&&!controlRenderer)return;const centerActive=animateDice(dice,now),controlsActive=animateDice(controlDice,now),active=centerActive||controlsActive;render();if(active)startFrame();
}
function moveDie(die,value,rolling,duration=680){
 const target=targetRotation(value),current=[die.rotation.x,die.rotation.y,die.rotation.z],index=die.userData.index,x=die.userData.centered?0:index?.64:-.64;
 die.userData.value=value;
 if(reduced()||!rolling){die.rotation.set(...target);die.position.set(x,-.12,0);die.userData.shadow.position.x=x;die.scale.setScalar(1);die.userData.animation=null;return;}
 const spins=index?[-1,2,-2]:[2,-2,2];
 die.userData.animation={start:performance.now(),duration,from:current,to:target.map((v,i)=>v+spins[i]*Math.PI*2),target,x,y:-.12,z:0,fromX:x*1.48,fromY:.25,fromZ:.28,lift:index?.95:1.18};
 die.position.set(x*1.48,.25,.28);die.scale.setScalar(.82);startFrame();
}
function resize(){if(!renderer||!root)return;const box=root.getBoundingClientRect(),width=Math.max(1,box.width),height=Math.max(1,box.height);renderer.setPixelRatio(Math.min(devicePixelRatio||1,1.5));renderer.setSize(width,height,false);camera.aspect=width/height;camera.updateProjectionMatrix();render();}
function resizeControls(){if(!controlRenderer||!controlRoot)return;const box=controlRoot.getBoundingClientRect(),width=Math.max(1,box.width),height=Math.max(1,box.height);controlRenderer.setPixelRatio(Math.min(devicePixelRatio||1,1.5));controlRenderer.setSize(width,height,false);controlCamera.aspect=width/height;controlCamera.updateProjectionMatrix();render();}
function setup(){
 try{
  renderer=new THREE.WebGLRenderer({alpha:true,antialias:true,powerPreference:'low-power',stencil:false,depth:true});renderer.setClearColor(0x000000,0);renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.15;canvas=renderer.domElement;canvas.className='nj-three-dice-layer';canvas.setAttribute('aria-hidden','true');
  scene=new THREE.Scene();camera=new THREE.PerspectiveCamera(31,1,.1,30);camera.position.set(0,1.25,6.1);camera.lookAt(0,.05,0);
  scene.add(new THREE.HemisphereLight(0xfff5df,0x25352f,2.15));
  const key=new THREE.DirectionalLight(0xfff4d7,3.2);key.position.set(-3,5,5);scene.add(key);
  const rim=new THREE.DirectionalLight(0xe88966,1.15);rim.position.set(4,2,-3);scene.add(rim);
  for(let i=0;i<2;i++)dice.push(cube(scene,i));
  canvas.addEventListener('webglcontextlost',event=>{event.preventDefault();fallback();});
  controlRenderer=new THREE.WebGLRenderer({alpha:true,antialias:true,powerPreference:'low-power',stencil:false,depth:true});controlRenderer.setClearColor(0x000000,0);controlRenderer.outputColorSpace=THREE.SRGBColorSpace;controlRenderer.toneMapping=THREE.ACESFilmicToneMapping;controlRenderer.toneMappingExposure=1.15;controlCanvas=controlRenderer.domElement;controlCanvas.className='nj-three-controls-layer';controlCanvas.setAttribute('aria-hidden','true');
  controlScene=new THREE.Scene();controlCamera=new THREE.PerspectiveCamera(43,1,.1,30);controlCamera.position.set(0,.5,2.8);controlCamera.lookAt(0,0,0);
  controlScene.add(new THREE.HemisphereLight(0xfff5df,0x25352f,2.15));const controlKey=new THREE.DirectionalLight(0xfff4d7,3.2);controlKey.position.set(-3,5,5);controlScene.add(controlKey);const controlRim=new THREE.DirectionalLight(0xe88966,1.15);controlRim.position.set(4,2,-3);controlScene.add(controlRim);
  for(let i=0;i<2;i++)controlDice.push(cube(controlScene,i));controlCanvas.addEventListener('webglcontextlost',event=>{event.preventDefault();fallback();});
  resizeObserver=new ResizeObserver(resize);controlResizeObserver=new ResizeObserver(resizeControls);ready=true;sync();
 }catch{fallback();}
}
function fallback(){ready=false;if(frame)cancelAnimationFrame(frame);frame=0;resizeObserver?.disconnect();controlResizeObserver?.disconnect();resizeObserver=controlResizeObserver=null;if(renderer){renderer.dispose();renderer.forceContextLoss();renderer=null;}if(controlRenderer){controlRenderer.dispose();controlRenderer.forceContextLoss();controlRenderer=null;}canvas?.remove();controlCanvas?.remove();canvas=controlCanvas=null;root?.removeAttribute('data-three-ready');controlRoot?.removeAttribute('data-three-ready');}
function sync(){
 const next=pickRoot();if(root!==next){if(root)resizeObserver?.unobserve(root);root=next;if(root&&ready){root.appendChild(canvas);root.dataset.threeReady='true';resizeObserver?.observe(root);resize();}else if(canvas?.parentNode)canvas.remove();}
 if(!root){controlResizeObserver?.disconnect();controlCanvas?.remove();controlRoot=null;cancel();return;}if(!ready)return;
 if(canvas.parentNode!==root){root.appendChild(canvas);root.dataset.threeReady='true';resizeObserver?.observe(root);resize();}
 const vals=values(),rolling=root.classList.contains('is-rolling'),buttons=[...root.querySelectorAll('.nj-center-button')];dice.forEach((die,i)=>{die.userData.centered=vals.length===1;die.visible=i<vals.length;die.userData.shadow.visible=i<vals.length;const used=buttons[i]?.classList.contains('is-used');die.traverse(node=>{if(node.material){node.material.transparent=!!used;node.material.opacity=used?.3:1;node.material.needsUpdate=true;}});});
 if(rolling&&!lastRolling){vals.forEach((v,i)=>{if(dice[i])moveDie(dice[i],v,true,680);});}
 else if(!rolling&&lastRolling){vals.forEach((v,i)=>{if(dice[i])moveDie(dice[i],v,false);});}
 else if(!rolling){vals.forEach((v,i)=>{if(dice[i]&&!dice[i].userData.animation)moveDie(dice[i],v,false);});}
 lastRolling=rolling;
 const nextControls=document.querySelector('.nj-dice-controls');if(controlRoot!==nextControls){if(controlRoot)controlResizeObserver?.unobserve(controlRoot);controlRoot=nextControls;if(controlRoot&&ready){controlRoot.appendChild(controlCanvas);controlRoot.dataset.threeReady='true';controlResizeObserver?.observe(controlRoot);resizeControls();}else if(controlCanvas?.parentNode)controlCanvas.remove();}
 if(controlRoot&&ready){if(controlCanvas.parentNode!==controlRoot){controlRoot.appendChild(controlCanvas);controlRoot.dataset.threeReady='true';controlResizeObserver?.observe(controlRoot);resizeControls();}
  const controlVals=[...controlRoot.querySelectorAll('.nj-die-button .nj-die')].map(el=>Number(el.dataset.diceValue)||1),controlIsRolling=!!controlRoot.querySelector('.nj-die-button.is-tumbling'),controlButtons=[...controlRoot.querySelectorAll('.nj-die-button')];controlDice.forEach((die,i)=>{die.userData.centered=controlVals.length===1;die.visible=i<controlVals.length;die.userData.shadow.visible=i<controlVals.length;const used=controlButtons[i]?.classList.contains('is-used');die.traverse(node=>{if(node.material){node.material.transparent=!!used;node.material.opacity=used?.3:1;node.material.needsUpdate=true;}});});
  if(controlIsRolling&&!controlRolling)controlVals.forEach((v,i)=>{if(controlDice[i])moveDie(controlDice[i],v,true,680);});else if(!controlIsRolling&&controlRolling)controlVals.forEach((v,i)=>{if(controlDice[i])moveDie(controlDice[i],v,false);});else if(!controlIsRolling)controlVals.forEach((v,i)=>{if(controlDice[i]&&!controlDice[i].userData.animation)moveDie(controlDice[i],v,false);});controlRolling=controlIsRolling;
 }
 render();
}
const observer=new MutationObserver(sync);observer.observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['class','data-dice-value']});
async function warmup(){if(loading)return loading;loading=Promise.all([import('three'),import('three/addons/geometries/RoundedBoxGeometry.js')]).then(([three,rounded])=>{THREE=three;RoundedBoxGeometry=rounded.RoundedBoxGeometry;setup();return ready;}).catch(()=>false);return loading;}
function cancel(){if(frame)cancelAnimationFrame(frame);frame=0;for(const die of [...dice,...controlDice])die.userData.animation=null;lastRolling=controlRolling=false;}
const api={warmup,get ready(){return ready;},sync,cancel};
if(/^(localhost|127\.0\.0\.1)$/.test(location.hostname))api.inspect=()=>({ready,center:dice.map(d=>({value:d.userData.value,rotation:[d.rotation.x,d.rotation.y,d.rotation.z],animating:!!d.userData.animation,visible:d.visible,x:d.position.x})),controls:controlDice.map(d=>({value:d.userData.value,rotation:[d.rotation.x,d.rotation.y,d.rotation.z],animating:!!d.userData.animation,visible:d.visible,x:d.position.x}))});
window.NightLudo3D=Object.freeze(api);
})();
