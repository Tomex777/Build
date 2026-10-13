import { AsyncLocalStorage } from 'node:async_hooks'

// A source adapter may call shared HTTP helpers without explicitly forwarding
// context.signal. Preserve the registry's lookup deadline across async calls,
// without global mutable signal state leaking between WhatsApp accounts.
const activeSource = new AsyncLocalStorage()

export function withSourceDeadline(signal, fn) {
  return activeSource.run({ signal:signal || null }, fn)
}

export function currentSourceSignal() {
  return activeSource.getStore()?.signal || null
}
