# NIGHT Play: Wave 2 large-scale quality campaign — 20 October 2026

**NOT a restart.** The first-wave ten-agent candidate was merged through [PR #436](https://github.com/Tomex777/Build/pull/436). All agents must start from the latest merged `feature/party-online-rooms-20261019`, inspect current game source and avoid recreating existing games. Long-session mobile touch quality, server-verified multiplayer and exceptional visuals are now more important than adding numerous low-quality titles.

**User launch:** In each of the ten NIGHT Play GitHub-connected chats say `You are NIGHT Play Agent 1`, `You are NIGHT Play Agent 2`, ... `You are NIGHT Play Agent 10`. If an existing agent chat is open, say `Continue NIGHT Play Wave 2: read your updated agent-NN.md on feature/party-online-rooms-20261019 and GitHub issue #459; implement the next unblocked task.` The chat must actually fetch these GitHub files; simply storing them doesn't inject them into other chats.

## Workflow
1. Read `night-play/AGENTS.md`, `night-play/agents/agent-NN.md`, `night-play/DESIGN.md`, `docs/agent-backlog.md`, [issue #459](https://github.com/Tomex777/Build/issues/459) latest comments and current integrated source.
2. Post `[PLAY A#][CLAIM]` with exact files/branch/API and real user-visible problem. If others own shared files, ask Agent 1 to acknowledge an exclusive edit window or extract module.
3. Work in `agent/play-v2-NN-topic` from current **integration**, open small reviewable PR targeting `feature/party-online-rooms-20261019` (not `main`).
4. Reproduce → fix → run actual **Chromium touch gameplay** + screenshot → review visuals → fix again → request independent A10 QA → Agent 1 merges frequently.
5. Report full status with PR/commit, integrated SHA, real device/browser/viewports, win/loss/restart, difficulty, effects, bug IDs, blockers and next executable action; continue while session permits.
6. Do not claim the system is automatically nonstop or that other chats have received messages until their acknowledgement is visible.

### Shared live coordination

GitHub issue **[#459](https://github.com/Tomex777/Build/issues/459)** is the collaboration message bus. Post at initial claim, before interface changes, when blocked/ready, after integration or QA. Use GitHub PR diff discussion for code comments, issue #459 for dependencies. Do not spam check-ins or ask other agents to guess who owns a file.

### Assigned work

| Agent | Focus | Detailed brief |
|---|---|---|
| 1 | Coordinator and release integrity | [agent-01.md](agent-01.md) |
| 2 | Fruit Slicing + Snake gesture reliability | [agent-02.md](agent-02.md) |
| 3 | Runners, Sky Jumper and platform challenge | [agent-03.md](agent-03.md) |
| 4 | Brick Breaker, Shooter and Wing Hop depth | [agent-04.md](agent-04.md) |
| 5 | Jigsaw cooperative-ready puzzles and 2048 | [agent-05.md](agent-05.md) |
| 6 | WHOT, Chess and Checkers: credible opponents | [agent-06.md](agent-06.md) |
| 7 | Piano Tiles, Maze Chase and catalogue quality | [agent-07.md](agent-07.md) |
| 8 | Real multiplayer, identity and co-op Jigsaw | [agent-08.md](agent-08.md) |
| 9 | Product-wide visual overhaul and accessibility | [agent-09.md](agent-09.md) |
| 10 | Independent full-catalog long-session gameplay QA | [agent-10.md](agent-10.md) |

**Definition of done:** one cohesive site with properly playable games, smooth real-device touch and clear game-over/restart, meaningful difficulty, accurate real-online multiplayer and authoritative profile identity, no duplicate controls/art placeholders, latest-SHA verified staging deployment and QA. Green CI is necessary but insufficient. No unapproved production website or MSCC protected backend promotion.
