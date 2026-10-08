"""Generate OpenFableSeed.kt: compiled-in skill/agent registry for the Android app.

Scans OpenFable skills/, agents/, and command/agent-definition surfaces,
then emits a Kotlin object with chunked listOf() parts (50 entries each, to
stay under JVM method size limits). All strings are JSON-escaped so they are
valid Kotlin string literals.
"""
import hashlib
import json
import re
from pathlib import Path

OF = Path(r"C:\Projects\OpenFable")
OUT = OF / "android" / "app" / "src" / "main" / "java" / "com" / "projectzerodays" / "quantumcli" / "ai" / "OpenFableSeed.kt"

PROMPT_MAX = 1500
DESC_MAX = 300
NAME_MAX = 80
CHUNK = 50


def klit(s: str) -> str:
    # Escape $ BEFORE JSON-encoding: Kotlin "..." strings interpolate $var/${...}.
    # ${'$'} renders a literal $ without starting a template.
    return json.dumps(s.replace("$", "${'$'}"), ensure_ascii=True)


def clean_id(s: str) -> str:
    s = s.lower().strip()
    s = re.sub(r"[^a-z0-9._-]+", "-", s)
    return s.strip("-.") or "unnamed"


def parse_frontmatter(text: str):
    """Return (name, description, body) from a SKILL.md/AGENT.md file."""
    name, desc = "", ""
    body = text
    if text.startswith("---"):
        end = text.find("\n---", 3)
        if end != -1:
            fm = text[3:end].strip().splitlines()
            body = text[end + 4:].lstrip("\n")
            i = 0
            while i < len(fm):
                line = fm[i]
                m = re.match(r"^([A-Za-z_]+):\s*(.*)$", line)
                if m:
                    key, val = m.group(1).lower(), m.group(2).strip().strip('"').strip("'")
                    if key in ("name", "description") and val in (">", "|", ">-", "|-"):
                        buf = []
                        i += 1
                        while i < len(fm) and (fm[i].startswith(" ") or fm[i].strip() == ""):
                            if fm[i].strip():
                                buf.append(fm[i].strip())
                            i += 1
                        val = " ".join(buf)
                        if key == "name":
                            name = val
                        else:
                            desc = val
                        continue
                    if key == "name":
                        name = val
                    elif key == "description":
                        desc = val
                i += 1
    return name, desc, body


def entry_from_md(path: Path, default_id: str, kind: str):
    try:
        text = path.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return None
    name, desc, body = parse_frontmatter(text)
    if not name:
        name = default_id.replace("-", " ").replace(".", " / ").replace("_", " ")[:NAME_MAX]
    if not desc:
        for line in body.splitlines():
            s = line.strip("# ").strip()
            if s:
                desc = s
                break
    prompt = re.sub(r"\s+", " ", body).strip()[:PROMPT_MAX]
    return {
        "id": clean_id(default_id),
        "name": name[:NAME_MAX],
        "description": desc[:DESC_MAX],
        "prompt": prompt,
        "kind": kind,
    }


def collect() -> tuple[list, list]:
    skills: list = []
    agents: list = []
    seen_body = set()

    def add(entry, bucket):
        if entry is None:
            return
        h = hashlib.sha1((entry["id"] + entry["prompt"]).encode("utf-8", "replace")).hexdigest()
        if h in seen_body:
            return
        seen_body.add(h)
        bucket.append(entry)

    skills_root = OF / "skills"
    for top in sorted(skills_root.iterdir()):
        if not top.is_dir() or top.name == ".hermes":
            continue
        root_md = top / "SKILL.md"
        if root_md.is_file():
            add(entry_from_md(root_md, top.name, "skill"), skills)
        for md in sorted(top.rglob("SKILL.md")):
            if md == root_md:
                continue
            rel = md.parent.relative_to(top).as_posix().replace("/", ".")
            add(entry_from_md(md, f"{top.name}.{rel}", "skill"), skills)

    for extra, eid in [
        (OF / ".openfable" / "skills" / "never-ask" / "SKILL.md", "openfable.never-ask"),
        (OF / ".mimocode" / "skills" / "complete-the-project" / "SKILL.md", "mimocode.complete-the-project"),
    ]:
        if extra.is_file():
            add(entry_from_md(extra, eid, "skill"), skills)

    spec = OF / "agents" / "specialized"
    if spec.is_dir():
        for md in sorted(spec.rglob("SKILL.md")):
            rel = md.parent.relative_to(spec).as_posix().replace("/", ".")
            add(entry_from_md(md, f"specialized.{rel}", "agent"), agents)

    hermes = OF / "agents" / "hermes"
    if hermes.is_dir():
        for md in sorted(hermes.rglob("AGENT.md")):
            rel = md.parent.relative_to(hermes).as_posix().replace("/", ".")
            add(entry_from_md(md, f"hermes.{rel}", "agent"), agents)

    claude_dir = OF / ".claude" / "agents"
    if claude_dir.is_dir():
        for md in sorted(claude_dir.glob("*.md")):
            add(entry_from_md(md, f"claude.{md.stem}", "agent"), agents)

    everything = OF / ".zcode" / "agents" / "everything.md"
    if everything.is_file():
        add(entry_from_md(everything, "zcode.everything", "agent"), agents)

    swarm = OF / ".opencode" / "command" / "swarm.md"
    if swarm.is_file():
        add(entry_from_md(swarm, "opencode.swarm-driver", "agent"), agents)

    # dedupe ids (keep first)
    def dedupe(items):
        out, seen_ids = [], set()
        for e in items:
            if e["id"] in seen_ids:
                continue
            seen_ids.add(e["id"])
            out.append(e)
        return out

    return dedupe(skills), dedupe(agents)


def emit(entries, ctor, fields, prefix) -> str:
    parts = []
    names = []
    for i in range(0, len(entries), CHUNK):
        fname = f"{prefix}{i // CHUNK:03d}"
        names.append(f"{fname}()")
        lines = [f"    private fun {fname}() = listOf("]
        for e in entries[i:i + CHUNK]:
            args = ", ".join(klit(e[f]) for f in fields)
            lines.append(f"        {ctor}({args}),")
        lines.append("    )")
        parts.append("\n".join(lines))
    return "\n\n".join(parts), names


def main():
    skills, agents = collect()
    print(f"skills collected: {len(skills)}, agents collected: {len(agents)}")
    skill_body, skill_funs = emit(skills, "SkillDef", ["id", "name", "description", "prompt"], "s")
    agent_body, agent_funs = emit(agents, "AgentDef", ["id", "name", "prompt"], "a")
    # AgentDef has (id, name, systemPrompt, autonomy); autonomy defaults to manual.
    src = f"""package com.projectzerodays.quantumcli.ai

/**
 * Auto-generated by android/generate_openfable_seed.py from the OpenFable
 * skill/agent library ({len(skills)} skills, {len(agents)} agents).
 * Do not edit by hand - regenerate instead.
 */
object OpenFableSeed {{
    val skills: List<SkillDef> = {' + '.join(skill_funs) if skill_funs else 'emptyList()'}
    val agents: List<AgentDef> = {' + '.join(agent_funs) if agent_funs else 'emptyList()'}

{skill_body}

{agent_body}
}}
"""
    OUT.write_text(src, encoding="utf-8")
    print(f"wrote {OUT} ({OUT.stat().st_size / 1024:.1f} KB)")


if __name__ == "__main__":
    main()
