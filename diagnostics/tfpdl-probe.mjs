import { chromium } from 'playwright';
import fs from 'node:fs';
import path from 'node:path';

const OUT = process.env.OUT_DIR || 'tfpdl-probe-output';
fs.mkdirSync(OUT, { recursive: true });
const events = [];
const interestingHosts = ['tfpdl.com', 'tfpdl.se', 'deinform.com', 'safetxt.net'];

function log(type, data = {}) {
  const row = { ts: new Date().toISOString(), type, ...data };
  events.push(row);
  console.log(JSON.stringify(row));
}

function safeName(s) {
  return s.replace(/^https?:\/\//, '').replace(/[^a-zA-Z0-9._-]+/g, '_').slice(0, 180);
}

async function dumpPage(page, label) {
  const url = page.url();
  const text = await page.locator('body').innerText().catch(() => '');
  const html = await page.content().catch(() => '');
  const links = await page.locator('a').evaluateAll(as => as.map(a => ({ text: (a.textContent || '').trim(), href: a.href })).filter(x => x.href)).catch(() => []);
  const forms = await page.locator('form').evaluateAll(fs => fs.map(f => ({
    action: f.action,
    method: (f.method || 'get').toLowerCase(),
    inputs: Array.from(f.querySelectorAll('input,button')).map(el => ({
      tag: el.tagName,
      type: el.getAttribute('type'),
      name: el.getAttribute('name'),
      value: el.getAttribute('value'),
      text: (el.textContent || '').trim(),
      placeholder: el.getAttribute('placeholder')
    }))
  }))).catch(() => []);
  fs.writeFileSync(path.join(OUT, `${label}.html`), html);
  fs.writeFileSync(path.join(OUT, `${label}.txt`), text);
  fs.writeFileSync(path.join(OUT, `${label}-links.json`), JSON.stringify(links, null, 2));
  fs.writeFileSync(path.join(OUT, `${label}-forms.json`), JSON.stringify(forms, null, 2));
  await page.screenshot({ path: path.join(OUT, `${label}.png`), fullPage: true }).catch(() => {});
  log('page_dump', { label, url, title: await page.title().catch(() => ''), textPreview: text.slice(0, 700), linkCount: links.length, formCount: forms.length });
  return { url, text, links, forms };
}

async function maybeSaveBody(response) {
  try {
    const u = new URL(response.url());
    const req = response.request();
    if (!interestingHosts.some(h => u.hostname === h || u.hostname.endsWith('.' + h))) return;
    if (!['document', 'script', 'xhr', 'fetch'].includes(req.resourceType())) return;
    const headers = await response.allHeaders();
    const ct = (headers['content-type'] || '').toLowerCase();
    if (!(ct.includes('text') || ct.includes('javascript') || ct.includes('json') || req.resourceType() === 'script' || req.resourceType() === 'document')) return;
    const body = await response.body();
    if (body.length > 1_500_000) return;
    const ext = ct.includes('json') ? '.json' : req.resourceType() === 'script' ? '.js' : '.html';
    const dir = path.join(OUT, 'responses');
    fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, safeName(response.url()) + ext), body);
  } catch {}
}

const browser = await chromium.launch({ headless: true });
const context = await browser.newContext({
  userAgent: 'Mozilla/5.0 (Linux; Android 16; SM-A165F) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36',
  viewport: { width: 412, height: 915 },
  locale: 'en-US'
});
const page = await context.newPage();
page.setDefaultTimeout(15_000);

const apiChecks = [];
async function checkApi(name, url) {
  try {
    const res = await context.request.get(url, { timeout: 30000, failOnStatusCode: false });
    const body = await res.text();
    const item = { name, url, status: res.status(), contentType: res.headers()['content-type'] || null, bodyPreview: body.slice(0, 2000) };
    apiChecks.push(item);
    log('api_check', item);
    fs.writeFileSync(path.join(OUT, `api-${name}.txt`), body);
  } catch (e) {
    const item = { name, url, error: e.message };
    apiChecks.push(item);
    log('api_check_error', item);
  }
}

await checkApi('wp-search', 'https://tfpdl.com/wp-json/wp/v2/search?search=Farming%20Life%20In%20Another%20World&per_page=3');
await checkApi('wp-posts', 'https://tfpdl.com/wp-json/wp/v2/posts?search=Farming%20Life%20In%20Another%20World&per_page=3&_fields=id,date,link,slug,title,content,excerpt,categories,tags');
await checkApi('wp-root', 'https://tfpdl.com/wp-json/');

