export const sleep = ms =>
  new Promise(resolve => setTimeout(resolve, Math.max(0, Number(ms) || 0)))

export async function withTimeout(promise, ms, message = 'Operation timed out') {
  const timeoutMs = Math.max(1, Number(ms) || 1)
  let timer

  try {
    return await Promise.race([
      Promise.resolve(promise),
      new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error(message)), timeoutMs)
      }),
    ])
  } finally {
    clearTimeout(timer)
  }
}

export function createLimiter(concurrency = 2) {
  const limit = Math.max(1, Number(concurrency) || 1)
  const queue = []
  let active = 0

  const drain = () => {
    while (active < limit && queue.length) {
      const item = queue.shift()
      active += 1

      Promise.resolve()
        .then(item.task)
        .then(item.resolve, item.reject)
        .finally(() => {
          active -= 1
          drain()
        })
    }
  }

  const run = task => new Promise((resolve, reject) => {
    queue.push({ task, resolve, reject })
    drain()
  })

  return {
    run,
    get active() {
      return active
    },
    get pending() {
      return queue.length
    },
  }
}
