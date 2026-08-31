"""
GreenHands AI Decision API entrypoint.

Preferred:
    cd backend
    pip install -r requirements.txt
    python -m uvicorn ai_server:app --host 0.0.0.0 --port 8002

Or from repo root:
    python main.py
"""

from __future__ import annotations

import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parent / "backend"
if str(BACKEND) not in sys.path:
    sys.path.insert(0, str(BACKEND))

from ai_server import app  # noqa: E402

__all__ = ["app"]

if __name__ == "__main__":
    import uvicorn

    print("GreenHands AI listening on http://0.0.0.0:8002 (LAN-reachable)")
    uvicorn.run("ai_server:app", host="0.0.0.0", port=8002, reload=False, app_dir=str(BACKEND))
