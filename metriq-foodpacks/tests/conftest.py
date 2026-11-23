"""Test-suite environment setup.

Some CI/agent sandboxes deny writes to the default ``pytest-of-<user>`` temp
root. Point pytest's fixture temp dir at a known-writable location while keeping
the tests themselves network-free.
"""
import os
import tempfile
from pathlib import Path

_preferred = Path(os.environ.get("TMPDIR") or "") / "opencode"
if not _preferred.is_dir():
    _preferred = Path("C:/Users/USER/AppData/Local/Temp/opencode")
if _preferred.is_dir() and os.access(_preferred, os.W_OK):
    tempfile.tempdir = str(_preferred)
