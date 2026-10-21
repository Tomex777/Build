#!/usr/bin/env node
import fs from 'node:fs/promises';
import {inside,atomic} from '../src/catalogue.mjs';
const target=inside(process.argv[2]??'dist/dmc-netflix/index.html');
const original=await fs.readFile(target,'utf8');
if(!original.includes('BLEACH'))throw Error('Unknown gallery template; inspect before relabel');
const changed=original.replaceAll('within BLEACH','within Netflix Devil May Cry').replaceAll('BLEACH','DEVIL MAY CRY · NETFLIX');
await atomic(target,changed);
console.log('Netflix-only shared NIGHT gallery label updated');
