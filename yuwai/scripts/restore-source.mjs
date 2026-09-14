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
const html = zlib.brotliDecompressSync(compressed);
const hash = crypto.createHash('sha256').update(html).digest('hex');
const expected = 'a6af228cbc4dd9c3c66b7ad48deac30c24b8ba1ed1eea395fbf0fe0e6bddc78d';
if (hash !== expected) throw new Error(`Source SHA-256 mismatch: ${hash}`);
fs.writeFileSync(path.join(root, 'YUWAI-Standalone.html'), html);
console.log(`Restored YUWAI-Standalone.html (${html.length} bytes, sha256 ${hash})`);
