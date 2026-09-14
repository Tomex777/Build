import { test, expect } from '@playwright/test';

async function openFresh(page) {
  const errors = [];
  page.on('pageerror', error => errors.push(`pageerror: ${error.message}`));
  page.on('console', msg => { if (msg.type() === 'error') errors.push(`console: ${msg.text()}`); });
  await page.goto('/YUWAI-Standalone.html', { waitUntil: 'networkidle' });
  await expect(page.locator('#app')).toBeVisible();
  await expect(page.locator('#mobile-toolbar')).toBeVisible();
  return errors;
}

async function buildDemo(page) {
  await page.locator('#mobile-more').click();
  await expect(page.locator('[data-ma="demo"]')).toBeVisible();
  await page.locator('[data-ma="demo"]').click();
  await expect.poll(() => page.evaluate(() => store.doc.meta.name)).toBe('Aster · YUWAI Showcase');
  await expect.poll(() => page.evaluate(() => Object.keys(store.doc.nodes).length)).toBeGreaterThan(40);
  await page.waitForTimeout(120);
}

test('mobile shell loads with SVG controls and no runtime errors', async ({ page }) => {
  const errors = await openFresh(page);
  await expect(page.locator('#mobile-toolbar .mobile-tool')).toHaveCount(6);
  expect(await page.locator('#mobile-toolbar svg.ui-icon').count()).toBeGreaterThanOrEqual(6);
  const labels = (await page.locator('#mobile-toolbar .mobile-tool').allTextContents()).join(' ');
  expect(labels).not.toMatch(/[☷▣□▭⌁▧✥↶↷▶⌫⌗🔒◉⌑◇◆＋☰⧉⇳]/u);
  const metrics = await page.evaluate(() => ({ mobile:isMobileUI(), toolbar:document.querySelector('#mobile-toolbar').getBoundingClientRect().height, workspace:document.querySelector('#workspace').getBoundingClientRect().height }));
  expect(metrics.mobile).toBe(true);
  expect(metrics.toolbar).toBeGreaterThanOrEqual(60);
  expect(metrics.workspace).toBeGreaterThan(500);
  expect(errors).toEqual([]);
});

test('Aster demo builds structured editable UI and fits the phone canvas', async ({ page }) => {
  const errors = await openFresh(page);
  await buildDemo(page);
  const model = await page.evaluate(() => ({
    pages: store.doc.pages.map(p => p.name),
    nodes: Object.keys(store.doc.nodes).length,
    svgIcons: Object.values(store.doc.nodes).filter(n => n.type === 'icon').length,
    autoLayouts: Object.values(store.doc.nodes).filter(n => ['row','column','grid'].includes(n.layout?.mode)).length,
    prototypes: Object.values(store.doc.nodes).filter(n => n.prototype?.action && n.prototype.action !== 'none').length
  }));
  expect(model.pages).toEqual(['Aster · Home','Aster · Trip']);
  expect(model.nodes).toBeGreaterThanOrEqual(45);
  expect(model.svgIcons).toBeGreaterThanOrEqual(10);
  expect(model.autoLayouts).toBeGreaterThanOrEqual(5);
  expect(model.prototypes).toBeGreaterThanOrEqual(2);
  const fit = await page.evaluate(() => {
    const rootId=getPage(store.doc,store.session.pageId).rootIds[0];
    const root=document.querySelector(`[data-node-id="${rootId}"]`).getBoundingClientRect();
    const work=document.querySelector('#workspace').getBoundingClientRect();
    return {root:root.toJSON(),work:work.toJSON()};
  });
  expect(fit.root.left).toBeGreaterThanOrEqual(fit.work.left-2);
  expect(fit.root.right).toBeLessThanOrEqual(fit.work.right+2);
  expect(fit.root.top).toBeGreaterThanOrEqual(fit.work.top-2);
  expect(fit.root.bottom).toBeLessThanOrEqual(fit.work.bottom+2);
  await page.screenshot({path:'test-results/aster-editor.png',fullPage:true});
  expect(errors).toEqual([]);
});

