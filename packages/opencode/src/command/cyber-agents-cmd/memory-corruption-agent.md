---
name: memory-corruption-agent
description: Memory corruption exploitation
---
# /memory-corruption-agent

Invoke the **MemoryCorruptionAgent** cyber-security agent (sources vendored under `src/agent/cyber-security/MemoryCorruptionAgent/`).

## Usage
```python
from memorycorruptionagent import MemoryCorruptionAgent
agent = MemoryCorruptionAgent()
result = agent.describe()
```

## Capabilities
- Vulnerability scanning and exploitation
- Payload generation and delivery
- Post-exploitation and persistence
- Evasion techniques

Skill doc: `src/skill/cyber-agents/memory-corruption-agent/SKILL.md`. Full sources: `src/agent/cyber-security/MemoryCorruptionAgent/`.

