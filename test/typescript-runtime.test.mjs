import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const root = 'annie-android/app/src/main/assets/typescript/';
const context = vm.createContext({});
vm.runInContext(fs.readFileSync(root + 'typescript.js', 'utf8'), context);
vm.runInContext(fs.readFileSync(root + 'transpile.js', 'utf8'), context);
const compile = (source, filename = 'main.ts') => context.annieTranspile(source, filename);
test('bundled compiler executes interfaces, enums, generics and async functions', async () => {
  const result = compile(`interface Item { value: number }; enum Mode { Twice = 2 };
    function identity<T>(value: T): T { return value; }
    async function run(): Promise<number> { const item: Item = {value: identity(21)}; return item.value * Mode.Twice; }
    globalThis.answer = run();`);
  assert.equal(result.error, undefined);
  const execution = vm.createContext({});
  vm.runInContext(result.code, execution);
  assert.equal(await execution.answer, 42);
});
test('module imports and exports survive compilation; type-only imports vanish', () => {
  const result = compile(`import type { Item } from './types.ts'; import { value } from './helper.ts'; export const answer: number = value;`);
  assert.equal(result.error, undefined);
  assert.match(result.code, /from ['"]\.\/helper\.ts['"]/);
  assert.doesNotMatch(result.code, /types\.ts['"]/);
  assert.match(result.code, /export const answer = value/);
});
test('syntax diagnostics include original file, line and column', () => {
  const result = compile('const value: = 3;', 'broken.ts');
  assert.match(result.error, /broken\.ts:1:\d+ TS\d+/);
  assert.equal(result.code, undefined);
});
test('compiler also runs in a bare context without Node or browser APIs', () => {
  assert.equal(vm.runInContext('typeof process + "," + typeof window + "," + typeof require', context), 'undefined,undefined,undefined');
  assert.equal(compile('const value: number = 4;').error, undefined);
});
