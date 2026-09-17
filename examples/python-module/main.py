import json
import os
import sys


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

        if request_type == "command.execute" and request.get("commandId") == "pyhello":
            greeting = os.getenv("PYTHON_EXAMPLE_GREETING", "Hello")
            sender = context.get("senderJid", "there")
            actions = [
                {"type": "reply", "text": f"{greeting} from Python, {sender}!"},
                {"type": "react", "emoji": "🐍"},
            ]
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

        send({
            "protocol": 1,
            "replyTo": request["id"],
            "ok": True,
            "actions": actions,
        })
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
