import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { readFileSync, mkdirSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'
import { chromium } from 'playwright'

const here = dirname(fileURLToPath(import.meta.url))
const root = join(here, '..')
const read = path => readFileSync(join(root, path))
const server = createServer((req, res) => {
  const path = new URL(req.url || '/', 'http://localhost').pathname
  if (path === '/site-shell.css') {
    res.writeHead(200, { 'content-type': 'text/css' })
    res.end(read('site-shell.css'))
  } else if (path === '/account-client.js' || path === '/player-identity.js') {
    res.writeHead(200, { 'content-type': 'text/javascript' })
    res.end(read(path.substring(1)))
  } else if (path === '/api/account') {
    res.writeHead(200, { 'content-type': 'application/json' })
    res.end(JSON.stringify({ linked: false, profile: null }))
  } else {
    res.writeHead(200, { 'content-type': 'text/html; charset=utf-8' })
    res.end(read('index.html'))
  }
})
await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
const url = 'http://127.0.0.1:' + server.address().port
const output = join(here, 'visual-output')
mkdirSync(output, { recursive: true })
let browser
try {
  browser = await chromium.launch({ headless: true, channel: 'chrome', args: ['--no-sandbox'] })
  const sizes = [320, 360, 390, 412, 640, 760, 1280]
  for (const width of sizes) {
    const mobile = width <= 412
    const page = await browser.newPage({
      viewport: { width, height: mobile ? 844 : 900 },
      hasTouch: mobile,
      isMobile: mobile,
      deviceScaleFactor: 1
    })
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.goto(url + '/dashboard')
    await page.locator('#view-dashboard').waitFor({ state: 'visible' })
    const onSite = await page.evaluate(() => {
      const header = document.querySelector('.site-header')
      const logo = header.querySelector('.logo').getBoundingClientRect()
      const links = [...header.querySelectorAll('.links a')].map(a => a.getBoundingClientRect().height)
      const mobileBar = document.querySelector('.mobile-nav')
      return {
        overflow: document.documentElement.scrollWidth - innerWidth,
        headerVisibility: getComputedStyle(header).visibility,
        logoHeight: logo.height,
        linkHeights: links,
        mobileNav: getComputedStyle(mobileBar).display,
        mobileTabHeights: [...mobileBar.querySelectorAll('a')].map(a => a.getBoundingClientRect().height)
      }
    })
    console.log('NIGHT_UI_SITE ' + JSON.stringify({ width, ...onSite }))
    assert.ok(onSite.overflow <= 1, 'NIGHT Play has no horizontal overflow at ' + width)
    assert.ok(onSite.logoHeight >= 44, 'Header logo has minimum touch target at ' + width)
    if (mobile) {
      assert.equal(onSite.mobileNav, 'grid', 'Mobile site navigation exists at ' + width)
      assert.ok(onSite.mobileTabHeights.every(height => height >= 44), 'Mobile tabs are touch-safe at ' + width)
    } else {
      assert.ok(onSite.linkHeights.every(height => height >= 44), 'Desktop nav links are keyboard/mouse accessible at ' + width)
    }
    await page.screenshot({ path: join(output, 'site-' + width + '.png') })
    await page.goto(url + '/games')
    await page.locator('#view-games').waitFor({ state: 'visible' })
    const library = await page.evaluate(() => ({
      cards: document.querySelectorAll('.library-card').length,
      overflow: document.documentElement.scrollWidth - innerWidth,
      emojiThumbnails: document.querySelectorAll('.library-card .game-emoji').length
    }))
    assert.ok(library.cards >= 10, 'The original NIGHT catalogue must remain populated at ' + width)
    assert.ok(library.overflow <= 1, 'The game library must not horizontally overflow at ' + width)
    assert.equal(library.emojiThumbnails, 0, 'Library should retain original artwork at ' + width)
    await page.screenshot({ path: join(output, 'library-' + width + '.png') })
    console.log('NIGHT_UI_LIBRARY ' + JSON.stringify({ width, ...library }))
    // A1 granted the scoped CSS edit window in issue #459; check the actual
    // integrated stylesheet served by this test, not a dynamically injected mock.
    const discovery = await page.evaluate(() => {
      const box = selector => {
        const el = document.querySelector(selector)
        const rect = el.getBoundingClientRect()
        const style = getComputedStyle(el)
        return { width: rect.width, height: rect.height, outline: style.outlineColor }
      }
      return { filter: box('#view-games [data-filter="puzzle"]'),
        search: box('#view-games #gameSearch'),
        overflow: document.documentElement.scrollWidth - innerWidth }
    })
    assert.ok(discovery.filter.height >= 44, 'Puzzle filter has 44px touch target at ' + width)
    assert.ok(discovery.search.height >= 44, 'Search field has 44px touch target at ' + width)
    assert.ok(discovery.overflow <= 1, 'Discovery layout must not overflow at ' + width)
    console.log('NIGHT_UI_WAVE2_DISCOVERY ' + JSON.stringify({ width, ...discovery }))

    // Wave 2 journey A: the actual category + search controls must work as
    // accessible filters before a keyboard or touch user opens a game.
    await page.locator('.filter-btn[data-filter="puzzle"]').click()
    assert.equal(await page.locator('.filter-btn[data-filter="puzzle"]').getAttribute('aria-pressed'), 'true')
    const puzzleCards = await page.locator('.library-card:visible').count()
    assert.ok(puzzleCards >= 2 && puzzleCards < library.cards,
      'Puzzle filter must show a strict non-empty subset of the catalogue at ' + width)
    await page.screenshot({ path: join(output, 'library-puzzles-' + width + '.png') })
    await page.locator('#gameSearch').fill('2048')
    const matches = await page.locator('.library-card:visible').count()
    assert.equal(matches, 1, 'Search within Puzzle category must find only 2048 at ' + width)
    assert.match((await page.locator('#libraryCount').textContent()) || '', /1 game found/)
    await page.screenshot({ path: join(output, 'library-search-' + width + '.png') })
    // Programmatic focus + Enter exercises the existing role=button keyboard
    // handler on the real non-anchor 2048 library card.
    await page.locator('.library-card[data-game="2048"]').focus()
    await page.keyboard.press('Enter')
    await page.locator('body.in-game').waitFor()
    await page.locator('#board2048').waitFor({ state: 'visible' })
    const game = await page.evaluate(() => {
      const header = document.querySelector('.site-header')
      const mobileBar = document.querySelector('.mobile-nav')
      const gameHeader = document.querySelector('#gameDialog .game-topline')
      const back = gameHeader.querySelector('[data-close-game]')
      const settings = gameHeader.querySelector('#gameSettings')
      const visible = el => {
        const b = el.getBoundingClientRect()
        const css = getComputedStyle(el)
        return css.visibility !== 'hidden' && css.display !== 'none' && b.width > 0 && b.height > 0
      }
      return {
        siteHeader: getComputedStyle(header).visibility,
        siteTabs: getComputedStyle(mobileBar).visibility,
        gameHeaderTop: gameHeader.getBoundingClientRect().top,
        scrollTop: document.querySelector('#gameDialog').scrollTop,
        backTop: back.getBoundingClientRect().top,
        backBottom: back.getBoundingClientRect().bottom,
        backVisible: visible(back),
        backHeight: back.getBoundingClientRect().height,
        redundantExitVisible: visible(document.querySelector('#gameDialog .modal-actions [data-close-game]')),
        settingsVisible: visible(settings)
      }
    })
    assert.equal(game.siteHeader, 'hidden', 'Sticky site header must not obscure game header at ' + width)
    assert.equal(game.siteTabs, 'hidden', 'Site tabs must not sit over immersive gameplay at ' + width)
    assert.ok(game.gameHeaderTop >= 0 && game.gameHeaderTop < 80, 'Game header is at top of screen at ' + width)
    assert.ok(game.backVisible && game.settingsVisible, 'Game navigation controls remain available at ' + width)
    assert.ok(game.backTop >= 0 && game.backBottom < 80, 'The actual back button must be within the visible screen at ' + width)
    assert.equal(game.redundantExitVisible, false, 'Keep one back/exit control rather than a duplicate at ' + width)
    if (width <= 360) assert.ok(game.backHeight <= 42, 'Narrow-phone back label must not wrap at ' + width)
    await page.screenshot({ path: join(output, 'game-2048-' + width + '.png') })
    // Perform real gameplay input rather than treating page load as proof.
    // Seed a deterministic left-edge tile and require a swipe or key to move it.
    await page.evaluate(() => {
      grid = [[2, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0], [0, 0, 0, 0]]
      tileScore = 0
      draw2048()
    })
    if (mobile) {
      const bounds = await page.locator('#board2048').boundingBox()
      const cdp = await page.context().newCDPSession(page)
      const startX = bounds.x + bounds.width * .25
      const y = bounds.y + bounds.height * .5
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: startX, y, id: 9 }] })
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: startX + 75, y, id: 9 }] })
      await page.waitForFunction(() => grid[0][3] === 2, null, { timeout: 1500 })
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] })
    } else {
      await page.keyboard.press('ArrowRight')
      await page.waitForFunction(() => grid[0][3] === 2, null, { timeout: 1500 })
    }
    await page.screenshot({ path: join(output, 'game-after-move-' + width + '.png') })
    await page.locator('#gameSettings').click()
    assert.equal(await page.locator('#settingsDialog').isVisible(), true, 'Game settings must open at ' + width)
    await page.locator('#closeSettings').click()
    assert.equal(await page.locator('#settingsDialog').isVisible(), false, 'Game settings must close at ' + width)
    await page.locator('#gameDialog .game-topline [data-close-game]').click()
    assert.equal(await page.locator('body').evaluate(el => el.classList.contains('in-game')), false)
    assert.equal(await page.locator('.site-header').evaluate(el => getComputedStyle(el).visibility), 'visible')
    await page.goto(url + '/profile')
    await page.locator('#view-profile').waitFor({ state: 'visible' })
    assert.ok(await page.locator('#editPlayerName').isVisible(), 'Player name editor is accessible at ' + width)
    const profileOverflow = await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)
    assert.ok(profileOverflow <= 1, 'Profile does not overflow horizontally at ' + width)
    await page.screenshot({ path: join(output, 'profile-' + width + '.png') })
    const profileEditTarget = await page.locator('#editPlayerName').evaluate(el => el.getBoundingClientRect().height)
    assert.ok(profileEditTarget >= 44, 'Edit-name control has 44px touch target at ' + width)
    // Wave 2 journey B: guest-name change must affect actual saved profile
    // state and the Home greeting, not simply a temporary text decoration.
    await page.locator('#editPlayerName').click()
    const editFormTargets = await page.evaluate(() => ({
      input: document.querySelector('#playerNameInput').getBoundingClientRect().height,
      save: document.querySelector('#playerNameForm button[type="submit"]').getBoundingClientRect().height,
      cancel: document.querySelector('#cancelPlayerName').getBoundingClientRect().height,
      overflow: document.documentElement.scrollWidth - innerWidth
    }))
    assert.ok(editFormTargets.input >= 44 && editFormTargets.save >= 44 && editFormTargets.cancel >= 44,
      'Profile custom input and buttons must be touch-safe at ' + width)
    assert.ok(editFormTargets.overflow <= 1, 'Expanded profile editor must not overflow at ' + width)
    await page.screenshot({ path: join(output, 'profile-edit-' + width + '.png') })
    await page.locator('#playerNameInput').fill('NIGHT Tester')
    await page.locator('#playerNameForm button[type="submit"]').click()
    assert.equal((await page.locator('#playerDisplayName').textContent()).trim(), 'NIGHT Tester')
    await page.reload()
    await page.locator('#view-profile').waitFor({ state: 'visible' })
    assert.equal((await page.locator('#playerDisplayName').textContent()).trim(), 'NIGHT Tester',
      'Guest profile name must survive reloading this same device at ' + width)
    const homeNav = mobile
      ? '.mobile-nav a[href="/dashboard"]'
      : '.site-header .links a[href="/dashboard"]'
    await page.locator(homeNav).click()
    await page.locator('#view-dashboard').waitFor({ state: 'visible' })
    assert.match((await page.locator('#homeGreeting').textContent()) || '', /NIGHT Tester/,
      'Home must use the saved name at ' + width)
    await page.screenshot({ path: join(output, 'dashboard-renamed-' + width + '.png') })
    // Keep measuring touch ergonomics; these are currently audit metrics,
    // not passing claims. A9 will gate fixes once A1 grants the CSS lock.
    const touchAudit = await page.evaluate(() => {
      const metrics = selector => [...document.querySelectorAll(selector)]
        .filter(el => getComputedStyle(el).display !== 'none')
        .map(el => ({ name: el.textContent?.trim().slice(0,40),
          width: Math.round(el.getBoundingClientRect().width),
          height: Math.round(el.getBoundingClientRect().height) }))
      return { header: metrics('.site-header a'), tabs: metrics('.mobile-nav a'),
        dashboardLinks: metrics('#view-dashboard a') }
    })
    console.log('NIGHT_UI_WAVE2_TOUCH_AUDIT ' + JSON.stringify({ width, ...touchAudit }))
    console.log('NIGHT_UI_PROFILE ' + JSON.stringify({ width, overflow: profileOverflow }))
    assert.deepEqual(errors, [], 'Browser page errors at ' + width)
    console.log('NIGHT_UI_GAME ' + JSON.stringify({ width, ...game, errors }))
    await page.close()
  }
} finally {
  if (browser) await browser.close()
  server.close()
}
