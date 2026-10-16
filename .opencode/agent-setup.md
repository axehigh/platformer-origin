Having access to Big Pickle completely redefines your multi-agent architecture. Big Pickle is a massive 200,000-token context reasoning model featuring deep multi-step problem solving and deliberate analysis. Developers consistently report that it outperforms heavyweights like Claude Code for complex, multi-file codebases and tricky API logic.
Because libGDX forces you to balance structural logic (rendering pipelines, cross-platform lifecycles) with memory performance, you should slot Big Pickle right into the center of your setup:

⚙️ Revised Multi-Agent Topology for libGDX

                 ┌───────────────────────────┐
                 │    @planner / @architect  │
                 │        BIG PICKLE         │  <-- Deep Structural Analysis
                 └─────────────┬─────────────┘
                               │ (Dispatches sub-tasks)
         ┌─────────────────────┼─────────────────────┐
         ▼                     ▼                     ▼
┌───────────┐         ┌───────────┐         ┌───────────┐
│  @coder   │         │  @scanner │         │  @critic  │
│ DEEPSEEK  │         │  LONGCAT  │         │ NEMOTRON  │
└───────────┘         └───────────┘         └───────────┘

1. The Core Architect / Lead Agent: @planner → Big Pickle

• Role: Architectural blueprints, complex math handling, and framework integration contracts.
• libGDX Match: Use it to blueprint your overall engine state machines, design decoupled screen managers, orchestrate your entity-component system (ECS like Ashley), or handle structural shader integrations. Big Pickle won't lose track of your codebase context even when working across dozens of interconnected classes.

2. The Speedy Constructor: @make or @coder → DeepSeek V4.1 Flash Free

• Role: Rapid, iterative class implementations.
• libGDX Match: Once Big Pickle passes down a clear structural layout or interface contract, let DeepSeek write out the manual boilerplate. It's exceptionally fast and accurate for implementing standard logic loops, input listeners, and data-heavy mathematical formulas.

3. The Repository Scanner: @review or @asset-manager → LongCat 2.5 Preview Free

• Role: Multi-file reading, system dependency auditing, and asset mapping.
• libGDX Match: Because it has a massive 1M token context window, use LongCat to scan across your entire packed texture catalog (.atlas), config schemas, or massive multi-layer level scripts to feed clean asset lists into your other agents.

4. The Visual Quality Auditor: @check or @designer → Nemotron 3.5 Super VL Free

• Role: GUI verification and spatial asset inspection.
• libGDX Match: Leave it to evaluate UI layouts generated via scene2d.ui skins, auditing camera viewports, or reviewing wireframe outputs for alignment glitches.
Would you like a sample OpenCode custom agent workflow file (multi-agent-workflow.md) mapping out exactly how Big Pickle should hand down tasks to your DeepSeek sub-agents?
