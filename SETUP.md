# LibGDX Agent Team — Setup Guide

Step-by-step guide to install and use the libGDX specialist agents on any machine.

## Prerequisites

| Requirement | Details |
|---|---|
| **opencode CLI** | Installed and available in PATH |
| **Java 21+** | Required by the project build |
| **Gradle** | Wrapped (`./gradlew`), no separate install needed |
| **Model access** | `opencode/big-pickle` must be available to your opencode provider |

## Quick Setup

```bash
# 1. Clone the repository
git clone <repo-url>
cd <project-directory>

# 2. Verify opencode is installed
opencode --version

# 3. Start opencode — agents are auto-discovered
opencode
```

That's it. The agents live in `.opencode/agents/` and are discovered automatically by opencode via convention. No configuration changes needed.

## What's Included

```
.opencode/
  agents/
    libgdx-developer.md    # Implements game features (ECS, rendering, maps)
    libgdx-explorer.md     # Read-only codebase research
    libgdx-planner.md      # Plans features, drafts GitHub issues
    libgdx-tester.md       # Writes/runs headless system tests

.junie/
  skills/
    libgdx-*/              # 30+ domain skills (rendering, ECS, tiled, etc.)
    ecs-system-testing/    # Headless ECS test conventions
    tmx-map-generator/     # Procedural map generation
    visual-runtime-debugging/  # Debug recipes for visual bugs

AGENTS.md                  # Project-specific rules & conventions
```

## Agent Roster

| Agent | Role | When to Use | Model |
|---|---|---|---|
| `libgdx-developer` | Developer | Implementing game features from issues | `opencode/big-pickle` |
| `libgdx-explorer` | Explorer | Researching codebase, finding patterns | `opencode/big-pickle` |
| `libgdx-planner` | Planner | Planning new features, drafting issues | `opencode/big-pickle` |
| `libgdx-tester` | Tester | Writing and running system tests | `opencode/big-pickle` |

## Using on Another Machine

1. **Copy or clone the project** — the agents and skills travel with the repo
2. **Ensure model access** — `opencode/big-pickle` must be configured in your opencode provider settings
3. **Run `opencode` from the project root** — agents are available immediately
4. **Select an agent** — use the agent selector or `@libgdx-developer`, `@libgdx-explorer`, etc.

### Model Configuration

The agents use `opencode/big-pickle`. Ensure your opencode provider can resolve this model. Check your global or project-level `opencode.json` for provider configuration.

If you need to use a different model, edit the `model:` field in each `.opencode/agents/libgdx-*.md` file.

## Customization

### Project-specific rules
Edit `AGENTS.md` in the project root. All agents reference it for project-specific conventions (ECS architecture, camera system, coding rules, etc.).

### Adding skills
Create a new directory in `.junie/skills/<skill-name>/SKILL.md` with frontmatter:
```markdown
---
name: my-skill
description: When to use this skill
---
```

### Modifying agents
Edit the `.md` files in `.opencode/agents/`. Changes take effect after restarting opencode.

## Troubleshooting

| Issue | Fix |
|---|---|
| Agents not appearing | Ensure you're in the project root and `.opencode/agents/` exists |
| Model not found | Check `opencode/big-pickle` is available in your provider config |
| Skills not loading | Verify `.junie/skills/` exists and `opencode.json` has `"skills": { "paths": [".junie/skills"] }` |
| Tests fail to run | Ensure Java 21+ is installed and `./gradlew test` works |
