import fs from 'node:fs';
import path from 'node:path';
import zlib from 'node:zlib';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const chunksDir = path.join(root, 'source-chunks');
const parts = fs.readdirSync(chunksDir).filter(n => /^part\d+\.txt$/.test(n)).sort();
if (!parts.length) throw new Error('No YUWAI source chunks found');
const encoded = parts.map(n => fs.readFileSync(path.join(chunksDir, n), 'utf8').trim()).join('');
const compressed = Buffer.from(encoded, 'base64');
const original = zlib.brotliDecompressSync(compressed);
const originalHash = crypto.createHash('sha256').update(original).digest('hex');
const expectedOriginal = 'a6af228cbc4dd9c3c66b7ad48deac30c24b8ba1ed1eea395fbf0fe0e6bddc78d';
if (originalHash !== expectedOriginal) throw new Error(`Original source SHA-256 mismatch: ${originalHash}`);

const oldScale = "const phone=$('#preview-phone'),stage=$('#preview-stage'),wrap=$('#preview-wrap');const previewScale=Math.min(1,Math.max(.2,(stage.clientWidth-8)/root.width),Math.max(.2,(stage.clientHeight-8)/root.height));wrap.style.width=`${root.width*previewScale}px`;wrap.style.height=`${root.height*previewScale}px`;";
const newScale = "const phone=$('#preview-phone'),stage=$('#preview-stage'),wrap=$('#preview-wrap'),stageStyle=getComputedStyle(stage),availableWidth=Math.max(1,stage.clientWidth-parseFloat(stageStyle.paddingLeft)-parseFloat(stageStyle.paddingRight)-4),availableHeight=Math.max(1,stage.clientHeight-parseFloat(stageStyle.paddingTop)-parseFloat(stageStyle.paddingBottom)-4);const previewScale=Math.max(.05,Math.min(1,availableWidth/root.width,availableHeight/root.height));wrap.style.width=`${root.width*previewScale}px`;wrap.style.height=`${root.height*previewScale}px`;";
let html = original.toString('utf8');
if (!html.includes(oldScale)) throw new Error('Expected prototype scale block was not found');
html = html.replace(oldScale, newScale);
const finalBytes = Buffer.from(html, 'utf8');
const finalHash = crypto.createHash('sha256').update(finalBytes).digest('hex');
const expectedFinal = '25eb988c7d0623183b5e70f4e7dd29787603df2dd659a30e881262f0a3dd985d';
if (finalHash !== expectedFinal) throw new Error(`Patched source SHA-256 mismatch: ${finalHash}`);
fs.writeFileSync(path.join(root, 'YUWAI-Standalone.html'), finalBytes);
console.log(`Restored and patched YUWAI-Standalone.html (${finalBytes.length} bytes, sha256 ${finalHash})`);
