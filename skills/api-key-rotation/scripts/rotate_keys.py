#!/usr/bin/env python3
"""
Auto-Circulate API Keys for Rate-Limit Management

Rotates (cycles) through multiple API keys for each provider using
round-robin to distribute load and avoid rate-limit exhaustion.

Usage:
    python rotate_keys.py --all                    # Rotate all providers
    python rotate_keys.py --provider agnes          # Rotate Agnes only
    python rotate_keys.py --provider venice         # Rotate Venice only
    python rotate_keys.py --daemon --interval 30   # Background auto-rotation
    python rotate_keys.py --status                 # Show current status
    python rotate_keys.py --backend-url http://localhost:8000  # Use backend API
"""
import argparse
import json
import os
import signal
import sys
import time
from urllib import request, error

AGNES_BASE_URL = "https://apihub.agnes-ai.com/v1"
AGNES_DEFAULT_MODEL = "agnes-2.0-flash"

AGNES_API_KEYS = [
    "sk-4qBlBtja6ZuLXLpRT69QGd9NEt0A3yI7HRRAwlzvV13KCNft",
    "sk-DavUfMcbyWPSROsuLrmiiZvdENT9aznOKYbJMvkxf3ataaj4",
    "sk-gE940pJBd02SRt3c8hBZPvQ3RsnM2gM14EuWJO3DkXeSbtb4",
    "Sk-gE940pJBd02SRt3c8hBZPvQ3RsnM2gM14EuWJO3DkXeSbtb4",
    "sk-qxfme7mHXOsx8FCBBlvtjnpLFDcqkdOQKcCfJWhHlB3BkszG",
    "sk-zI6D3bfHeMzdrvbfp2fHrgIrz1Sc2aJB43KUswJMAkoAXS7c",
    "sk-Po1GN96CBN8wrhRNvnYmENlUZGAsrjd7du7hOzNnKpp2qgHw",
    "e3867409-9741-4629-a5f8-51d49d0734e5",
]

VENICE_API_KEYS = []


def load_keys_from_env():
    """Load API keys from environment variables if available."""
    global AGNES_API_KEYS, VENICE_API_KEYS
    env_agnes = []
    for i in range(1, 9):
        val = os.environ.get(f"AGNES_API_KEY_{i}", "")
        if val:
            env_agnes.append(val)
    legacy = os.environ.get("AGNES_API_KEY", "")
    if legacy and legacy not in env_agnes:
        env_agnes.append(legacy)
    if env_agnes:
        AGNES_API_KEYS = env_agnes

    env_venice = []
    for i in range(1, 5):
        val = os.environ.get(f"VENICE_API_KEY_{i}", "")
        if val:
            env_venice.append(val)
    legacy_v = os.environ.get("VENICE_API_KEY", "")
    if legacy_v and legacy_v not in env_venice:
        env_venice.append(legacy_v)
    admin_v = os.environ.get("VENICEADMIN_API_KEY", "")
    if admin_v and admin_v not in env_venice:
        env_venice.append(admin_v)
    if env_venice:
        VENICE_API_KEYS = env_venice


