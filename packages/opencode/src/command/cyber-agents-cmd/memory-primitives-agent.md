---
name: memory-primitives-agent
description: Memory corruption primitive simulation: buffer overflow, use-after-free, double-free, heap overflow, format string, integer overflow, out-of-bounds, type confusion, TOCTOU race con
---
# /memory-primitives-agent

Invoke the **MemoryPrimitivesAgent** cyber-security agent (sources vendored under `src/agent/cyber-security/MemoryPrimitivesAgent/`).

## Usage
```python
from agents.specialized.memory_primitives import MemoryPrimitivesAgent

agent = MemoryPrimitivesAgent()

# List all primitives
primitives = agent.list_primitives()

# Get details on a specific primitive
details = agent.get_primitive("buffer_overflow")

# Simulate exploitation (returns {"status": "simulated"})
result = agent.simulate_primitive("buffer_overflow", {
    "target": "192.168.1.10",
    "overflow_type": "stack",
    "buffer_size": 256
})

# Map primitive to exploit techniques
mapping = agent.map_to_exploit("use_after_free")

# Find mitigations
mitigations = agent.find_mitigations("format_string")

# Get CVE database
cves = agent.get_cves()
```

## Capabilities
1. **Buffer Overflow (Stack/Heap)** â€” Writing beyond allocated buffer boundaries to overwrite adjacent memory. Stack overflows target return addresses and local variables; heap overflows target malloc metadata and adjacent chunks.
2. **Use-After-Free (UAF)** â€” Accessing heap memory after it has been freed, allowing attackers to control object state and achieve arbitrary code execution via vtable hijacking or function pointer overwrite.
3. **Double-Free** â€” Calling free() twice on the same pointer, corrupting allocator metadata (fastbin/tcache) to achieve arbitrary write or allocation control.
4. **Heap Overflow / Chunk Overflow** â€” Overflowing a heap allocation to corrupt adjacent chunk headers or data, enabling unlink attacks, tcache poisoning, or house-of-* techniques.
5. **Format String Vulnerability** â€” Exploiting unchecked format string arguments (printf family) to leak stack memory (%p/%x), write arbitrary addresses (%n), or control execution flow.
6. **Integer Overflow / Underflow** â€” Arithmetic operations that wrap around type boundaries, causing undersized allocations, incorrect loop bounds, or bypassed security checks.
7. **Out-of-Bounds Read/Write** â€” Accessing array or buffer indices outside valid range, enabling information disclosure (OOB read) or memory corruption (OOB write).
8. **Type Confusion** â€” Treating an object as an incorrect type, leading to misinterpreted memory layout, vtable mismatch, or incorrect method dispatch.
9. **Race Conditio

Skill doc: `src/skill/cyber-agents/memory-primitives-agent/SKILL.md`. Full sources: `src/agent/cyber-security/MemoryPrimitivesAgent/`.

