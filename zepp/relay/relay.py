#!/usr/bin/env python3
"""FMD relay for the Zepp OS remote (reference implementation).

Receives command requests from the Zepp app-side service (which has no
local path out of the Zepp sandbox — fetch() to the internet is its only
escape hatch) and forwards them to the phone via an ntfy topic.

Phone side: the ntfy Android app (or any UnifiedPush distributor) is
subscribed to TOPIC; delivery into the FMD Watch Bridge is the remaining
integration point (see zepp/README.md "the last hop" — options and
contributor help wanted).

Self-host me:  python3 relay.py  (or behind any TLS-terminating proxy;
Zepp side services require https).
"""

import json
import os
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

TOKEN = os.environ.get("FMD_RELAY_TOKEN", "change-me")
NTFY_URL = os.environ.get("FMD_RELAY_NTFY", "https://ntfy.sh")
TOPIC = os.environ.get("FMD_RELAY_TOPIC", "fmd-remote-CHANGE-ME")
PORT = int(os.environ.get("FMD_RELAY_PORT", "8765"))


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/command":
            self.send_error(404)
            return
        length = int(self.headers.get("Content-Length", 0))
        try:
            payload = json.loads(self.rfile.read(length))
        except ValueError:
            self.send_error(400)
            return
        if payload.get("token") != TOKEN or not payload.get("cmd"):
            self.send_error(403)
            return

        req = urllib.request.Request(
            f"{NTFY_URL}/{TOPIC}",
            data=payload["cmd"].encode("utf-8"),
            headers={"Title": "FMD Remote", "Priority": "high"},
        )
        try:
            urllib.request.urlopen(req, timeout=10)
            self.send_response(200)
        except Exception as exc:  # noqa: BLE001
            print("relay forward failed:", exc)
            self.send_error(502)
        self.end_headers()

    def log_message(self, format, *args):  # noqa: A002
        print("relay:", format % args)


if __name__ == "__main__":
    print(f"FMD relay on :{PORT} -> {NTFY_URL}/{TOPIC}")
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
