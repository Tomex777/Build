'use strict';
// Privileged entry point used exclusively by the fixed-path Cortex sudo helper.
// Never let HTTP callers provide file paths or arbitrary JSON to this process.
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const { execFileSync } = require('node:child_process')

function updateSetting({ schemaPath, settingsPath, key, enabled, grantAcl } = {}) {
  if (!/^[A-Za-z_][A-Za-z0-9._-]{0,79}$/.test(String(key || '')) || typeof enabled !== 'boolean') {
    throw new Error('Invalid setting request')
  }
  for (const name of [schemaPath, settingsPath]) {
    if (typeof name !== 'string' || !path.isAbsolute(name)) throw new Error('Invalid settings path')
  }
  const dir = path.dirname(settingsPath)
  if (!fs.statSync(dir).isDirectory() || fs.lstatSync(dir).isSymbolicLink()) {
    throw new Error('Settings directory is not a regular directory')
  }
  if (fs.lstatSync(schemaPath).isSymbolicLink()) throw new Error('Settings schema is a symlink')
  const schemaStat = fs.statSync(schemaPath)
  if (!schemaStat.isFile() || schemaStat.size > 1024 * 1024) throw new Error('Invalid settings schema')
  const schema = JSON.parse(fs.readFileSync(schemaPath, 'utf8'))
  if (!Array.isArray(schema.entries) ||
      !schema.entries.some(entry => entry && entry.key === key && entry.type === 'boolean')) {
    throw new Error('Unknown or non-boolean setting')
  }

  let original = null
  try {
    const info = fs.lstatSync(settingsPath)
    if (!info.isFile() || info.isSymbolicLink() || info.size > 2 * 1024 * 1024) {
      throw new Error('Unsafe settings file')
    }
    original = info
  } catch (error) {
    if (error.code !== 'ENOENT') throw error
  }
  const raw = original ? JSON.parse(fs.readFileSync(settingsPath, 'utf8')) : {}
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('Invalid settings state')
  }
  const next = { ...raw, version: Math.max(1, Number(raw.version) || 1), [key]: enabled, savedAt: Date.now() }
  const output = JSON.stringify(next, null, 2) + '\n'
  const temp = path.join(dir, '.cortex-settings-' + crypto.randomBytes(12).toString('hex'))
  const mode = original ? original.mode & 0o777 : 0o600
  const owner = original || fs.statSync(dir)
  let fd
  try {
    fd = fs.openSync(temp, fs.constants.O_WRONLY | fs.constants.O_CREAT |
      fs.constants.O_EXCL | (fs.constants.O_NOFOLLOW || 0), 0o600)
    if (process.getuid?.() === 0) fs.fchownSync(fd, owner.uid, owner.gid)
    fs.fchmodSync(fd, mode)
    fs.writeFileSync(fd, output, 'utf8')
    fs.fsyncSync(fd)
    fs.closeSync(fd)
    fd = undefined

    // Grant the service account read access BEFORE swapping the file. Do not
    // make private bot settings globally readable or grant directory writes.
    ;(grantAcl || ((file) => execFileSync('/usr/bin/setfacl',
      ['-m', 'u:cortex-agent:r--', file], { stdio: 'ignore' })))(temp)
    fs.renameSync(temp, settingsPath)
    return true
  } finally {
    if (fd !== undefined) fs.closeSync(fd)
    try { fs.unlinkSync(temp) } catch (error) { if (error.code !== 'ENOENT') throw error }
  }
}

module.exports = { updateSetting }

if (require.main === module) {
  if (process.argv.length !== 6 || !['true', 'false'].includes(process.argv[5])) {
    process.exitCode = 64
  } else {
    try {
      updateSetting({
        schemaPath: process.argv[2],
        settingsPath: process.argv[3],
        key: process.argv[4],
        enabled: process.argv[5] === 'true',
      })
    } catch {
      // Do not print key values, settings, permissions or secret metadata.
      process.stderr.write('Cortex protected setting update failed.\n')
      process.exitCode = 1
    }
  }
}
