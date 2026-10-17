/* Browser-only studio. Uses the same renderer that produces the exports. */
(()=>{
 const {renderCard,setCardLight}=window.Night;
 const {cards,assets}=JSON.parse(document.getElementById('card-data').textContent);
 const stage=document.getElementById('stage'),face=document.getElementById('face');
 const turn=document.getElementById('turn'),finish=document.getElementById('finish');
 const status=document.getElementById('status');
 let index=0,side='front',foil=true,light=[.28,.2],frame=0,latest;
 const reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
 const current=()=>cards[index];
 function mount(){
  const c=current(),a=assets[c.id];
  document.getElementById('artist-name').textContent='Illustration · '+a.artist;
  const artist=document.getElementById('artist-credit');artist.textContent=a.artist;artist.href=a.sourceUrl;
  const license=document.getElementById('art-license');license.textContent=a.license;license.href=a.licenseUrl;
  document.getElementById('card-license').textContent='Artwork cropped and framed by NIGHT. Card image: '+a.outputLicense+'.';
  face.innerHTML=renderCard(c,assets[c.id],{side,foil:foil?1:0,light});
  document.getElementById('edition-name').textContent=c.edition.charAt(0)+c.edition.slice(1).toLowerCase();
  document.getElementById('description').textContent=c.description;
  document.getElementById('material-name').textContent=c.material;
  document.getElementById('number').textContent=c.number+' / ARTIST EDITIONS';
  turn.querySelector('span').textContent=side==='front'?'Turn card':'Show artwork';
  turn.setAttribute('aria-label',side==='front'?'Turn card to reverse':'Show front artwork');
  finish.setAttribute('aria-pressed',String(foil));finish.querySelector('span').textContent=foil?'Foil on':'Foil off';
  stage.setAttribute('aria-label',`${c.edition} card, ${side}. Use arrow keys to move the light. Home resets it.`);
  document.querySelectorAll('[data-card]').forEach((el,i)=>el.setAttribute('aria-pressed',String(index===i)));
  status.textContent='';
 }
 function illuminate(x,y){
  light=[Math.max(0,Math.min(1,x)),Math.max(0,Math.min(1,y))];
  setCardLight(face.querySelector('svg'),light);
  if(!reduced)face.style.transform=`rotateX(${(light[1]-.5)*-9}deg) rotateY(${(light[0]-.5)*11}deg)`;
 }
 stage.addEventListener('pointermove',e=>{
  latest=e;if(frame)return;frame=requestAnimationFrame(()=>{frame=0;const box=stage.getBoundingClientRect();illuminate((latest.clientX-box.left)/box.width,(latest.clientY-box.top)/box.height);});
 });
 stage.addEventListener('pointerleave',()=>{if(frame)cancelAnimationFrame(frame);frame=0;face.style.transform='';});
 stage.addEventListener('keydown',e=>{
  const keys={ArrowLeft:[-.12,0],ArrowRight:[.12,0],ArrowUp:[0,-.12],ArrowDown:[0,.12]};
  if(e.key==='Home'){e.preventDefault();illuminate(.28,.2);return;}
  if(keys[e.key]){e.preventDefault();illuminate(light[0]+keys[e.key][0],light[1]+keys[e.key][1]);}
 });
 document.querySelectorAll('[data-card]').forEach((b,i)=>b.addEventListener('click',()=>{index=i;side='front';light=[.28,.2];face.style.transform='';mount();}));
 turn.addEventListener('click',()=>{side=side==='front'?'back':'front';face.style.transform='';mount();});
 finish.addEventListener('click',()=>{foil=!foil;mount();});
 function save(blob,name){const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),30000);}
 function serialized(){const node=face.querySelector('svg').cloneNode(true);return new XMLSerializer().serializeToString(node);}
 document.getElementById('svg-export').addEventListener('click',()=>{save(new Blob([serialized()],{type:'image/svg+xml'}),`NIGHT-${current().id}-${side}.svg`);status.textContent='SVG saved.';});
 document.getElementById('png-export').addEventListener('click',async e=>{
  const b=e.currentTarget,exportName=`NIGHT-${current().id}-${side}-2000.png`,exportSVG=serialized();b.disabled=true;status.textContent='Preparing image…';
  try{
   await document.fonts.ready;
   const url=URL.createObjectURL(new Blob([exportSVG],{type:'image/svg+xml'})),img=new Image();
   try{await new Promise((resolve,reject)=>{img.onload=resolve;img.onerror=reject;img.src=url;});
    const canvas=document.createElement('canvas');canvas.width=2000;canvas.height=2800;
    const ctx=canvas.getContext('2d');ctx.drawImage(img,0,0,2000,2800);
    const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/png'));if(!blob)throw new Error('PNG encoding failed');
    save(blob,exportName);status.textContent='2000 × 2800 PNG saved.';
   }finally{URL.revokeObjectURL(url);}
  }catch(error){status.textContent='Export failed. Try the supplied rendered PNG.';console.error(error);}
  finally{b.disabled=false;}
 });
 mount();
 window.NIGHT_STUDIO={getState:()=>({index,side,foil,light:[...light]}),illuminate};
})();
