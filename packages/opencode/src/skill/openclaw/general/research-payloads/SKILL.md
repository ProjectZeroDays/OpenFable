---
name: compose:research-payloads
hidden: true
description: research-payloads
source_platform: openclaw
---

# Research Payloads

This skill enables the agent to stay current with the global threat landscape.

## Research Workflow
1. **Source Identification**: Query GitHub, Pastebin, and specialized security repos for keywords related to target OS/versions.
2. **Payload Analysis**: Evaluate the provided code for stability, detection signatures, and effectiveness.
3. **Sourcing**: Extract the raw payload and store it in the `payloads/` directory for customization.
4. **Categorization**: Label payloads by attack vector (e.g., "Zero-Click", "PrivEsc", "Persistence").
