package com.tomex777.annie

internal object StarterScripts {
    internal fun migrateChess(source: String): String {
        var migrated = source
        if ("capabilities:" !in migrated) {
            migrated = migrated.replace(
                """annie.commands.register({
  name: "chess",
  description: "Play local chess with Annie",
  usage: "/chess new",
  async execute(ctx) {""",
                """annie.commands.register({
  name: "chess",
  description: "Play local chess with Annie",
  usage: "/chess new",
  keywords: ["game", "board", "move", "hint", "resign"],
  capabilities: ["game", "chess", "move", "board", "hint", "resign"],
  suggestions: [
    { label: "Show board", input: "board" },
    { label: "Hint", input: "hint" },
    { label: "Resign", input: "resign" }
  ],
  async execute(ctx) {""",
            )
        }
        if ("action === \"board\"" !in migrated) {
            migrated = migrated.replace(
                """    if (String(ctx.text).trim().toLowerCase() === "resign") {
      ctx.session.end();
      await annie.storage.set("game:" + ctx.chatId, null);
      return { type: "text", text: "Game ended. Use /chess new whenever you want another one." };
    }
    const user = parseMove(state, ctx.text);""",
                """    const action = String(ctx.text).trim().toLowerCase();
    if (action === "board") {
      return sendBoard(ctx, state, state.turn === "w" ? "Your move." : "Black to move.");
    }
    if (action === "hint") {
      const ideas = pseudoMoves(state).slice(0, 4).map(moveLabel);
      return { type: "text", text: ideas.length ? "Try " + ideas.join(", ") + "." : "No moves are available." };
    }
    if (action === "resign") {
      ctx.session.end();
      await annie.storage.set("game:" + ctx.chatId, null);
      return { type: "text", text: "Game ended. Use /chess new whenever you want another one." };
    }
    const user = parseMove(state, ctx.text);""",
            )
        }
        return migrated
    }

