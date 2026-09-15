import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';
import { PNG } from 'pngjs';

const out = '/tmp/room-v4/output';
fs.mkdirSync(out, { recursive: true });
const cases = [];
let fatal = false;

function analyzePng(file) {
  const png = PNG.sync.read(fs.readFileSync(file));
  let sum = 0, sum2 = 0, min = 255, max = 0, n = 0;
  const step = Math.max(1, Math.floor((png.width * png.height) / 50000));
  for (let p = 0; p < png.width * png.height; p += step) {
    const i = p * 4;
    const y = .2126 * png.data[i] + .7152 * png.data[i + 1] + .0722 * png.data[i + 2];
    sum += y; sum2 += y * y; min = Math.min(min, y); max = Math.max(max, y); n++;
  }
  const mean = sum / n, variance = Math.max(0, sum2 / n - mean * mean);
  return {
    width: png.width, height: png.height,
    meanLuma: +mean.toFixed(2), stdLuma: +Math.sqrt(variance).toFixed(2),
    minLuma: +min.toFixed(2), maxLuma: +max.toFixed(2)
  };
}

const browser = await chromium.launch({
  headless: true,
  args: ['--use-angle=swiftshader', '--enable-webgl', '--ignore-gpu-blocklist', '--disable-dev-shm-usage', '--disable-gpu-vsync']
});

async function snap(page, file) {
  const f = path.join(out, file);
  await page.screenshot({ path: f, fullPage: false, timeout: 90000 });
  return analyzePng(f);
}

async function inspect(name, viewport, mobile = false) {
  const context = await browser.newContext({ viewport, deviceScaleFactor: 1, isMobile: mobile, hasTouch: mobile });
  const page = await context.newPage();
  page.setDefaultTimeout(90000);
  const events = [];
  page.on('console', m => events.push({ type: `console:${m.type()}`, text: m.text() }));
  page.on('pageerror', e => { events.push({ type: 'pageerror', text: e.message }); fatal = true; });
  page.on('requestfailed', r => events.push({ type: 'requestfailed', text: `${r.url()} :: ${r.failure()?.errorText || ''}` }));
  await page.goto('http://127.0.0.1:4176/', { waitUntil: 'networkidle', timeout: 90000 });
  await page.waitForTimeout(2400);

  const runtime = await page.evaluate(() => {
    const c = document.querySelector('#scene');
    const e = document.querySelector('#error');
    const r = document.querySelector('#resetView');
    let gl = null;
    try { gl = c?.getContext('webgl2') || c?.getContext('webgl'); } catch (_) {}
    return {
      canvasWidth: c?.width || 0,
      canvasHeight: c?.height || 0,
      errorVisible: e ? !e.hidden : null,
      errorText: e?.textContent || '',
      resetVisible: !!r && getComputedStyle(r).display !== 'none',
      webgl: !!gl
    };
  });

  if (runtime.errorVisible || !runtime.webgl || runtime.canvasWidth < 2 || runtime.canvasHeight < 2) fatal = true;

  const shots = {};
  shots.initial = await snap(page, `${name}-initial.png`);
  if (!mobile) {
    const x = Math.round(viewport.width * .56), y = Math.round(viewport.height * .50);
    await page.mouse.move(x, y);
    await page.mouse.down();
    await page.mouse.move(x - 135, y + 28, { steps: 12 });
    await page.mouse.up();
    await page.waitForTimeout(500);
    shots.orbited = await snap(page, `${name}-orbited.png`);
    await page.keyboard.press('KeyL');
    await page.waitForTimeout(450);
    shots.lamp = await snap(page, `${name}-lamp-on.png`);
    await page.keyboard.press('KeyR');
    await page.waitForTimeout(450);
    shots.reset = await snap(page, `${name}-reset.png`);
  } else {
    await page.locator('#resetView').tap();
    await page.waitForTimeout(350);
    shots.reset = await snap(page, `${name}-reset.png`);
  }

  const meaningful = Object.values(shots).every(s => s.stdLuma > 8 && (s.maxLuma - s.minLuma) > 50);
  if (!meaningful) fatal = true;
  cases.push({ name, viewport, mobile, runtime, shots, meaningful, events });
  await context.close();
}

await inspect('desktop-1280x800', { width: 1280, height: 800 }, false);
await inspect('mobile-393x873', { width: 393, height: 873 }, true);
await inspect('mobile-landscape-873x393', { width: 873, height: 393 }, true);
await browser.close();

fs.writeFileSync(path.join(out, 'report.json'), JSON.stringify({ fatal, cases }, null, 2));
fs.writeFileSync(path.join(out, 'browser.log'), cases.flatMap(x => x.events.map(e => `[${x.name}] ${e.type}: ${e.text}`)).join('\n'));
if (fatal) process.exitCode = 2;
