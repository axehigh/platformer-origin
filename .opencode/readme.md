# .opencode Directory

This directory is used by the `@opencode-ai/plugin` npm package for plugin functionality.

## Structure

```
.opencode/
  agents/           # libGDX specialist agents (markdown files)
  node_modules/     # plugin dependencies (@opencode-ai/plugin)
  package.json      # plugin dependency declaration
  skills/           # symlink to .junie/skills/
```

## Agent Registration

Both mechanisms are active, so keep them in sync:

1. Project-level agents are defined as markdown files in `.opencode/agents/` (full instructions live here).
2. Minimal agent entries (name + mode + description) are mirrored in the project-level `opencode.json` under the `"agents"` key.
3. `.opencode/commands/` drives agents directly (e.g. `design.md` runs as the `game-designer` subagent).

These must stay consistent: no agent file without a matching `opencode.json` entry (and vice versa).

## Agent Files (reference)

The markdown files in `.opencode/agents/` hold the full agent instructions:

- `game-developer.md` — Primary/lead agent; orchestrates planning, research, implementation, testing
- `game-designer.md` — Design discussion partner (no code)
- `libgdx-developer.md` — Implements game features (ECS, rendering, maps)
- `libgdx-explorer.md` — Read-only codebase research
- `libgdx-planner.md` — Plans features, drafts GitHub issues
- `libgdx-tester.md` — Writes/runs headless system tests
