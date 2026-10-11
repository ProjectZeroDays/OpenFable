# CookieHarvesterAgent — White Paper

## Overview
Cookie theft and session hijacking.

## Architecture
The CookieHarvesterAgent agent implements a modular exploitation framework with:
- Vulnerability discovery
- Payload generation
- Exploitation orchestration
- Post-exploitation capabilities

## CVE Database
- Maintains a curated database of relevant CVEs
- Supports real-time CVE lookups
- Tracks exploit availability status

## Testing
Run tests with:
```bash
pytest cookieharvesteragent/test_cookieharvesteragent.py -v
```
