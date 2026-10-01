import { josiahCalc, josiahCalcError, josiahUsage } from '../../response-pools.js'

const TOKEN = /\s*(?:(\d+(?:\.\d+)?|\.\d+)|([()+\-*/%^]))/gy

function tokenize(input) {
  const text = String(input || '').trim()
  if (!text) throw new Error('empty')
  const tokens = []
  let index = 0
  while (index < text.length) {
    TOKEN.lastIndex = index
    const match = TOKEN.exec(text)
    if (!match || match.index !== index) throw new Error('invalid')
    tokens.push(match[1] ? { type:'number', value:Number(match[1]) } : { type:match[2], value:match[2] })
    index = TOKEN.lastIndex
  }
  return tokens
}

function evaluate(input) {
  const tokens = tokenize(input)
  let index = 0
  const peek = type => tokens[index]?.type === type
  const take = type => {
    if (!peek(type)) throw new Error('invalid')
    return tokens[index++]
  }

  function primary() {
    if (peek('number')) return take('number').value
    if (peek('(')) {
      take('(')
      const value = expression()
      take(')')
      return value
    }
    throw new Error('invalid')
  }

  function unary() {
    if (peek('+')) { take('+'); return unary() }
    if (peek('-')) { take('-'); return -unary() }
    return primary()
  }

  function power() {
    let left = unary()
    if (peek('^')) {
      take('^')
      left = left ** power()
    }
    return left
  }

  function term() {
    let left = power()
    while (peek('*') || peek('/') || peek('%')) {
      const op = tokens[index++].type
      const right = power()
      if ((op === '/' || op === '%') && right === 0) throw new Error('zero')
      left = op === '*' ? left * right : op === '/' ? left / right : left % right
    }
    return left
  }

  function expression() {
    let left = term()
    while (peek('+') || peek('-')) {
      const op = tokens[index++].type
      const right = term()
      left = op === '+' ? left + right : left - right
    }
    return left
  }

  const result = expression()
  if (index !== tokens.length || !Number.isFinite(result)) throw new Error('invalid')
  return result
}

function render(value) {
  if (Number.isInteger(value)) return String(value)
  return Number(value.toPrecision(12)).toString()
}

export default {
  name: 'calc',
  aliases: ['calculate'],
  description: 'Calculate a mathematical expression.',
  usage: '.calc <expression>',
  help: 'Supports +, -, *, /, %, ^ and parentheses.',
  async run(ctx) {
    const expression = ctx.args.join(' ').trim()
    if (!expression) return ctx.reply(josiahUsage(`${ctx.publicPrefix || '.'}calc <expression>`))
    try {
      const result = render(evaluate(expression))
      return ctx.reply(josiahCalc(expression, result))
    } catch (error) {
      return ctx.reply(josiahCalcError(error?.message === 'zero' ? 'zero' : 'invalid'))
    }
  },
}
