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
    await page.locator('.library-card[data-game="2048"]').click()
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
        backVisible: visible(back),
        settingsVisible: visible(settings)
      }
    })
    assert.equal(game.siteHeader, 'hidden', 'Sticky site header must not obscure game header at ' + width)
    assert.equal(game.siteTabs, 'hidden', 'Site tabs must not sit over immersive gameplay at ' + width)
    assert.ok(game.gameHeaderTop >= 0 && game.gameHeaderTop < 80, 'Game header is at top of screen at ' + width)
    assert.ok(game.backVisible && game.settingsVisible, 'Game navigation controls remain available at ' + width)
    await page.screenshot({ path: join(output, 'game-2048-' + width + '.png') })
    await page.locator('#gameDialog .game-topline [data-close-game]').click()
    assert.equal(await page.locator('body').evaluate(el => el.classList.contains('in-game')), false)
    assert.equal(await page.locator('.site-header').evaluate(el => getComputedStyle(el).visibility), 'visible')
    assert.deepEqual(errors, [], 'Browser page errors at ' + width)
    console.log('NIGHT_UI_GAME ' + JSON.stringify({ width, ...game, errors }))
    await page.close()
  }
} finally {
  if (browser) await browser.close()
  server.close()
}
