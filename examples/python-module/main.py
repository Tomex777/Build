import json
import os
import sys
import time

next_id = 0


def rpc(service, method, params):
    global next_id
    next_id += 1
    call_id = f"python-call-{next_id}"
    send({"protocol": 1, "id": call_id, "type": "host.call", "service": service, "method": method, "params": params})
    # The host sends host.result separately while this command/event/job is pending.
    for raw in sys.stdin:
        message = json.loads(raw)
        if message.get("type") == "host.result" and message.get("replyTo") == call_id:
            if not message.get("ok"):
                raise RuntimeError(message.get("error", "Host service call failed"))
            return message.get("result")
    raise RuntimeError("Bailey closed the Protocol 1 stream during a service call")


def result(request, actions=None):
    send({"protocol": 1, "replyTo": request["id"], "ok": True, "actions": actions or []})


def send(payload):
    sys.stdout.write(json.dumps(payload, separators=(",", ":")) + "\n")
    sys.stdout.flush()


for raw in sys.stdin:
    raw = raw.strip()
    if not raw:
        continue

    try:
        request = json.loads(raw)
        if request.get("protocol") != 1:
            raise ValueError("Unsupported Bailey module protocol")

        request_type = request.get("type")
        context = request.get("context", {})

        if request_type == "lifecycle.start":
            # Use this hook to prepare local module state. Bailey owns worker restart/reload.
            actions = [{"type": "log", "level": "info", "message": "Python example started"}]
        elif request_type == "lifecycle.stop":
            actions = [{"type": "log", "level": "info", "message": "Python example stopped"}]
        elif request_type == "command.execute" and request.get("commandId") == "pyhello":
            greeting = os.getenv("PYTHON_EXAMPLE_GREETING", "Hello")
            sender = context.get("senderJid", "there")
            saved = rpc("storage", "put", {"key": "example/last-greeting.txt", "text": f"{greeting} from Python"})
            actions = [{"type": "reply", "text": f"{greeting} from Python, {sender}! Storage saved {saved.get('size', 0)} bytes."}, {"type": "react", "emoji": "🐍"}]
        elif request_type == "job.execute" and request.get("jobId") == "daily-summary":
            usage = rpc("storage", "get", {"key": "example/last-greeting.txt", "encoding": "text"})
            actions = [{"type": "log", "level": "info", "message": f"Daily job ran at {time.strftime('%Y-%m-%d %H:%M:%S UTC', time.gmtime())}; saved greeting exists: {bool(usage.get('text'))}."}]
        elif request_type == "event.dispatch" and request.get("event") == "message.received":
            text = str(context.get("text") or "").strip().lower()
            actions = []
            if text == "hello python":
                actions = [
                    {"type": "reply", "text": "Python heard an ordinary message event."},
                    {"type": "react", "emoji": "👂"},
                ]
        else:
            raise ValueError("Unsupported request")

        result(request, actions)
    except Exception as error:
        request_id = "unknown"
        try:
            request_id = request.get("id", "unknown")
        except Exception:
            pass
        send({
            "protocol": 1,
            "replyTo": request_id,
            "ok": False,
            "error": str(error),
        })
