#!/usr/bin/env python3
"""
Show API Key Rotation Status

Displays the current rotation state, active keys, and rate-limit info
for all configured providers.

Usage:
    python key_status.py                     # Show all providers
    python key_status.py --provider agnes     # Show Agnes only
    python key_status.py --backend            # Fetch from backend API
"""
import argparse
import json
import os
import sys
from datetime import datetime, timezone
from urllib import request, error

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "scripts"))


def load_keys():
    """Load API keys from environment or defaults."""
    agnes_keys = []
    for i in range(1, 9):
        val = os.environ.get(f"AGNES_API_KEY_{i}", "")
        if val:
            agnes_keys.append(val)
    legacy = os.environ.get("AGNES_API_KEY", "")
    if legacy and legacy not in agnes_keys:
        agnes_keys.append(legacy)
    if not agnes_keys:
        agnes_keys = [
            "sk-4qBlBtja6ZuLXLpRT69QGd9NEt0A3yI7HRRAwlzvV13KCNft",
            "sk-DavUfMcbyWPSROsuLrmiiZvdENT9aznOKYbJMvkxf3ataaj4",
            "sk-gE940pJBd02SRt3c8hBZPvQ3RsnM2gM14EuWJO3DkXeSbtb4",
        ]

    venice_keys = []
    for i in range(1, 5):
        val = os.environ.get(f"VENICE_API_KEY_{i}", "")
        if val:
            venice_keys.append(val)
    legacy_v = os.environ.get("VENICE_API_KEY", "")
    if legacy_v and legacy_v not in venice_keys:
        venice_keys.append(legacy_v)
    admin_v = os.environ.get("VENICEADMIN_API_KEY", "")
    if admin_v and admin_v not in venice_keys:
        venice_keys.append(admin_v)

    return {
        "agnes": {
            "base_url": "https://apihub.agnes-ai.com/v1",
            "model": "agnes-2.0-flash",
            "keys": agnes_keys,
        },
        "venice": {
            "base_url": "https://api.venice.ai/api/v1",
            "model": "llama-3.3-70b",
            "keys": venice_keys,
        },
    }


def mask_key(key: str) -> str:
    if len(key) > 12:
        return f"{key[:8]}...{key[-4:]}"
    return "***" if key else ""


def show_local_status(provider_filter: str | None = None):
    """Show status of locally configured keys."""
    providers = load_keys()
    if provider_filter:
        if provider_filter not in providers:
            print(f"Unknown provider: {provider_filter}")
            sys.exit(1)
        providers = {provider_filter: providers[provider_filter]}

    for name, cfg in providers.items():
        print(f"\n{'='*50}")
        print(f"  Provider: {name.upper()}")
        print(f"  Base URL: {cfg['base_url']}")
        print(f"  Model:    {cfg['model']}")
        print(f"  Keys:     {len(cfg['keys'])}")
        print(f"  Env Vars: AGNES_API_KEY_1..{len(cfg['keys'])}" if name == "agnes"
              else "  Env Vars: VENICE_API_KEY_1..N")
        print(f"{'='*50}")
        for i, key in enumerate(cfg["keys"]):
            marker = " ◀ CURRENT" if i == 0 else ""
            print(f"  [{i}] {mask_key(key)}{marker}")
        print()


def show_backend_status(backend_url: str, provider_filter: str | None = None):
    """Fetch status from the backend API."""
    url = f"{backend_url}/api/chatbot/api-key/status"
    try:
        req = request.Request(url)
        with request.urlopen(req, timeout=10) as resp:
            data = json.loads(resp.read().decode())
        print(json.dumps(data, indent=2))
    except (error.URLError, error.HTTPError, Exception) as e:
        print(f"Error fetching from backend: {e}")
        sys.exit(1)

    # Also fetch rate-limit status
    rl_url = f"{backend_url}/api/chatbot/rate-limit/status"
    try:
        req = request.Request(rl_url)
        with request.urlopen(req, timeout=10) as resp:
            rl_data = json.loads(resp.read().decode())
        print("\n--- Rate Limit Status ---")
        print(json.dumps(rl_data, indent=2))
    except Exception:
        pass


def main():
    parser = argparse.ArgumentParser(
        description="Show API key rotation status"
    )
    parser.add_argument("--provider", choices=["agnes", "venice"],
                        help="Show specific provider only")
    parser.add_argument("--backend", action="store_true",
                        help="Fetch status from backend API")
    parser.add_argument("--backend-url", default="http://localhost:8000",
                        help="Backend API URL (default: http://localhost:8000)")
    args = parser.parse_args()

    if args.backend:
        show_backend_status(args.backend_url, args.provider)
    else:
        show_local_status(args.provider)


if __name__ == "__main__":
    main()
