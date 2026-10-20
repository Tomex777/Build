'use strict'
const assert = require('node:assert/strict')
const { mkdtempSync, writeFileSync, readFileSync, symlinkSync, readdirSync, rmSync, mkdirSync } = require('node:fs')
const { tmpdir } = require('node:os')
const path = require('node:path')
const { updateSetting } = require('./protected-settings-write.cjs')

const root = mkdtempSync(path.join(tmpdir(), 'cortex-settings-'))
try {
  const folder = path.join(root, 'private')
  mkdirSync(folder)
  const schemaPath = path.join(folder, 'schema.json')
  const settingsPath = path.join(folder, 'settings.json')
  writeFileSync(schemaPath, JSON.stringify({ entries: [
    { key: 'anti-delete', type: 'boolean' },
    { key: 'ignored-secret', type: 'text' },
  ] }))
  writeFileSync(settingsPath, JSON.stringify({
    version: 3,
    'anti-delete': false,
    'other-flag': true,
    cc: { policy: 'main-control-account', defaultDestinationAccountId: 'A' },
  }))
  const args = { schemaPath, settingsPath, grantAcl: () => {} }
  assert.equal(updateSetting({ ...args, key: 'anti-delete', enabled: true }), true)
  const updated = JSON.parse(readFileSync(settingsPath, 'utf8'))
  assert.equal(updated['anti-delete'], true)
  assert.equal(updated['other-flag'], true)
  assert.equal(updated.cc.defaultDestinationAccountId, 'A')
  assert.equal(updated.version, 3)
  assert.ok(Number.isInteger(updated.savedAt))
  updateSetting({ ...args, key: 'anti-delete', enabled: false })
  assert.equal(JSON.parse(readFileSync(settingsPath, 'utf8'))['anti-delete'], false)
  const before = readFileSync(settingsPath, 'utf8')
  for (const key of ['ignored-secret', 'bad;key', '__proto__', 'missing']) {
    assert.throws(() => updateSetting({ ...args, key, enabled: true }))
    assert.equal(readFileSync(settingsPath, 'utf8'), before)
  }
  assert.throws(() => updateSetting({ ...args, key: 'anti-delete', enabled: 'true' }))
  assert.equal(readFileSync(settingsPath, 'utf8'), before)
  assert.ok(!readdirSync(folder).some(name => name.startsWith('.cortex-settings-')))

  const link = path.join(folder, 'settings-link.json')
  symlinkSync(settingsPath, link)
  assert.throws(() => updateSetting({ ...args, settingsPath: link, key: 'anti-delete', enabled: true }))
  assert.equal(readFileSync(settingsPath, 'utf8'), before)
  assert.throws(() => updateSetting({ ...args, key: 'anti-delete', enabled: true,
    grantAcl: () => { throw new Error('ACL unavailable') },
  }))
  assert.equal(readFileSync(settingsPath, 'utf8'), before)
  assert.ok(!readdirSync(folder).some(name => name.startsWith('.cortex-settings-')))
  console.log('PASS protected Cortex settings helper: schema allowlist, atomic writes, private state preservation, ACL failure safety')
} finally {
  rmSync(root, { recursive: true, force: true })
}