test('mobile Layers and Properties open as dismissible bottom sheets', async ({ page }) => {
  const errors = await openFresh(page);
  await buildDemo(page);
  await page.locator('#mobile-more').click();
  await page.locator('[data-ma="layers"]').click();
  await expect(page.locator('#left-panel')).toHaveClass(/mobile-open/);
  await expect(page.locator('#mobile-scrim')).toBeVisible();
  expect(await page.locator('#left-panel .tree-row').count()).toBeGreaterThan(10);
  await page.locator('#mobile-scrim').click({position:{x:20,y:20}});
  await expect(page.locator('#left-panel')).not.toHaveClass(/mobile-open/);
  await page.locator('#mobile-more').click();
  await page.locator('[data-ma="properties"]').click();
  await expect(page.locator('#right-panel')).toHaveClass(/mobile-open/);
  await expect(page.locator('#right-panel')).toContainText('Position');
  await page.locator('#mobile-scrim').click({position:{x:20,y:20}});
  await expect(page.locator('#right-panel')).not.toHaveClass(/mobile-open/);
  expect(errors).toEqual([]);
});

test('touch drag moves a free layer, undo restores it, and long press opens actions', async ({ page }) => {
  const errors = await openFresh(page);
  await buildDemo(page);
  const target=await page.evaluate(() => Object.values(store.doc.nodes).find(n=>n.name==='Notifications').id);
  const before=await page.evaluate(id=>({x:store.doc.nodes[id].x,y:store.doc.nodes[id].y}),target);
  const locator=page.locator(`[data-node-id="${target}"]`);
  const box=await locator.boundingBox(); expect(box).toBeTruthy();
  const sx=box.x+box.width/2, sy=box.y+box.height/2;
  await locator.dispatchEvent('pointerdown',{pointerId:31,pointerType:'touch',isPrimary:true,button:0,buttons:1,clientX:sx,clientY:sy});
  await page.evaluate(({x,y})=>window.dispatchEvent(new PointerEvent('pointermove',{pointerId:31,pointerType:'touch',isPrimary:true,buttons:1,clientX:x,clientY:y,bubbles:true})),{x:sx-24,y:sy+30});
  await page.evaluate(({x,y})=>window.dispatchEvent(new PointerEvent('pointerup',{pointerId:31,pointerType:'touch',isPrimary:true,button:0,clientX:x,clientY:y,bubbles:true})),{x:sx-24,y:sy+30});
  const after=await page.evaluate(id=>({x:store.doc.nodes[id].x,y:store.doc.nodes[id].y}),target);
  expect(after).not.toEqual(before);
  await page.evaluate(()=>store.undo());
  await expect.poll(()=>page.evaluate(id=>({x:store.doc.nodes[id].x,y:store.doc.nodes[id].y}),target)).toEqual(before);

  const box2=await locator.boundingBox(); const x=box2.x+box2.width/2, y=box2.y+box2.height/2;
  await locator.dispatchEvent('pointerdown',{pointerId:51,pointerType:'touch',isPrimary:true,button:0,buttons:1,clientX:x,clientY:y});
  await page.waitForTimeout(620);
  await expect(page.locator('.context-menu')).toBeVisible();
  await expect(page.locator('.context-menu')).toContainText('Duplicate');
  await expect(page.locator('.context-menu')).toContainText('Create component');
  await page.evaluate(({x,y})=>window.dispatchEvent(new PointerEvent('pointerup',{pointerId:51,pointerType:'touch',clientX:x,clientY:y,bubbles:true})),{x,y});
  expect(errors).toEqual([]);
});

