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

        if request.get("type") == "command.execute" and request.get("commandId") == "pyhello":
            greeting = os.getenv("PYTHON_EXAMPLE_GREETING", "Hello")
            sender = request.get("context", {}).get("senderJid", "there")
            send({
                "protocol": 1,
                "replyTo": request["id"],
                "ok": True,
                "actions": [
                    {"type": "reply", "text": f"{greeting} from Python, {sender}!"},
                    {"type": "react", "emoji": "🐍"},
                ],
            })
        else:
            send({
                "protocol": 1,
                "replyTo": request.get("id", "unknown"),
                "ok": False,
                "error": "Unsupported request",
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
