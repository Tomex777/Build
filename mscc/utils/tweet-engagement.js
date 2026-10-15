import { randomInt } from 'node:crypto'

// Tweet cards are fictional mockups, not a live X/Twitter API feed.
// Each command invocation gets fresh numbers; replies and reposts stay
// proportional to likes instead of appearing as unrelated giant totals.
export function randomTweetEngagement(random = randomInt) {
  const popularity = random(100)
  const [minimum, maximum] = popularity < 40
    ? [24, 550]
    : popularity < 85
      ? [551, 9900]
      : popularity < 98
        ? [9901, 105000]
        : [105001, 800000]

  const likeCount = random(minimum, maximum + 1)
  const replyCount = Math.max(1, Math.round(likeCount * random(15, 80) / 1000))
  const repostCount = Math.max(1, Math.round(likeCount * random(25, 130) / 1000))
  return { likeCount, replyCount, repostCount }
}

// Social post layouts use the same weighted popularity curve as mock tweets,
// but expose the labels appropriate to Instagram, Facebook, stories and Night.
export function randomPostEngagement(random = randomInt) {
  const { likeCount, replyCount, repostCount } = randomTweetEngagement(random)
  const viewCount = Math.max(likeCount + 1, likeCount * random(3, 16))
  return {
    likeCount,
    commentCount: replyCount,
    shareCount: repostCount,
    viewCount,
  }
}


// User-supplied numbers supersede fictional random metrics. Other metrics
// remain randomized, and follow manually chosen likes when supplied.
export function resolveTweetEngagement(overrides = {}, random = randomInt) {
  const generated = randomTweetEngagement(random)
  const likeCount = overrides.likeCount ?? generated.likeCount
  return {
    likeCount,
    replyCount:overrides.replyCount ?? (overrides.likeCount === undefined
      ? generated.replyCount
      : (likeCount === 0 ? 0 : Math.max(1, Math.round(likeCount * random(15, 80) / 1000)))),
    repostCount:overrides.repostCount ?? (overrides.likeCount === undefined
      ? generated.repostCount
      : (likeCount === 0 ? 0 : Math.max(1, Math.round(likeCount * random(25, 130) / 1000)))),
  }
}

export function resolvePostEngagement(overrides = {}, random = randomInt) {
  const generated = randomPostEngagement(random)
  const likeCount = overrides.likeCount ?? generated.likeCount
  return {
    likeCount,
    commentCount:overrides.commentCount ?? (overrides.likeCount === undefined
      ? generated.commentCount
      : (likeCount === 0 ? 0 : Math.max(1, Math.round(likeCount * random(15, 80) / 1000)))),
    shareCount:overrides.shareCount ?? (overrides.likeCount === undefined
      ? generated.shareCount
      : (likeCount === 0 ? 0 : Math.max(1, Math.round(likeCount * random(25, 130) / 1000)))),
    viewCount:overrides.viewCount ?? (overrides.likeCount === undefined
      ? generated.viewCount
      : Math.max(likeCount + 1, likeCount * random(3, 16))),
  }
}
