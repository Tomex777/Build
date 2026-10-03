import { weatherFor, weatherLabel } from '../../utility-services.js'
export default {
  name:'weather',
  description:'Show current weather and a short forecast for a place.',
  usage:'.weather <place>',
  async run(ctx) {
    const place = ctx.args.join(' ').trim()
    if (!place) return ctx.reply('Use .weather <place>.')
    try {
      const data = await weatherFor(place)
      const c = data.current || {}
      const d = data.daily || {}
      const lines = [
        '🌤️ *' + data.place + '*',
        weatherLabel(c.weather_code) + ' · ' + Number(c.temperature_2m).toFixed(0) + '°C',
        'Feels like ' + Number(c.apparent_temperature).toFixed(0) + '°C · Wind ' + Number(c.wind_speed_10m).toFixed(0) + ' km/h',
        '',
        '*Next days*',
      ]
      for (let i=0;i<Math.min(3, d.time?.length || 0);i+=1) {
        lines.push(String(d.time[i]) + ' · ' + weatherLabel(d.weather_code?.[i]) + ' · ' +
          Number(d.temperature_2m_min?.[i]).toFixed(0) + '–' + Number(d.temperature_2m_max?.[i]).toFixed(0) + '°C · Rain ' +
          Number(d.precipitation_probability_max?.[i] || 0).toFixed(0) + '%')
      }
      return ctx.reply(lines.join('\n'))
    } catch (error) { return ctx.reply(error?.message || 'Weather lookup failed.') }
  },
}