page.on('request', req => {
  const rt = req.resourceType();
  if (['document', 'xhr', 'fetch', 'script'].includes(rt)) {
    log('request', { method: req.method(), resourceType: rt, url: req.url(), postData: req.postData() || null });
  }
});
page.on('response', async res => {
  const req = res.request();
  const rt = req.resourceType();
  if (['document', 'xhr', 'fetch', 'script'].includes(rt)) {
    const headers = await res.allHeaders().catch(() => ({}));
    log('response', { status: res.status(), resourceType: rt, url: res.url(), location: headers.location || null, contentType: headers['content-type'] || null });
  }
  await maybeSaveBody(res);
});
page.on('framenavigated', frame => {
  if (frame === page.mainFrame()) log('navigation', { url: frame.url() });
});
page.on('console', msg => log('console', { level: msg.type(), text: msg.text().slice(0, 1000) }));
page.on('pageerror', err => log('pageerror', { message: err.message }));

const postUrl = process.env.TFPDL_POST_URL || 'https://tfpdl.com/farming-life-in-another-world-s02-dual-720p-webrip-x265-tfpdl/';
log('start', { postUrl });
await page.goto(postUrl, { waitUntil: 'domcontentloaded', timeout: 60_000 });
await page.waitForTimeout(3000);
let snap = await dumpPage(page, '01-tfpdl-post');

let downloadHref = null;
for (const l of snap.links) {
  if (/\/tfpdl\?/i.test(l.href)) { downloadHref = l.href; break; }
}
if (!downloadHref) {
  const dl = snap.links.find(l => /download/i.test(l.text));
  downloadHref = dl?.href || null;
}
if (!downloadHref) throw new Error('Could not find a TFPDL download link on the test post');
log('download_link', { downloadHref });

await page.goto(downloadHref, { waitUntil: 'domcontentloaded', timeout: 60_000 }).catch(e => log('goto_error', { message: e.message }));
await page.waitForTimeout(5000);
snap = await dumpPage(page, '02-after-tfpdl-gateway');

for (let stage = 0; stage < 3; stage++) {
  await page.waitForTimeout(stage === 0 ? 25_000 : 12_000);
  snap = await dumpPage(page, `03-stage-${stage}`);
  const hasCaptchaWords = /(captcha|verify you are human|verification code|enter the (number|code)|type the (number|code)|security code)/i.test(snap.text);
  const hasUserInput = snap.forms.some(f => f.inputs.some(i => ['text','number','tel'].includes((i.type || 'text').toLowerCase())));
  if (hasCaptchaWords || hasUserInput) {
    log('human_verification_boundary', { url: page.url(), hasCaptchaWords, hasUserInput });
    break;
  }

  const candidates = [
    page.getByRole('button', { name: /continue|get link|proceed|download/i }),
    page.getByRole('link', { name: /continue|get link|proceed|download/i })
  ];
  let clicked = false;
  for (const loc of candidates) {
    const count = await loc.count().catch(() => 0);
    for (let i = 0; i < Math.min(count, 6); i++) {
      const el = loc.nth(i);
      if (await el.isVisible().catch(() => false)) {
        const txt = (await el.innerText().catch(() => '')).trim();
        const href = await el.getAttribute('href').catch(() => null);
        log('ordinary_continue_candidate', { text: txt, href });
        await el.click({ timeout: 10_000 }).catch(e => log('click_error', { message: e.message }));
        clicked = true;
        await page.waitForTimeout(6000);
        await dumpPage(page, `04-after-click-${stage}`);
        break;
      }
    }
    if (clicked) break;
  }
  if (!clicked) {
    log('no_safe_continue_button', { url: page.url(), textPreview: snap.text.slice(0, 500) });
    break;
  }
}

const cookies = (await context.cookies()).map(c => ({ name: c.name, domain: c.domain, path: c.path, expires: c.expires, httpOnly: c.httpOnly, secure: c.secure, sameSite: c.sameSite }));
fs.writeFileSync(path.join(OUT, 'cookie-metadata.json'), JSON.stringify(cookies, null, 2));
fs.writeFileSync(path.join(OUT, 'network-events.json'), JSON.stringify(events, null, 2));

const summary = {
  startPost: postUrl,
  downloadHref,
  finalUrl: page.url(),
  domainsSeen: [...new Set(events.flatMap(e => { try { return e.url ? [new URL(e.url).hostname] : []; } catch { return []; } }))],
  xhrFetch: events.filter(e => e.type === 'request' && ['xhr','fetch'].includes(e.resourceType)).map(e => ({ method: e.method, url: e.url, postData: e.postData })),
  redirects: events.filter(e => e.type === 'response' && e.location).map(e => ({ status: e.status, url: e.url, location: e.location })),
  humanVerificationDetected: events.some(e => e.type === 'human_verification_boundary'),
  apiChecks
};
fs.writeFileSync(path.join(OUT, 'summary.json'), JSON.stringify(summary, null, 2));
console.log('\n=== SUMMARY ===\n' + JSON.stringify(summary, null, 2));
await browser.close();