    val chess: String = """
        |const START = [
        |  "rnbqkbnr",
        |  "pppppppp",
        |  "........",
        |  "........",
        |  "........",
        |  "........",
        |  "PPPPPPPP",
        |  "RNBQKBNR"
        |];
        |
        |const files = "abcdefgh";
        |const cloneBoard = board => board.map(row => row.slice());
        |const startState = () => ({ board: START.map(row => row.split("")), turn: "w", ply: 0 });
        |const isWhite = piece => piece && piece === piece.toUpperCase();
        |const sideOf = piece => !piece || piece === "." ? null : (isWhite(piece) ? "w" : "b");
        |const inBounds = (r, c) => r >= 0 && r < 8 && c >= 0 && c < 8;
        |const sq = (r, c) => files[c] + String(8 - r);
        |const rc = square => [8 - Number(square[1]), files.indexOf(square[0])];
        |
        |function pathClear(board, r, c, tr, tc) {
        |  const dr = Math.sign(tr - r), dc = Math.sign(tc - c);
        |  let rr = r + dr, cc = c + dc;
        |  while (rr !== tr || cc !== tc) {
        |    if (board[rr][cc] !== ".") return false;
        |    rr += dr; cc += dc;
        |  }
        |  return true;
        |}
        |
        |function pseudoMoves(state) {
        |  const board = state.board, side = state.turn, out = [];
        |  for (let r = 0; r < 8; r++) for (let c = 0; c < 8; c++) {
        |    const piece = board[r][c];
        |    if (sideOf(piece) !== side) continue;
        |    const p = piece.toUpperCase();
        |    const add = (tr, tc, promotion = null) => {
        |      if (!inBounds(tr, tc)) return;
        |      const target = board[tr][tc];
        |      if (sideOf(target) === side) return;
        |      out.push({ from: sq(r,c), to: sq(tr,tc), piece: p, capture: target !== ".", promotion });
        |    };
        |    if (p === "P") {
        |      const dir = side === "w" ? -1 : 1;
        |      const start = side === "w" ? 6 : 1;
        |      const promote = side === "w" ? 0 : 7;
        |      if (inBounds(r + dir, c) && board[r + dir][c] === ".") {
        |        add(r + dir, c, r + dir === promote ? "Q" : null);
        |        if (r === start && board[r + 2 * dir][c] === ".") add(r + 2 * dir, c);
        |      }
        |      for (const dc of [-1, 1]) {
        |        const tr = r + dir, tc = c + dc;
        |        if (inBounds(tr, tc) && board[tr][tc] !== "." && sideOf(board[tr][tc]) !== side) {
        |          add(tr, tc, tr === promote ? "Q" : null);
        |        }
        |      }
        |    } else if (p === "N") {
        |      for (const [dr,dc] of [[-2,-1],[-2,1],[-1,-2],[-1,2],[1,-2],[1,2],[2,-1],[2,1]]) add(r+dr,c+dc);
        |    } else if (p === "K") {
        |      for (const dr of [-1,0,1]) for (const dc of [-1,0,1]) if (dr || dc) add(r+dr,c+dc);
        |    } else {
        |      const dirs = p === "B" ? [[-1,-1],[-1,1],[1,-1],[1,1]]
        |        : p === "R" ? [[-1,0],[1,0],[0,-1],[0,1]]
        |        : [[-1,-1],[-1,1],[1,-1],[1,1],[-1,0],[1,0],[0,-1],[0,1]];
        |      for (const [dr,dc] of dirs) {
        |        let tr=r+dr, tc=c+dc;
        |        while (inBounds(tr,tc)) {
        |          const target = board[tr][tc];
        |          if (sideOf(target) === side) break;
        |          out.push({from:sq(r,c),to:sq(tr,tc),piece:p,capture:target!==".",promotion:null});
        |          if (target !== ".") break;
        |          tr += dr; tc += dc;
        |        }
        |      }
        |    }
        |  }
        |  return out;
        |}
        |
        |function parseMove(state, raw) {
        |  const text = String(raw || "").trim().replace(/[+#?!]+$/g, "");
        |  const moves = pseudoMoves(state);
        |  let m = text.match(/^([a-h][1-8])([a-h][1-8])([qrbnQRBN])?$/);
        |  if (m) {
        |    const exact = moves.filter(x => x.from === m[1] && x.to === m[2]);
        |    return exact.length === 1 ? exact[0] : null;
        |  }
        |  m = text.match(/^([KQRBN])?([a-h1-8]?)(x)?([a-h][1-8])(?:=([QRBN]))?$/);
        |  if (!m) return null;
        |  const piece = m[1] || "P", hint = m[2] || "", dest = m[4];
        |  const candidates = moves.filter(x => {
        |    if (x.piece !== piece || x.to !== dest) return false;
        |    if (m[3] && !x.capture) return false;
        |    if (!hint) return true;
        |    return x.from[0] === hint || x.from[1] === hint;
        |  });
        |  return candidates.length === 1 ? candidates[0] : null;
        |}
        |
        |function applyMove(state, move) {
        |  const next = { board: cloneBoard(state.board), turn: state.turn === "w" ? "b" : "w", ply: state.ply + 1 };
        |  const [fr,fc] = rc(move.from), [tr,tc] = rc(move.to);
        |  let piece = next.board[fr][fc];
        |  next.board[fr][fc] = ".";
        |  if (move.promotion) piece = state.turn === "w" ? move.promotion : move.promotion.toLowerCase();
        |  next.board[tr][tc] = piece;
        |  return next;
        |}
        |
        |function moveLabel(move) {
        |  if (move.piece === "P") return (move.capture ? move.from[0] + "x" : "") + move.to + (move.promotion ? "=" + move.promotion : "");
        |  return move.piece + (move.capture ? "x" : "") + move.to;
        |}
        |
        |function botMove(state) {
        |  const moves = pseudoMoves(state);
        |  const preferences = ["c7c5","e7e5","d7d5","g8f6","b8c6","c8g4","f8b4"];
        |  for (const key of preferences) {
        |    const found = moves.find(x => x.from + x.to === key);
        |    if (found) return found;
        |  }
        |  return moves[0] || null;
        |}
        |
        |function fen(state) {
        |  const rows = state.board.map(row => {
        |    let out = "", empty = 0;
        |    for (const p of row) {
        |      if (p === ".") empty++;
        |      else { if (empty) { out += empty; empty = 0; } out += p; }
        |    }
        |    if (empty) out += empty;
        |    return out;
        |  });
        |  return rows.join("/") + " " + state.turn + " - - 0 1";
        |}
        |
        |async function sendBoard(ctx, state, caption) {
        |  await annie.storage.set("game:" + ctx.chatId, state);
        |  return { type: "image", uri: annie.image.chess(fen(state)), caption };
        |}
        |
        |annie.sessions.register({
        |  name: "chess-game",
        |  async onMessage(ctx) {
        |    let state = await annie.storage.get("game:" + ctx.chatId);
        |    if (!state) {
        |      ctx.session.end();
        |      return { type: "text", text: "There is no active chess game. Use /chess new." };
        |    }
        |    const action = String(ctx.text).trim().toLowerCase();
        |    if (action === "board") {
        |      return sendBoard(ctx, state, state.turn === "w" ? "Your move." : "Black to move.");
        |    }
        |    if (action === "hint") {
        |      const ideas = pseudoMoves(state).slice(0, 4).map(moveLabel);
        |      return { type: "text", text: ideas.length ? "Try " + ideas.join(", ") + "." : "No moves are available." };
        |    }
        |    if (action === "resign") {
        |      ctx.session.end();
        |      await annie.storage.set("game:" + ctx.chatId, null);
        |      return { type: "text", text: "Game ended. Use /chess new whenever you want another one." };
        |    }
        |    const user = parseMove(state, ctx.text);
        |    if (!user) return { type: "text", text: "I couldn't apply that move. Try algebraic notation like e4, Nf3, Bc4, or e2e4." };
        |    state = applyMove(state, user);
        |    const reply = botMove(state);
        |    if (!reply) {
        |      ctx.session.end();
        |      return sendBoard(ctx, state, "No reply is available. Game ended.");
        |    }
        |    state = applyMove(state, reply);
        |    return sendBoard(ctx, state, "Black played " + moveLabel(reply) + ". Your move.");
        |  }
        |});
        |
        |annie.commands.register({
        |  name: "chess",
        |  description: "Play local chess with Annie",
        |  usage: "/chess new",
        |  keywords: ["game", "board", "move", "hint", "resign"],
        |  capabilities: ["game", "chess", "move", "board", "hint", "resign"],
        |  suggestions: [
        |    { label: "Show board", input: "board" },
        |    { label: "Hint", input: "hint" },
        |    { label: "Resign", input: "resign" }
        |  ],
        |  async execute(ctx) {
        |    const arg = String(ctx.text || "").trim().toLowerCase();
        |    if (arg && arg !== "new") return { type: "text", text: "Use /chess new to begin a local game." };
        |    const state = startState();
        |    ctx.session.start("chess-game");
        |    return sendBoard(ctx, state, "Your move. Try e4, d4, Nf3, or c4.");
        |  }
        |});
    """.trimMargin()

