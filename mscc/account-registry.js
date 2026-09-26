import { mkdir, readFile, rename, writeFile } from 'node:fs/promises'
import { dirname, join, resolve } from 'node:path'

const ID_RE = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/
const PHONE_RE = /^\d{7,15}$/

const digits = value => String(value || '').replace(/\D/g, '')

function normalizeRecord(row) {
  const id = String(row?.id || '').trim()
  const phoneNumber = digits(row?.phoneNumber)
  const authDir = String(row?.authDir || '').trim()
  if (!ID_RE.test(id) || !PHONE_RE.test(phoneNumber) || !authDir) return null
  return {
    id,
    phoneNumber,
    displayName: String(row?.displayName || '').trim().slice(0, 48),
    authDir: resolve(authDir),
    role: row?.role === 'owner' ? 'owner' : 'linked',
    createdAt: Number(row?.createdAt) || Date.now(),
  }
}

export class AccountRegistry {
  constructor({ file, authRoot, maxAccounts = 2, legacy = [] }) {
    this.file = resolve(file)
    this.authRoot = resolve(authRoot)
    this.maxAccounts = Math.max(1, Math.min(50, Number(maxAccounts) || 2))
    this.legacy = legacy
    this.records = new Map()
    this.nextSequence = 1
  }

  list() {
    return [...this.records.values()].map(row => ({ ...row }))
  }

  resolveId(value) {
    const raw = String(value || '').trim()
    if (this.records.has(raw)) return raw
    const lower = raw.toLowerCase()
    for (const id of this.records.keys()) if (id.toLowerCase() === lower) return id
    return ''
  }

  get(value) {
    const id = this.resolveId(value)
    return id ? { ...this.records.get(id) } : null
  }

  entitlements() {
    return {
      count: this.records.size,
      maxAccounts: this.maxAccounts,
      canAddAccount: this.records.size < this.maxAccounts,
    }
  }

  async load() {
    let raw = null
    try {
      raw = JSON.parse(await readFile(this.file, 'utf8'))
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error
    }

    this.records.clear()
    for (const row of raw?.accounts || []) {
      const record = normalizeRecord(row)
      if (record && !this.records.has(record.id)) this.records.set(record.id, record)
    }

    if (!this.records.size) {
      for (const row of this.legacy) {
        const record = normalizeRecord(row)
        if (record && !this.records.has(record.id)) this.records.set(record.id, record)
      }
      this.nextSequence = this.records.size + 1
      if (this.records.size) await this.save()
    } else {
      this.nextSequence = Math.max(
        Number(raw?.nextSequence) || 1,
        this.records.size + 1,
      )
    }
    return this.list()
  }

  async save() {
    await mkdir(dirname(this.file), { recursive: true })
    const tmp = this.file + '.tmp'
    await writeFile(tmp, JSON.stringify({
      version: 1,
      savedAt: Date.now(),
      nextSequence: this.nextSequence,
      accounts: this.list(),
    }, null, 2))
    await rename(tmp, this.file)
  }

  async create({ phoneNumber, displayName = '' }) {
    if (this.records.size >= this.maxAccounts) {
      throw new Error(`Account limit reached (${this.records.size}/${this.maxAccounts})`)
    }
    const number = digits(phoneNumber)
    if (!PHONE_RE.test(number)) throw new Error('Phone number must contain 7 to 15 digits')
    if ([...this.records.values()].some(row => row.phoneNumber === number)) {
      throw new Error('That WhatsApp number is already registered')
    }

    let id
    do {
      id = `account-${this.nextSequence++}`
    } while (this.records.has(id))

    const record = {
      id,
      phoneNumber: number,
      displayName: String(displayName || '').trim().slice(0, 48),
      authDir: join(this.authRoot, id),
      role: 'linked',
      createdAt: Date.now(),
    }
    this.records.set(id, record)
    await mkdir(record.authDir, { recursive: true })
    await this.save()
    return { ...record }
  }

  async rename(value, displayName) {
    const id = this.resolveId(value)
    if (!id) throw new Error('Unknown account')
    const record = this.records.get(id)
    record.displayName = String(displayName || '').trim().slice(0, 48)
    await this.save()
    return { ...record }
  }

  async remove(value) {
    const id = this.resolveId(value)
    if (!id) throw new Error('Unknown account')
    const record = this.records.get(id)
    this.records.delete(id)
    await this.save()
    return { ...record, authPreserved: true }
  }
}

export function legacyAccountRecords({
  accountA,
  accountB,
  authA = '/var/lib/mscc/auth',
  authB = '/var/lib/mscc/auth-b',
}) {
  const rows = []
  if (PHONE_RE.test(digits(accountA))) {
    rows.push({
      id: 'A',
      phoneNumber: digits(accountA),
      displayName: 'Main',
      authDir: authA,
      role: 'owner',
    })
  }
  if (PHONE_RE.test(digits(accountB))) {
    rows.push({
      id: 'B',
      phoneNumber: digits(accountB),
      displayName: 'Second',
      authDir: authB,
      role: 'linked',
    })
  }
  return rows
}
