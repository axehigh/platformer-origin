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

## Known Issue: Agent Detection

Project-level agents defined as markdown files in `.opencode/agents/` are **not detected** by opencode. This appears to be because:

1. The `.opencode/` directory already exists as a plugin directory (contains `package.json` with `@opencode-ai/plugin`)
2. opencode may treat `.opencode/` as a plugin directory and skip agent discovery from `.opencode/agents/`
3. The `.gitignore` confirms this — it ignores plugin files but NOT the agents directory:
   ```
   /.opencode/node_modules
   /.opencode/skills
   /.opencode/package.json
   /.opencode/package-lock.json
   ```

## Workaround

Define the libGDX agents in the project-level `opencode.json` under the `"agent"` key. This bypasses directory scanning entirely.

## Agent Files (reference)

The markdown files in `.opencode/agents/` remain as documentation and for manual copy/paste:

- `libgdx-developer.md` — Implements game features (ECS, rendering, maps)
- `libgdx-explorer.md` — Read-only codebase research
- `libgdx-planner.md` — Plans features, drafts GitHub issues
- `libgdx-tester.md` — Writes/runs headless system tests
