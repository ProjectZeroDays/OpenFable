#!/usr/bin/env python3
"""
Agnes Key Rotation Manager

Maintains a rotating index across the Agnes AI custom providers to distribute
rate-limit exposure across the full key pool.  The index persists to disk so
rotation survives process restarts.

Files:
  - State file: <project>/.hermes/agnes_rotation_index.json
    {"current": <int 0-7>, "last_used": "<iso timestamp>", "total_requests": <n>}

Environment:
  - AGNES_ROTATION_PROVIDER_COUNT  (default 8)  — number of providers in the pool
  - AGNES_ROTATION_STATE_FILE      (default: .hermes/agnes_rotation_index.json)

Usage:
  python agnes_key_rotate.py get-provider   # prints the next provider name
  python agnes_key_rotate.py advance        # advance to next provider + save
  python agnes_key_rotate.py status         # prints current rotation state
  python agnes_key_rotate.py set-provider <index>  # force a specific provider
"""
from __future__ import annotations

import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[3] if __name__ == "__main__" else Path(".")
DEFAULT_STATE_FILE = PROJECT_ROOT / ".hermes" / "agnes_rotation_index.json"
DEFAULT_PROVIDER_COUNT = 8


def _state_file() -> Path:
    return Path(os.environ.get("AGNES_ROTATION_STATE_FILE", str(DEFAULT_STATE_FILE)))


def _provider_count() -> int:
    try:
        return int(os.environ.get("AGNES_ROTATION_PROVIDER_COUNT", str(DEFAULT_PROVIDER_COUNT)))
    except ValueError:
        return DEFAULT_PROVIDER_COUNT


def load_state() -> dict:
    sf = _state_file()
    if not sf.exists():
        return {"current": 0, "last_used": None, "total_requests": 0}
    try:
        with open(sf, "r") as f:
            return json.load(f)
    except (json.JSONDecodeError, OSError):
        return {"current": 0, "last_used": None, "total_requests": 0}


def save_state(state: dict) -> None:
    sf = _state_file()
    sf.parent.mkdir(parents=True, exist_ok=True)
    with open(sf, "w") as f:
        json.dump(state, f, indent=2)


def current_provider() -> str:
    state = load_state()
    idx = state.get("current", 0) % _provider_count()
    return f"agnes-rot-{idx}"


def advance_provider() -> str:
    state = load_state()
    count = _provider_count()
    state["current"] = (state.get("current", 0) + 1) % count
    state["last_used"] = datetime.now(timezone.utc).isoformat()
    state["total_requests"] = state.get("total_requests", 0) + 1
    save_state(state)
    return f"agnes-rot-{state['current']}"


def set_provider(index: int) -> str:
    count = _provider_count()
    idx = max(0, index % count)
    state = load_state()
    state["current"] = idx
    state["last_used"] = datetime.now(timezone.utc).isoformat()
    save_state(state)
    return f"agnes-rot-{idx}"


def status_str() -> str:
    state = load_state()
    count = _provider_count()
    idx = state.get("current", 0) % count
    return (
        f"current: agnes-rot-{idx}\n"
        f"index: {idx}\n"
        f"providers: {count}\n"
        f"last_used: {state.get('last_used', 'never')}\n"
        f"total_requests: {state.get('total_requests', 0)}"
    )


def main() -> int:
    if len(sys.argv) < 2:
        print("Usage: agnes_key_rotate.py [get-provider|advance|status|set-provider <index>]", file=sys.stderr)
        return 1

    cmd = sys.argv[1].lower()

    if cmd == "get-provider":
        print(current_provider())
    elif cmd == "advance":
        print(advance_provider())
    elif cmd == "status":
        print(status_str())
    elif cmd == "set-provider":
        if len(sys.argv) < 3:
            print("Usage: agnes_key_rotate.py set-provider <index>", file=sys.stderr)
            return 1
        print(set_provider(int(sys.argv[2])))
    else:
        print(f"Unknown command: {cmd}", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