test('two-finger pinch zoom works without mutating document nodes', async ({ page }) => {
  const errors = await openFresh(page);
  await buildDemo(page);
  const before=await page.evaluate(()=>({zoom:store.session.zoom,nodes:Object.keys(store.doc.nodes).length}));
  const work=await page.locator('#workspace').boundingBox(); const cy=work.y+work.height/2; const x1=work.x+work.width/2-35; const x2=work.x+work.width/2+35;
  await page.locator('#workspace').dispatchEvent('pointerdown',{pointerId:41,pointerType:'touch',isPrimary:true,button:0,buttons:1,clientX:x1,clientY:cy});
  await page.locator('#workspace').dispatchEvent('pointerdown',{pointerId:42,pointerType:'touch',isPrimary:false,button:0,buttons:1,clientX:x2,clientY:cy});
  await page.evaluate(({x,y})=>window.dispatchEvent(new PointerEvent('pointermove',{pointerId:41,pointerType:'touch',isPrimary:true,buttons:1,clientX:x,clientY:y,bubbles:true})),{x:x1-30,y:cy});
  await page.evaluate(({x,y})=>window.dispatchEvent(new PointerEvent('pointermove',{pointerId:42,pointerType:'touch',isPrimary:false,buttons:1,clientX:x,clientY:y,bubbles:true})),{x:x2+30,y:cy});
  expect(await page.evaluate(()=>store.session.zoom)).toBeGreaterThan(before.zoom);
  await page.evaluate(({x,y})=>window.dispatchEvent(new PointerEvent('pointerup',{pointerId:41,pointerType:'touch',clientX:x,clientY:y,bubbles:true})),{x:x1-30,y:cy});
  await page.evaluate(({x,y})=>window.dispatchEvent(new PointerEvent('pointerup',{pointerId:42,pointerType:'touch',clientX:x,clientY:y,bubbles:true})),{x:x2+30,y:cy});
  expect(await page.evaluate(()=>Object.keys(store.doc.nodes).length)).toBe(before.nodes);
  expect(errors).toEqual([]);
});

test('prototype fits mobile modal and navigates Home → Trip → Home', async ({ page }) => {
  const errors = await openFresh(page);
  await buildDemo(page);
  await page.locator('#mobile-more').click();
  await page.locator('[data-ma="preview"]').click();
  await expect(page.locator('#preview-phone')).toBeVisible();
  const fit=await page.evaluate(()=>{const p=document.querySelector('#preview-phone').getBoundingClientRect();const s=document.querySelector('#preview-stage').getBoundingClientRect();return{p:p.toJSON(),s:s.toJSON(),title:document.querySelector('.modal-head span').textContent};});
  expect(fit.p.left).toBeGreaterThanOrEqual(fit.s.left-2); expect(fit.p.right).toBeLessThanOrEqual(fit.s.right+2); expect(fit.p.top).toBeGreaterThanOrEqual(fit.s.top-2); expect(fit.p.bottom).toBeLessThanOrEqual(fit.s.bottom+2); expect(fit.title).toContain('Aster · Home');
  const hero=page.locator('#preview-phone > div').filter({hasText:'Quiet light'}).first(); await expect(hero).toBeVisible(); await hero.click();
  await expect(page.locator('.modal-head span')).toContainText('Aster · Trip'); await expect(page.locator('#preview-phone')).toContainText('Save this trip');
  await page.locator('#preview-phone').getByText('Save this trip',{exact:true}).click(); await expect(page.locator('.modal-head span')).toContainText('Aster · Home');
  await page.screenshot({path:'test-results/aster-prototype.png',fullPage:true});
  expect(errors).toEqual([]);
});

test('.yuwai ZIP bytes contain handoff/previews and round-trip back into the model', async ({ page }) => {
  const errors = await openFresh(page);
  await buildDemo(page);
  const result=await page.evaluate(async()=>{const bytes=buildProjectBytes(store.doc);const files=readZip(bytes);const names=Object.keys(files).sort();const parsed=await parseProjectBytes(bytes);const handoff=JSON.parse(bytesText(files['handoff/design.json']));return{size:bytes.length,names,parsedName:parsed.meta.name,parsedPages:parsed.pages.length,parsedNodes:Object.keys(parsed.nodes).length,handoffKind:handoff.kind,handoffNodes:Object.keys(handoff.nodes).length};});
  expect(result.size).toBeGreaterThan(10000); expect(result.names).toContain('manifest.json'); expect(result.names).toContain('document.json'); expect(result.names).toContain('tokens.json'); expect(result.names).toContain('handoff/design.json'); expect(result.names.filter(n=>n.startsWith('previews/')&&n.endsWith('.svg')).length).toBe(2); expect(result.parsedName).toBe('Aster · YUWAI Showcase'); expect(result.parsedPages).toBe(2); expect(result.parsedNodes).toBeGreaterThanOrEqual(45); expect(result.handoffKind).toBe('YUWAI Design Handoff'); expect(result.handoffNodes).toBe(result.parsedNodes);
  expect(errors).toEqual([]);
});
