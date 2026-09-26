package com.tomex777.annie

internal object StarterScripts {
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
}
