"""rsi_harness - a small, cross-platform recursive self-improvement harness.

The framework edits the files around an agent (prompts, rules, scripts, memory),
never the model weights. It learns from behavioural differences between harness
versions and never exposes grading signals to the editing loop.

Design invariants:
  1. A harness is a plain directory tree addressed with POSIX-style keys.
  2. Proposals are grounded in recorded trajectories, never in an answer key.
  3. Blind selection sees renamed artefacts, not harness provenance.
  4. Scoring happens in a separate `judge` phase and never flows back.
  5. Everything is deterministic, content-addressed and resumable.
  6. Only the Python standard library is used; no POSIX-only APIs.
"""

__version__ = "0.1.0"
