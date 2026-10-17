import fs from 'node:fs/promises';
import {pathToFileURL} from 'node:url';
import {ROOT} from '../src/node-assets.mjs';
import {launch} from './browser.mjs';
const browser=await launch(),out=ROOT+'/qa/depth-motion';await fs.mkdir(out,{recursive:true});
try{
 const page=await browser.newPage({viewport:{width:1060,height:840},deviceScaleFactor:1});
 await page.goto(pathToFileURL(ROOT+'/dist/NIGHT-Depth-Editions.html').href);await page.evaluate(()=>document.fonts.ready);
 await page.evaluate(()=>{
  const {cards,assets}=JSON.parse(document.getElementById('card-data').textContent);
  const display=assets[cards[0].id].fonts.display;
  document.body.innerHTML=`<style>@font-face{font-family:MotionDisplay;src:url('${display}')}*{box-sizing:border-box}body{margin:0;background:#d5d8d0}main{display:flex;gap:46px;justify-content:center;padding-top:35px;perspective:1500px}.card{width:470px;height:658px;position:relative;transform-style:preserve-3d;filter:drop-shadow(0 24px 14px #122b3440)}.card svg{display:block;width:470px;height:658px}.labels{display:flex;gap:46px;justify-content:center;margin-top:40px;font:32px MotionDisplay;color:#142b35}.labels div{width:470px;text-align:center}</style><main>${cards.map((c,i)=>`<div class="card" id="motion-${i}">${window.Night.renderCard(c,assets[c.id])}</div>`).join('')}</main><div class="labels"><div>Nocturne</div><div>Tidelight</div></div>`;
 });
 await page.evaluate(()=>document.fonts.ready);
 for(let i=0;i<60;i++){
  const theta=i/60*Math.PI*2,light=[.5+.44*Math.cos(theta),.42+.30*Math.sin(theta)];
  await page.evaluate(l=>{for(const c of document.querySelectorAll('.card')){window.Night.setCardLight(c.querySelector('svg'),l);c.style.transform=`rotateX(${(l[1]-.5)*-7}deg) rotateY(${(l[0]-.5)*11}deg)`;}},light);
  await page.screenshot({path:`${out}/${String(i).padStart(3,'0')}.png`});
 }
 console.log('Recorded 60 actual SVG light/depth frames.');
}finally{await browser.close();}
