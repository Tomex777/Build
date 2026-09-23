import readline from "node:readline";

let nextId = 0;
const pendingCalls = new Map();

function send(message) {
  process.stdout.write(`${JSON.stringify(message)}\n`);
}

function hostCall(service, method, params) {
  const id = `javascript-call-${++nextId}`;
  return new Promise((resolve, reject) => {
    pendingCalls.set(id, { resolve, reject });
    send({ protocol: 1, id, type: "host.call", service, method, params });
  });
}

async function respond(request, actions = []) {
  send({ protocol: 1, replyTo: request.id, ok: true, actions });
}

async function handle(request) {
  if (request.type === "host.result") {
    const pending = pendingCalls.get(request.replyTo);
    if (!pending) return;
    pendingCalls.delete(request.replyTo);
    if (request.ok) pending.resolve(request.result);
    else pending.reject(new Error(request.error ?? "Host service call failed"));
    return;
  }

  try {
    if (request.type === "lifecycle.start") {
      await respond(request, [{ type: "log", level: "info", message: "JavaScript example started" }]);
    } else if (request.type === "lifecycle.stop") {
      await respond(request, [{ type: "log", level: "info", message: "JavaScript example stopped" }]);
    } else if (request.type === "command.execute" && request.commandId === "jshello") {
      const saved = await hostCall("storage", "put", { key: "example/last-greeting.txt", text: "Hello from JavaScript" });
      await respond(request, [
        { type: "reply", text: `Hello from JavaScript. Bailey stored ${saved.size} bytes.` },
        { type: "react", emoji: "👋" },
      ]);
    } else if (request.type === "event.dispatch" && request.event === "message.received") {
      if ((request.context?.text ?? "").trim().toLowerCase() === "hello javascript") {
        await respond(request, [{ type: "reply", text: "JavaScript received a passive Protocol 1 event." }]);
      } else {
        await respond(request);
      }
    } else if (request.type === "job.execute" && request.jobId === "daily-summary") {
      const saved = await hostCall("storage", "get", { key: "example/last-greeting.txt", encoding: "text" });
      const checked = await hostCall("network", "request", {
        url: "https://example.com/",
        method: "HEAD",
      });
      await respond(request, [{ type: "log", level: "info", message: `Daily check: stored greeting ${saved.text ? "found" : "missing"}; HTTPS status ${checked.status}.` }]);
    } else {
      await respond(request);
    }
  } catch (error) {
    send({ protocol: 1, replyTo: request.id, ok: false, error: error instanceof Error ? error.message : String(error) });
  }
}

readline.createInterface({ input: process.stdin }).on("line", (line) => {
  try {
    const request = JSON.parse(line);
    if (request.protocol !== 1 || !request.id && request.type !== "host.result") throw new Error("Unsupported Protocol 1 message");
    void handle(request);
  } catch (error) {
    process.stderr.write(`Invalid Bailey protocol input: ${error instanceof Error ? error.message : String(error)}\n`);
  }
});