    /** Built-in playable game demonstrating HTML/CSS/JS Canvas as a chat message. */
    val canvasSnake: String = """
        |annie.commands.register({
        |  name: "snake",
        |  description: "Play Snake directly inside an Annie chat",
        |  usage: "/snake",
        |  keywords: ["game", "canvas", "play", "snake"],
        |  async execute() {
        |    return {
        |      type: "canvas",
        |      title: "Snake • Canvas",
        |      height: 440,
        |      html: `<main class="game">
        |        <header><strong>🐍 Snake</strong><span id="score">Score 0</span></header>
        |        <canvas id="board" width="300" height="300" aria-label="Snake board"></canvas>
        |        <div class="pad">
        |          <span></span><button data-dir="up" aria-label="Up">▲</button><span></span>
        |          <button data-dir="left" aria-label="Left">◀</button>
        |          <button id="restart">↻</button>
        |          <button data-dir="right" aria-label="Right">▶</button>
        |          <span></span><button data-dir="down" aria-label="Down">▼</button><span></span>
        |        </div>
        |        <p id="hint">Tap arrows or use your keyboard</p>
        |      </main>`,
        |      css: `
        |        body { background:#071622; margin:0; color:#eef5ff; }
        |        .game { width:100%; max-width:320px; padding:8px 10px; margin:auto; text-align:center; }
        |        header { display:flex; justify-content:space-between; align-items:center; margin-bottom:6px; color:#54d6ae; }
        |        #board { display:block; background:#0c2230; width:min(100%,300px); aspect-ratio:1; border-radius:12px; touch-action:none; }
        |        .pad { display:grid; grid-template-columns:repeat(3,42px); justify-content:center; gap:3px; margin-top:8px; }
        |        button { background:#244256; color:white; border:0; border-radius:9px; height:32px; touch-action:manipulation; }
        |        button:active { background:#168eea; }
        |        #restart { background:#168eea; }
        |        #hint { color:#9cb2cc; font-size:11px; margin:6px 0 0; }
        |      `,
        |      javascript: `
        |        (() => {
        |          const board = document.getElementById('board');
        |          const ctx = board.getContext('2d');
        |          const score = document.getElementById('score');
        |          const hint = document.getElementById('hint');
        |          const unit = 15, cells = 20;
        |          let snake, direction, nextDirection, food, points, lost;
        |          const rand = () => Math.floor(Math.random() * cells);
        |          function placeFood() {
        |            do { food = {x: rand(), y: rand()}; }
        |            while (snake.some(p => p.x === food.x && p.y === food.y));
        |          }
        |          function draw() {
        |            ctx.fillStyle = '#0c2230'; ctx.fillRect(0, 0, 300, 300);
        |            ctx.fillStyle = '#e78e8e';
        |            ctx.fillRect(food.x * unit + 2, food.y * unit + 2, unit - 4, unit - 4);
        |            snake.forEach((p, i) => {
        |              ctx.fillStyle = i ? '#36bca0' : '#91f2cb';
        |              ctx.fillRect(p.x * unit + 1, p.y * unit + 1, unit - 2, unit - 2);
        |            });
        |          }
        |          function reset() {
        |            snake = [{x:10,y:10},{x:9,y:10},{x:8,y:10}];
        |            direction = {x:1,y:0}; nextDirection = direction;
        |            points = 0; lost = false; score.textContent = 'Score 0';
        |            hint.textContent = 'Tap arrows or use your keyboard';
        |            placeFood(); draw();
        |          }
        |          function turn(x,y) {
        |            if (direction.x + x === 0 && direction.y + y === 0) return;
        |            nextDirection = {x,y};
        |          }
        |          function tick() {
        |            if (lost) return;
        |            direction = nextDirection;
        |            const head = {x:snake[0].x+direction.x,y:snake[0].y+direction.y};
        |            const eat = head.x === food.x && head.y === food.y;
        |            const body = eat ? snake : snake.slice(0,-1);
        |            if (head.x<0 || head.y<0 || head.x>=cells || head.y>=cells ||
        |              body.some(p=>p.x===head.x && p.y===head.y)) {
        |              lost=true; hint.textContent='Game over — tap ↻ to restart'; return;
        |            }
        |            snake.unshift(head);
        |            if (eat) { points++; score.textContent='Score '+points; placeFood(); }
        |            else snake.pop();
        |            draw();
        |          }
        |          const directions = {up:[0,-1],down:[0,1],left:[-1,0],right:[1,0]};
        |          document.querySelectorAll('[data-dir]').forEach(button => {
        |            button.addEventListener('click', () => {
        |              const dir = directions[button.dataset.dir]; turn(dir[0],dir[1]);
        |            });
        |          });
        |          document.addEventListener('keydown', event => {
        |            const name = event.key.replace('Arrow','').toLowerCase();
        |            if (directions[name]) {
        |              event.preventDefault(); const dir = directions[name]; turn(dir[0],dir[1]);
        |            }
        |          });
        |          document.getElementById('restart').addEventListener('click', reset);
        |          reset(); setInterval(tick, 160);
        |          window.annieCanvasSnakeReady = true;
        |        })();
        |      `
        |    };
        |  }
        |});
    """.trimMargin()
}
