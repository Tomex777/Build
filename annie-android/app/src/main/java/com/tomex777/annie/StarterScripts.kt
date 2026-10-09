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

    /** Built-in gesture-first arcade game. No arrow buttons; swipe anywhere on the playing surface. */
    val canvasSnake: String = """
        |annie.commands.register({
        |  name: "snake",
        |  description: "Play the swipe-first Canvas Arcade Snake game",
        |  usage: "/snake",
        |  keywords: ["game", "arcade", "canvas", "swipe", "snake"],
        |  async execute() {
        |    return {
        |      type: "canvas",
        |      title: "Snake • Arcade",
        |      height: 394,
        |      html: `<main class="game" id="game" role="application" aria-label="Swipe anywhere on the board to steer Snake" tabindex="0">
        |        <header><div class="identity"><span class="spark">✦</span><span>ARCADE <small>/ SNAKE</small></span></div><span id="score">00</span></header>
        |        <section class="stage"><canvas id="board" width="300" height="300" aria-label="Swipe to steer the snake"></canvas>
        |        <div class="hint" id="hint">SWIPE TO PLAY<span>← ↑ → ↓</span></div></section>
        |        <footer><span id="status">Swipe anywhere to start</span><span>BEST <b id="best">00</b></span></footer>
        |      </main>`,
        |      css: `
        |        html, body { margin:0; padding:0; overflow:hidden; background:#090f19; color:#e9f6ff; }
        |        .game { padding:9px 10px 5px; max-width:360px; margin:auto; user-select:none; -webkit-user-select:none; touch-action:none; outline:none; }
        |        header { display:flex; justify-content:space-between; align-items:center; min-height:26px; margin-bottom:8px; }
        |        .identity { display:flex; align-items:center; gap:7px; font-size:11px; font-weight:800; letter-spacing:2px; color:#a2f4dc; }
        |        .identity small { color:#667b8d; font-weight:600; font-size:10px; letter-spacing:1px; }
        |        .spark { font-size:17px; color:#65e3d1; }
        |        #score { border-radius:13px; color:#caffea; background:#163b39; padding:5px 12px; font-size:15px; font-weight:800; font-variant-numeric:tabular-nums; }
        |        .stage { position:relative; display:flex; justify-content:center; align-items:center; overflow:hidden; border-radius:18px;
        |          background:radial-gradient(circle at 50% 35%,#183344 0%,#101e2d 52%,#0b1725 100%);
        |          border:1px solid #233e4d; box-shadow:inset 0 0 28px #0a1523; }
        |        #board { display:block; width:min(100%,300px); aspect-ratio:1; height:auto; touch-action:none; }
        |        .hint { pointer-events:none; position:absolute; left:0; right:0; bottom:22px; text-align:center;
        |          font-size:11px; font-weight:800; color:#b0ffe5; letter-spacing:2px; text-shadow:0 2px 10px #04090f; }
        |        .hint span { display:block; font-size:10px; letter-spacing:7px; color:#80b0b9; margin-top:6px; }
        |        .hint.hidden { opacity:0; }
        |        footer { display:flex; align-items:center; justify-content:space-between; padding:9px 2px 0; min-height:16px;
        |          font-size:10px; letter-spacing:.4px; color:#86a3b4; }
        |        footer b { color:#a2f4dc; }
        |      `,
        |      javascript: `
        |        (() => {
        |          const surface = document.getElementById('game');
        |          const board = document.getElementById('board');
        |          const ctx = board.getContext('2d');
        |          const score = document.getElementById('score');
        |          const best = document.getElementById('best');
        |          const hint = document.getElementById('hint');
        |          const status = document.getElementById('status');
        |          const cells = 20, unit = 15, interval = 145;
        |          const vectors = { up:[0,-1], right:[1,0], down:[0,1], left:[-1,0] };
        |          const rand = () => Math.floor(Math.random() * cells);
        |          const cell = (x,y) => ({x,y});
        |          let snake, previous, food, direction, pending, points, record = 0;
        |          let started = false, lost = false, moves = 0, accumulator = 0, lastFrame = 0;
        |          function placeFood() {
        |            if (snake.length === cells*cells) { lost = true; status.textContent = 'Board cleared! Tap to replay'; return; }
        |            do { food = cell(rand(), rand()); }
        |            while (snake.some(p => p.x === food.x && p.y === food.y));
        |          }
        |          function reset() {
        |            snake = [cell(10,10),cell(9,10),cell(8,10)];
        |            previous = snake.map(p => cell(p.x,p.y));
        |            direction = 'right'; pending = 'right'; food = cell(5,5);
        |            points = 0; moves = 0; accumulator = 0; lastFrame = 0;
        |            started = false; lost = false;
        |            score.textContent = '00'; best.textContent = String(record).padStart(2,'0');
        |            status.textContent = 'Swipe anywhere to start';
        |            hint.innerHTML = 'SWIPE TO PLAY<span>← ↑ → ↓</span>';
        |            hint.classList.remove('hidden');
        |            placeFood();
        |            draw(1);
        |          }
        |          function play() {
        |            if (lost) reset();
        |            started = true;
        |            hint.classList.add('hidden');
        |            status.textContent = 'Swipe to change direction';
        |          }
        |          function turn(name) {
        |            if (!vectors[name]) return;
        |            if (vectors[name][0] + vectors[direction][0] === 0 &&
        |                vectors[name][1] + vectors[direction][1] === 0) return;
        |            pending = name;
        |            play();
        |          }
        |          function tick() {
        |            direction = pending;
        |            previous = snake.map(p => cell(p.x,p.y));
        |            const vector = vectors[direction];
        |            // Match the arcade's seamless edges: the snake wraps around the board.
        |            const head = cell((snake[0].x+vector[0]+cells)%cells,(snake[0].y+vector[1]+cells)%cells);
        |            const eat = head.x === food.x && head.y === food.y;
        |            const body = eat ? snake : snake.slice(0,-1);
        |            if (body.some(p => p.x === head.x && p.y === head.y)) {
        |              lost = true; status.textContent = 'Game over · tap the board to replay';
        |              hint.innerHTML = 'GAME OVER<span>TAP TO REPLAY</span>';
        |              hint.classList.remove('hidden');
        |              return;
        |            }
        |            snake.unshift(head);
        |            if (eat) {
        |              points++;
        |              record = Math.max(record, points);
        |              score.textContent = String(points).padStart(2,'0');
        |              best.textContent = String(record).padStart(2,'0');
        |              placeFood();
        |            } else snake.pop();
        |            moves++;
        |          }
        |          function tile(x,y,size,r) {
        |            ctx.beginPath();
        |            ctx.moveTo(x+r,y);ctx.lineTo(x+size-r,y);ctx.quadraticCurveTo(x+size,y,x+size,y+r);
        |            ctx.lineTo(x+size,y+size-r);ctx.quadraticCurveTo(x+size,y+size,x+size-r,y+size);
        |            ctx.lineTo(x+r,y+size);ctx.quadraticCurveTo(x,y+size,x,y+size-r);
        |            ctx.lineTo(x,y+r);ctx.quadraticCurveTo(x,y,x+r,y);ctx.closePath();ctx.fill();
        |          }
        |          function draw(alpha) {
        |            ctx.clearRect(0,0,300,300);
        |            ctx.fillStyle = 'rgba(147,221,213,0.045)';
        |            for (let i=1;i<cells;i++) for (let j=1;j<cells;j++) {
        |              ctx.fillRect(i*unit-.6,j*unit-.6,1.2,1.2);
        |            }
        |            ctx.fillStyle = '#efb981';
        |            ctx.shadowColor = '#efb981';ctx.shadowBlur = 13;
        |            tile(food.x*unit+3,food.y*unit+3,unit-6,4);
        |            ctx.shadowBlur = 0;
        |            snake.forEach((p,i) => {
        |              const old = previous[i] || p;
        |              const canTween = Math.abs(p.x-old.x)<=1 && Math.abs(p.y-old.y)<=1;
        |              const x = (canTween ? old.x+(p.x-old.x)*alpha : p.x)*unit;
        |              const y = (canTween ? old.y+(p.y-old.y)*alpha : p.y)*unit;
        |              ctx.fillStyle = i ? '#52cbbb' : '#c0ffdc';
        |              if (!i) {ctx.shadowColor='#5cecca';ctx.shadowBlur=9;}
        |              tile(x+1.1,y+1.1,unit-2.2,4.5);
        |              ctx.shadowBlur=0;
        |            });
        |          }
        |          function frame(time) {
        |            if (!lastFrame) lastFrame=time;
        |            const delta = Math.min(48,time-lastFrame);
        |            lastFrame=time;
        |            if (started && !lost) {
        |              accumulator += delta;
        |              if (accumulator >= interval) { accumulator -= interval; tick(); }
        |            }
        |            draw(started && !lost ? Math.min(1,accumulator/interval) : 1);
        |            requestAnimationFrame(frame);
        |          }
        |          function swipe(dx,dy) {
        |            if (Math.max(Math.abs(dx),Math.abs(dy)) < 16) return false;
        |            turn(Math.abs(dx)>Math.abs(dy) ? (dx>0?'right':'left') : (dy>0?'down':'up'));
        |            return true;
        |          }
        |          let origin = null;
        |          surface.addEventListener('pointerdown', event => {
        |            origin={x:event.clientX,y:event.clientY};
        |            if (lost) play();
        |            else if (!started) play();
        |            if (surface.setPointerCapture) surface.setPointerCapture(event.pointerId);
        |            event.preventDefault();
        |          });
        |          surface.addEventListener('pointermove', event => {
        |            if (!origin) return;
        |            if (swipe(event.clientX-origin.x,event.clientY-origin.y))
        |              origin={x:event.clientX,y:event.clientY};
        |            event.preventDefault();
        |          });
        |          const finish = event => {
        |            if (!origin) return;
        |            swipe(event.clientX-origin.x,event.clientY-origin.y);
        |            origin=null;event.preventDefault();
        |          };
        |          surface.addEventListener('pointerup',finish);
        |          surface.addEventListener('pointercancel', () => { origin=null; });
        |          document.addEventListener('keydown', event => {
        |            const key = event.key.replace('Arrow','').toLowerCase();
        |            const mapped = key==='w'?'up':key==='a'?'left':key==='s'?'down':key==='d'?'right':key;
        |            if (vectors[mapped]) { event.preventDefault();turn(mapped); }
        |            if (event.key===' ' && lost) {event.preventDefault();play();}
        |          });
        |          window.annieCanvasSnakeState = () => ({
        |            direction, pending, moves, score:points, started, lost
        |          });
        |          reset();
        |          requestAnimationFrame(frame);
        |          window.annieCanvasSnakeReady = true;
        |        })();
        |      `
        |    };
        |  }
        |});
    """.trimMargin()
}
