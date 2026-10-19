/* NIGHT offline microgame engine. Original code; no remote dependencies. */
(()=>{'use strict';
const games={};const $=id=>document.getElementById(id);
let cleanup=()=>{};
const api={
 add(id,title,desc,category,init){games[id]={title,desc,category,init}},
 status(t){$('status').textContent=t},
 note(t){$('hint').textContent=t||''},
 board(){const b=$('board');b.replaceChildren();return b},
 actions(){const a=$('actions');a.replaceChildren();return a},
 action(label,fn,primary=false){const b=document.createElement('button');b.textContent=label;b.type='button';if(primary)b.className='primary';b.addEventListener('click',fn);$('actions').append(b);return b},
 el(tag,cls,text){const e=document.createElement(tag);if(cls)e.className=cls;if(text!=null)e.textContent=text;return e},
 grid(rows,cols,items,press,configure){const b=$('board');b.replaceChildren();const wrap=api.el('div','tiles');wrap.dataset.size=String(cols);wrap.style.gridTemplateColumns='repeat('+cols+',minmax(0,1fr))';items.forEach((item,i)=>{const btn=api.el('button','cell',item==null?'':String(item));btn.type='button';btn.setAttribute('aria-label','Cell '+(i+1)+(item?': '+item:''));btn.addEventListener('click',()=>press(i));if(configure)configure(btn,i,item);wrap.append(btn)});b.append(wrap);return wrap},
 score(key,score){try{const k='night-offline-classic-best-'+key;if(score>Number(localStorage.getItem(k)||0))localStorage.setItem(k,String(score))}catch{}},
 best(key){try{return Number(localStorage.getItem('night-offline-classic-best-'+key)||0)}catch{return 0}},
 shuffle(a){const out=a.slice();for(let i=out.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[out[i],out[j]]=[out[j],out[i]]}return out},
 clear(){cleanup();cleanup=()=>{}},
 run(id){const g=games[id];if(!g){api.status('Game unavailable');return}api.clear();document.body.dataset.game=id;document.body.dataset.category=g.category.toLowerCase();$('title').textContent=g.title;$('description').textContent=g.desc;$('category').textContent=g.category.toUpperCase();document.title=g.title+' · NIGHT';api.board();api.actions();api.note('');api.status('');cleanup=g.init(api)||(()=>{})}
};
window.G=api;
window.addEventListener('DOMContentLoaded',()=>{const id=new URL(location.href).searchParams.get('game')||'ttt';$('restart').addEventListener('click',()=>api.run(id));api.run(id)});
window.addEventListener('pagehide',()=>api.clear());
})();