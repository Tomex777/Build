import fs from 'node:fs';
import path from 'node:path';
import vm from 'node:vm';
import crypto from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here,'..');
const file = path.join(root,'YUWAI-Standalone.html');
const html = fs.readFileSync(file,'utf8');
const checks = [];
const assert = (name, ok) => { checks.push([name,!!ok]); if(!ok) throw new Error(`Verification failed: ${name}`); };

assert('exact audited source hash', crypto.createHash('sha256').update(html).digest('hex') === 'a6af228cbc4dd9c3c66b7ad48deac30c24b8ba1ed1eea395fbf0fe0e6bddc78d');
assert('standalone HTML has app shell', html.includes('id="app" class="app-shell"'));
assert('mobile-first tool dock exists', html.includes('id="mobile-toolbar"'));
assert('SVG icon system exists', html.includes('const uiIconDefs={') && html.includes('function uiSvg('));
assert('showcase builder exists', html.includes('function buildShowcaseDemo()'));
assert('root nodes render in free mode', html.includes("root.append(renderNode(n,'free'))"));
assert('prototype uses fit wrapper', html.includes('id="preview-wrap"') && html.includes('previewScale'));
assert('portable YUWAI project builder exists', html.includes('buildProjectBytes') && html.includes('parseProjectBytes'));
assert('mobile touch pinch implementation exists', html.includes('function handleTouchStart(e)') && html.includes('pinchGesture'));
assert('mobile long press implementation exists', html.includes('function scheduleLongPress(e,id)'));
assert('bottom-sheet mobile panels exist', html.includes('mobile-open') && html.includes('openMobilePanel'));
assert('no old Unicode toolbar placeholders remain', !/[☷▣□▭⌁▧✥↶↷▶⌫⌗🔒◉⌑◇◆＋☰⧉⇳]/u.test(html));

const scriptStart = html.indexOf('<script>') + '<script>'.length;
const scriptEnd = html.lastIndexOf('</script>');
assert('inline script found', scriptStart > 7 && scriptEnd > scriptStart);
const js = html.slice(scriptStart,scriptEnd);
const temp = path.join(root,'.verify-yuwai.js');
fs.writeFileSync(temp,js);
try { execFileSync(process.execPath,['--check',temp],{stdio:'pipe'}); } finally { fs.rmSync(temp,{force:true}); }
assert('JavaScript syntax is valid', true);

const used = [...html.matchAll(/uiSvg\('([^']+)'/g)].map(m=>m[1]);
const defsBlock = (html.match(/const uiIconDefs=\{([\s\S]*?)\n\};/)||[])[1] || '';
for(const icon of new Set(used)) assert(`SVG definition: ${icon}`, new RegExp(`\\b${icon}\\s*:`).test(defsBlock));

console.log(`YUWAI static verification passed: ${checks.length}/${checks.length}`);
