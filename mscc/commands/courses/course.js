import { runCourseCommand } from '../../course-flow.js'

export default {
  name: 'course',
  aliases: ['courses'],
  description: 'Search courses, choose a course, then reply with the part number(s) to download.',
  usage: '.course <topic>',
  async run(ctx) {
    return runCourseCommand(ctx, { args:ctx.args })
  },
}
