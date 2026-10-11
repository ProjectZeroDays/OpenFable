# PayloadEngineAgent — Deployment Guide

## Prerequisites
- Python 3.11+
- Required packages: see requirements.txt
- API keys: configured in settings

## Installation
```bash
cd payloadengineagent
pip install -r requirements.txt
python -m pytest test_payloadengineagent.py -v
```

## Configuration
Edit `config.json`:
```json
{
  "api_key": "your-key-here",
  "timeout": 30,
  "max_retries": 3
}
```

## Testing
```bash
pytest test_payloadengineagent.py -v --tb=short
```
