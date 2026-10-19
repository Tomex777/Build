// Node 18+, no dependencies. Detect broken entries and invented "playable" cards.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const base = __dirname;
const source = fs.readFileSync(path.join(base, 'app/src/main/java/com/night/offlinegames/MainActivity.kt'), 'utf8');
const declared = [...source.matchAll(/GameEntry\("([^"]+)"/g)].map(m => m[1]);
const special = new Set(['2048','mines','blocks','bird','maze','sudoku','connect']);
const folder = path.join(base,'app/src/main/assets/classics');
const scripts = fs.readdirSync(folder).filter(name=>name.endsWith('.js'));
const implementations=[];
for(const file of scripts){
 const js=fs.readFileSync(path.join(folder,file),'utf8');
 for(const match of js.matchAll(/G\.add\('([^']+)'/g))implementations.push(match[1]);
}
assert.equal(new Set(declared).size,declared.length,'Duplicated hub ids');
assert.equal(new Set(implementations).size,implementations.length,'Duplicated game registrations');
for(const id of declared)assert(special.has(id)||implementations.includes(id), 'No implementation for '+id);
for(const id of implementations)assert(declared.includes(id),'Hidden, unlisted game '+id);
assert.equal(declared.length,105,'Update documented count and review new games when expanding');
const html=fs.readFileSync(path.join(folder,'index.html'),'utf8');
for(const script of scripts)assert(html.includes('/classics/'+script),'Missing script tag '+script);
console.log('Catalog validated:',declared.length,'registered playable games');

// Enforce all 54 on-device illustrations; no hotlinked images or fake empty cards.
const memoryCode = fs.readFileSync(path.join(folder,'memory.js'),'utf8');
for (const theme of ['animals','vehicles','space']) {
 const marker=theme+':[';
 const from=memoryCode.indexOf(marker);
 assert(from>=0,'Missing memory category '+theme);
 const to=memoryCode.indexOf(']',from);
 const names=memoryCode.slice(from+marker.length,to).split(',').map(x=>x.replace(/['"]/g,'').trim());
 assert.equal(names.length,18,theme+' must provide 18 distinct cards for largest board');
 assert.equal(new Set(names).size,18,'Duplicated SVG card '+theme);
 const root=path.join(base,'app/src/main/assets/memory/icons',theme);
 for(const name of names)assert(fs.statSync(path.join(root,name+'.svg')).size>100,'Missing SVG '+theme+'/'+name);
}
