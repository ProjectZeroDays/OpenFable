#!/usr/bin/env python3
"""Tests for APISnifferAgent agent."""
import pytest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent.parent / "agents" / "specialized"))

from apisnifferagent import APISnifferAgent


def test_describe():
    agent = APISnifferAgent()
    result = agent.describe()
    assert result["name"] == "apisnifferagent"
    assert "description" in result
    assert result["category"] == "red_teaming"


def test_capabilities():
    agent = APISnifferAgent()
    desc = agent.describe()
    assert "capabilities" in desc
    assert len(desc["capabilities"]) > 0


if __name__ == "__main__":
    pytest.main([__file__, "-v"])