class KeyRotator:
    """Manages round-robin API key rotation for multiple providers."""

    def __init__(self):
        self._index: dict[str, int] = {"agnes": 0, "venice": 0}
        self._last_rotation: dict[str, float] = {"agnes": 0.0, "venice": 0.0}
        self._errors: dict[str, list[float]] = {"agnes": [], "venice": []}
        self._rotate_interval = int(os.environ.get("KEY_ROTATE_INTERVAL", "30"))
        load_keys_from_env()
        self._keys: dict[str, list[str]] = {
            "agnes": AGNES_API_KEYS,
            "venice": VENICE_API_KEYS,
        }
        self._backend_url = os.environ.get("BACKEND_URL", "http://localhost:8000")

    def _mask(self, key: str) -> str:
        if len(key) > 12:
            return f"{key[:8]}...{key[-4:]}"
        return "***" if key else ""

    def get_current_key(self, provider: str) -> str | None:
        """Get the current API key for a provider (with time-based rotation check)."""
        keys = self._keys.get(provider, [])
        if not keys:
            return None
        idx = self._index.get(provider, 0)
        return keys[idx % len(keys)]

    def rotate(self, provider: str) -> dict[str, any]:
        """Rotate to the next key for a provider (round-robin)."""
        keys = self._keys.get(provider, [])
        if not keys:
            return {"provider": provider, "status": "no_keys", "total_keys": 0}
        self._index[provider] = (self._index[provider] + 1) % len(keys)
        self._last_rotation[provider] = time.time()
        new_idx = self._index[provider]
        new_key = keys[new_idx]
        return {
            "provider": provider,
            "status": "rotated",
            "key_index": new_idx,
            "key_preview": self._mask(new_key),
            "total_keys": len(keys),
        }

    def auto_circulate_all(self) -> dict[str, any]:
        """Auto-circulate keys for all registered providers."""
        results = {}
        for provider in self._keys:
            results[provider] = self.rotate(provider)
        return {
            "timestamp": time.time(),
            "rotation_interval_seconds": self._rotate_interval,
            "results": results,
        }

    def check_rate_limit(self, provider: str, elapsed: float) -> bool:
        """Check if enough time has elapsed to rotate."""
        if elapsed - self._last_rotation.get(provider, 0) > self._rotate_interval:
            return True
        return False

    def status(self) -> dict[str, any]:
        """Get current rotation status for all providers."""
        result = {}
        for provider, keys in self._keys.items():
            idx = self._index.get(provider, 0)
            result[provider] = {
                "total_keys": len(keys),
                "current_index": idx,
                "current_key": self._mask(keys[idx]) if keys else "",
                "last_rotation": self._last_rotation.get(provider, 0),
                "rotate_interval": self._rotate_interval,
            }
        return result

    def call_backend_rotate(self, provider: str) -> dict[str, any]:
        """Call the backend API to rotate a provider's key."""
        url = f"{self._backend_url}/api/chatbot/rotate-key/{provider}"
        try:
            req = request.Request(url, method="POST")
            with request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode())
                return data
        except (error.URLError, error.HTTPError, Exception) as e:
            return {"provider": provider, "status": "backend_error", "error": str(e)}

    def call_backend_auto_circulate(self) -> dict[str, any]:
        """Call the backend API to auto-circulate all keys."""
        url = f"{self._backend_url}/api/chatbot/auto-circulate"
        try:
            req = request.Request(url, method="POST")
            with request.urlopen(req, timeout=10) as resp:
                data = json.loads(resp.read().decode())
                return data
        except (error.URLError, error.HTTPError, Exception) as e:
            return {"status": "backend_error", "error": str(e)}


_running = True


def _signal_handler(signum, frame):
    global _running
    _running = False
    print("\n[daemon] Stopping auto-circulation...")


def main():
    parser = argparse.ArgumentParser(
        description="Auto-circulate API keys for rate-limit management"
    )
    parser.add_argument("--provider", choices=["agnes", "venice"],
                        help="Rotate specific provider only")
    parser.add_argument("--all", action="store_true", help="Rotate all providers")
    parser.add_argument("--daemon", action="store_true",
                        help="Run continuously in background")
    parser.add_argument("--interval", type=int, default=30,
                        help="Rotation interval in seconds (default: 30)")
    parser.add_argument("--status", action="store_true",
                        help="Show current rotation status")
    parser.add_argument("--backend", action="store_true",
                        help="Use backend API for rotation")
    parser.add_argument("--backend-url", default=None,
                        help="Backend URL (default: http://localhost:8000)")
    args = parser.parse_args()

    rotator = KeyRotator()
    if args.backend_url:
        rotator._backend_url = args.backend_url
    if args.interval:
        rotator._rotate_interval = args.interval

    if args.status:
        status = rotator.status()
        print(json.dumps(status, indent=2))
        return

    if args.backend:
        if args.provider:
            result = rotator.call_backend_rotate(args.provider)
            print(json.dumps(result, indent=2))
        else:
            result = rotator.call_backend_auto_circulate()
            print(json.dumps(result, indent=2))
        return

    if args.daemon:
        signal.signal(signal.SIGINT, _signal_handler)
        signal.signal(signal.SIGTERM, _signal_handler)
        print(f"[daemon] Auto-circulation started (interval={args.interval}s)")
        while _running:
            now = time.time()
            for provider in list(rotator._keys):
                if rotator.check_rate_limit(provider, now):
                    result = rotator.rotate(provider)
                    print(f"[daemon] {result}")
            time.sleep(1)
        print("[daemon] Stopped.")
        return

    if args.provider:
        result = rotator.rotate(args.provider)
        print(json.dumps(result, indent=2))
    elif args.all:
        result = rotator.auto_circulate_all()
        print(json.dumps(result, indent=2))
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()
