'use strict';
// Agent E: independent, deterministic Channels regression. This is a local
// fixture, NOT evidence that production MSCC has passed authorization tests.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const http = require('node:http');
const { chromium } = require('playwright');
const root = path.resolve(__dirname, '../..');
const target = path.resolve(__dirname, '..', 'index.html');
const out = path.join(root, 'visual-output', 'channels-agent-e');
const channelA = '120363282083849178@newsletter';
const awaitingA = '120363282083849180@newsletter';
const channelB = '120363282083849179@newsletter';
const nameA = '𝙉𝙄𝙂𝙃𝙏 🌙👩🏽‍💻 العربية हिन्दी 日本語 A very long channel name that must wrap gracefully';
const maliciousName = 'Private A <img src=x onerror=alert(1)>';
const ownerState = { A: { linked: true, disconnected: false, allowStart: true }, B: { linked: true, disconnected: false, allowStart: false } };
const requests = [];
let outage = false;
const serve = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://127.0.0.1');
  const headers = { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' };
  const reply = (status, data) => { res.writeHead(status, headers); res.end(JSON.stringify(data)); };
  if (url.pathname === '/site-shell.css') {
    res.writeHead(200, { 'Content-Type': 'text/css' });
    return res.end(fs.readFileSync(path.join(root, 'site-shell.css')));
  }
  if (url.pathname === '/channels/' || url.pathname === '/channels/index.html') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    return res.end(fs.readFileSync(target));
  }
  if (url.pathname === '/anonymous/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    return res.end('<!doctype html><title>Test-only owner session</title>');
  }
  if (url.pathname !== '/api/account') return reply(404, { error: 'Not found' });
  if (outage) return reply(503, { error: 'NIGHT connection is unavailable' });
  const who = /(?:^|;\s*)qa_owner=([AB])(?:;|$)/.exec(req.headers.cookie || '')?.[1];
  const state = ownerState[who];
  if (!state) return reply(401, { error: 'Not authenticated' });
  if (req.method === 'GET') return reply(200, {
    linked: state.linked,
    profile: state.linked ? { id: who, displayName: 'QA ' + who } : null
  });
  let input;
  try {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    input = JSON.parse(Buffer.concat(chunks).toString('utf8'));
  } catch { return reply(400, { error: 'Invalid JSON' }); }
  const { action, channelId } = input;
  requests.push({ who, action, channelId });
  if (!state.linked) return reply(403, { error: 'Disconnected' });
  const accessible = who === 'A' ? [channelA, awaitingA] : [channelB];
  if (channelId && !accessible.includes(channelId))
    return reply(403, { error: 'Channel not owned by this account' });
  if (action === 'channel:list') {
    if (state.disconnected) return reply(200, { channels: [] });
    return reply(200, { channels: who === 'A' ? [
      { id: channelA, name: nameA, status: 'connected', anonymousStartEnabled: state.allowStart,
        photoData: 'data:image/svg+xml;base64,PHN2Zy8+' },
      { id: awaitingA, name: maliciousName, status: 'awaiting_admin', proof: 'NIGHT-VERIFY-ABC123' }
    ] : [
      { id: channelB, name: 'Owner B only', status: 'connected', anonymousStartEnabled: state.allowStart }
    ] });
  }
  if (action === 'anonymous:start') {
    if (channelId === awaitingA || !state.allowStart || state.disconnected)
      return reply(403, { error: 'Not enabled for this owner/channel' });
    return reply(200, { sessionId: 'NIGHT_QA_SESSION_12345' });
  }
  if (action === 'channel:remove') {
    state.disconnected = true;
    state.allowStart = false;
    return reply(200, { ok: true });
  }
  if (action === 'channel:verify') return reply(200, { connected: false, reason: 'admin' });
  return reply(400, { error: 'Unknown action' });
});
function assertNoOverflow(page, width) {
  return page.evaluate(width => {
    const doc = document.documentElement;
    return { scroll: doc.scrollWidth, viewport: innerWidth, width,
      offenders: [...document.querySelectorAll('body *')].filter(e => {
        if (getComputedStyle(e).display === 'none') return false;
        const r = e.getBoundingClientRect();
        return r.width > 0 && (r.right > innerWidth + 1 || r.left < -1);
      }).slice(0, 10).map(e => e.tagName + '.' + e.className) };
  }, width).then(info => assert.ok(info.scroll <= info.viewport + 1,
    JSON.stringify(info)));
}
async function cookieContext(browser, owner, width, height = 844) {
  const context = await browser.newContext({
    viewport: { width, height }, isMobile: width < 600, hasTouch: width < 600,
    deviceScaleFactor: 1
  });
  await context.addCookies([{ name: 'qa_owner', value: owner, url: base }]);
  return context;
}
let base = '';
async function main() {
  await new Promise(resolve => serve.listen(0, '127.0.0.1', resolve));
  base = 'http://127.0.0.1:' + serve.address().port;
  fs.mkdirSync(out, { recursive: true });
  const browser = await chromium.launch({ headless: true, channel: 'chrome', args: ['--no-sandbox'] });
  const failures = [], findings = [];
  try {
    // Locked approved Channels screen across all requested breakpoints.
    for (const width of [320, 360, 390, 412, 1280]) {
      const context = await cookieContext(browser, 'A', width, width === 1280 ? 900 : 844);
      const page = await context.newPage();
      const pageErrors = [];
      page.on('pageerror', e => pageErrors.push(e.message));
      await page.goto(base + '/channels/');
      await page.getByRole('heading', { name: nameA }).waitFor();
      assert.equal(await page.locator('article.channel-card').count(), 2);
      assert.equal(await page.locator('.channel-name').first().innerText(), nameA);
      assert.equal(await page.locator('.channel-avatar img').count(), 0,
        'Unapproved SVG data URI must not become a remote/unsafe image');
      assert.equal(await page.locator('article.channel-card img').count(), 0);
      assert.equal(await page.locator('article.channel-card').nth(1).getByRole('heading',
        { name: maliciousName, exact: true }).count(), 1, 'Names are text, not HTML');
      assert.equal(await page.locator('.site-header .brand-mark').count(), 1);
      assert.equal(await page.locator('nav.mobile-nav').isVisible(), width < 600);
      await assertNoOverflow(page, width);
      for (const btn of await page.locator('.channel-actions button').all()) {
        const rect = await btn.boundingBox();
        assert.ok(rect && rect.height >= 40 && rect.width >= 40,
          'Touch target under 40 CSS px at ' + width);
      }
      await page.screenshot({ path: path.join(out, 'owner-a-' + width + '.png'), fullPage: true });
      assert.deepEqual(pageErrors, [], 'No JS runtime exceptions at ' + width);
      await context.close();
    }
    // Browser contexts are independent; interface cannot leak account A
    // when backend returns only B, and hostile direct ID mutations are denied.
    const ctxA = await cookieContext(browser, 'A', 390);
    const ctxB = await cookieContext(browser, 'B', 390);
    const pA = await ctxA.newPage();
    const pB = await ctxB.newPage();
    await Promise.all([pA.goto(base + '/channels/'), pB.goto(base + '/channels/')]);
    await pA.getByRole('heading', { name: nameA }).waitFor();
    await pB.getByRole('heading', { name: 'Owner B only' }).waitFor();
    assert.equal(await pB.getByText(nameA).count(), 0, 'Other owner is never rendered');
    assert.equal(await pA.getByText('Owner B only').count(), 0);
    for (const [page, foreignId] of [[pA, channelB], [pB, channelA]]) {
      const check = await page.evaluate(async id => {
        const response = await fetch('/api/account', {
          method: 'POST', credentials: 'same-origin',
          headers: { 'content-type': 'application/json' },
          body: JSON.stringify({ action: 'anonymous:start', channelId: id })
        });
        return response.status;
      }, foreignId);
      assert.equal(check, 403, 'Fixture rejects cross-owner send');
    }
    await pB.screenshot({ path: path.join(out, 'owner-b-390.png'), fullPage: true });
    // A permission loss after page rendering must stop the POST even if
    // the stale button still looks enabled.
    ownerState.A.allowStart = false;
    await pA.locator('article.channel-card').first().getByRole('button',
      { name: /Start Anonymous Night/ }).click();
    await pA.locator('#toast:not([hidden])').waitFor();
    assert.match(await pA.locator('#toast').innerText(), /Not enabled/);
    assert.equal(new URL(pA.url()).pathname, '/channels/');
    // Backend outage must not be mistaken for unlinking, and must recover.
    outage = true;
    await pB.reload();
    await pB.getByRole('heading', { name: 'Channels unavailable' }).waitFor();
    assert.equal(await pB.getByRole('link', { name: 'Connect WhatsApp' }).isVisible(), false);
    await pB.screenshot({ path: path.join(out, 'owner-b-outage-390.png'), fullPage: true });
    outage = false;
    await pB.getByRole('button', { name: 'Try again' }).click();
    await pB.getByRole('heading', { name: 'Owner B only' }).waitFor();
    // Disconnect confirmation and empty-state behavior are owner-specific.
    await pB.getByRole('button', { name: 'Disconnect', exact: true }).click();
    await pB.getByRole('button', { name: 'Keep channel' }).click();
    assert.equal(ownerState.B.disconnected, false);
    await pB.getByRole('button', { name: 'Disconnect', exact: true }).click();
    await pB.getByRole('button', { name: 'Disconnect channel' }).click();
    await pB.getByRole('heading', { name: 'No channels connected' }).waitFor();
    assert.equal(ownerState.A.disconnected, false, 'Other account is untouched');
    await pB.screenshot({ path: path.join(out, 'owner-b-empty-390.png'), fullPage: true });
    await ctxA.close(); await ctxB.close();

    // Explicitly do not mark unimplemented v1 gates as passed.
    findings.push('BLOCKED: automation per-module OFF-default server persistence not deployed here');
    findings.push('BLOCKED: four-card review, approve/decline and queue cannot yet be exercised');
    findings.push('BLOCKED: real MSCC owner/channel final-send authorization cannot be proven with a local mock');
    findings.push('BLOCKED: genuine WhatsApp channel image/caption/send/retry and 1GB RSS require authorized infrastructure');
    fs.writeFileSync(path.join(out, 'result.json'), JSON.stringify({
      scope: 'Source branch only; fixture-backed UI/browser checks', widths: [320,360,390,412,1280],
      screenshots: fs.readdirSync(out).filter(f => f.endsWith('.png')),
      fixturesNotProduction: true, findings, requests, failures
    }, null, 2));
    console.log('PASS Agent E fixture-backed Channels cross-viewport/isolated-owner visual browser QA');
    console.log(findings.map(x => '- ' + x).join('\n'));
  } catch (error) {
    failures.push(error.stack || String(error));
    fs.writeFileSync(path.join(out, 'failure.json'), JSON.stringify({ failures, requests }, null, 2));
    throw error;
  } finally {
    await browser.close();
    serve.closeAllConnections();
    await new Promise(resolve => serve.close(resolve));
  }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
