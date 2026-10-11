#!/usr/bin/env python3
"""
Setup Hermes Custom Providers

Configures multiple custom AI provider API keys in hermes CLI for
round-robin rotation and rate-limit management.

Usage:
    python setup_providers.py --provider agnes     # Configure Agnes (8 keys)
    python setup_providers.py --provider venice     # Configure Venice
    python setup_providers.py --all                 # Configure all
    python setup_providers.py --hermes /path/to/hermes  # Use specific hermes binary
"""
import argparse
import os
import subprocess
import sys

AGNES_BASE_URL = "https://apihub.agnes-ai.com/v1"
AGNES_DEFAULT_MODEL = "agnes-2.0-flash"
VENICE_BASE_URL = "https://api.venice.ai/api/v1"
VENICE_DEFAULT_MODEL = "llama-3.3-70b"


def get_agnes_keys() -> list[str]:
    """Collect all Agnes API keys from environment."""
    keys = []
    for i in range(1, 9):
        val = os.environ.get(f"AGNES_API_KEY_{i}", "")
        if val:
            keys.append(val)
    legacy = os.environ.get("AGNES_API_KEY", "")
    if legacy and legacy not in keys:
        keys.append(legacy)
    if not keys:
        print("WARNING: No Agnes API keys found in environment. Using defaults.")
        keys = [
            "sk-4qBlBtja6ZuLXLpRT69QGd9NEt0A3yI7HRRAwlzvV13KCNft",
            "sk-DavUfMcbyWPSROsuLrmiiZvdENT9aznOKYbJMvkxf3ataaj4",
            "sk-gE940pJBd02SRt3c8hBZPvQ3RsnM2gM14EuWJO3DkXeSbtb4",
            "Sk-gE940pJBd02SRt3c8hBZPvQ3RsnM2gM14EuWJO3DkXeSbtb4",
            "sk-qxfme7mHXOsx8FCBBlvtjnpLFDcqkdOQKcCfJWhHlB3BkszG",
            "sk-zI6D3bfHeMzdrvbfp2fHrgIrz1Sc2aJB43KUswJMAkoAXS7c",
            "sk-Po1GN96CBN8wrhRNvnYmENlUZGAsrjd7du7hOzNnKpp2qgHw",
            "e3867409-9741-4629-a5f8-51d49d0734e5",
        ]
    return keys


def get_venice_keys() -> list[str]:
    """Collect all Venice API keys from environment."""
    keys = []
    for i in range(1, 5):
        val = os.environ.get(f"VENICE_API_KEY_{i}", "")
        if val:
            keys.append(val)
    legacy = os.environ.get("VENICE_API_KEY", "")
    if legacy and legacy not in keys:
        keys.append(legacy)
    admin = os.environ.get("VENICEADMIN_API_KEY", "")
    if admin and admin not in keys:
        keys.append(admin)
    return keys


def hermes_set(hermes_bin: str, key: str, value: str):
    """Run a hermes config set command."""
    cmd = [hermes_bin, "config", "set", key, value]
    print(f"  $ {' '.join(cmd)}")
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        if result.returncode != 0:
            print(f"  ERROR: {result.stderr.strip()}")
        return result.returncode == 0
    except FileNotFoundError:
        print(f"  ERROR: hermes binary not found at '{hermes_bin}'")
        return False
    except subprocess.TimeoutExpired:
        print(f"  ERROR: hermes config timed out")
        return False


def configure_provider(hermes_bin: str, provider_name: str, api_key: str,
                        base_url: str, model: str, index: int):
    """Configure a single hermes custom provider."""
    key_prefix = f"model.{index}"
    print(f"\n  Configuring {provider_name} #{index}...")
    hermes_set(hermes_bin, f"{key_prefix}.provider", "custom")
    hermes_set(hermes_bin, f"{key_prefix}.baseurl", base_url)
    hermes_set(hermes_bin, f"{key_prefix}.apikey", api_key)
    hermes_set(hermes_bin, f"{key_prefix}.default", model)
    hermes_set(hermes_bin, f"{key_prefix}.name", f"{provider_name}-{index}")


def configure_agnes(hermes_bin: str):
    """Configure all Agnes API keys as separate hermes custom providers."""
    print("\n═══ Configuring Agnes AI providers (8 keys) ═══")
    keys = get_agnes_keys()
    for i, key in enumerate(keys, 1):
        configure_provider(
            hermes_bin, "agnes", key,
            AGNES_BASE_URL, AGNES_DEFAULT_MODEL, i
        )
    print(f"\n✓ Configured {len(keys)} Agnes providers")


def configure_venice(hermes_bin: str):
    """Configure Venice API keys as hermes custom providers."""
    print("\n═══ Configuring Venice AI providers ═══")
    keys = get_venice_keys()
    if not keys:
        print("  No Venice API keys found, skipping.")
        return
    for i, key in enumerate(keys, 1):
        configure_provider(
            hermes_bin, "venice", key,
            VENICE_BASE_URL, VENICE_DEFAULT_MODEL, i
        )
    print(f"\n✓ Configured {len(keys)} Venice providers")


def main():
    parser = argparse.ArgumentParser(
        description="Setup hermes custom providers with API key rotation"
    )
    parser.add_argument(
        "--provider", choices=["agnes", "venice"],
        help="Configure specific provider"
    )
    parser.add_argument("--all", action="store_true", help="Configure all providers")
    parser.add_argument(
        "--hermes", default="hermes",
        help="Path to hermes executable (default: hermes in PATH)"
    )
    args = parser.parse_args()

    if not args.all and not args.provider:
        parser.print_help()
        sys.exit(1)

    if args.provider == "agnes" or args.all:
        configure_agnes(args.hermes)
    if args.provider == "venice" or args.all:
        configure_venice(args.hermes)

    if args.all:
        print("\n✓ All providers configured with rotation support")


if __name__ == "__main__":
    main()
