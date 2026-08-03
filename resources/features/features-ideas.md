# Agent Instructions: Features ideas

# TODO
- Chest opening, should not disappear but change to a different tile, or change sprite.
- resume a screen, all sprites disappears
# effects
- when opening a chest, display smoke or any other particle effect.
- when coins drop, it should be proper coins, not the simple coin sprite.
- when jumping down, should have an effect that it shrinks a bit, and back up. 
- Smoke effect when landing over a certain distance, also for jumping. 
# Code
- Need to remove calls to version over 8 in java, instead use libgdx lists, arrays etc.
- Collision layer, since we wont have strict square collision, ex half tiles etc. How do we do this best?
- Should components have public values? Should the final values be in component or in a game constant?

### Game Loop
- Dead, has 3 tries each have 3 base lives. One dead, will reset to start of level.

### Level and Tiled Map
- Need to update the tiled set and add gfx and custom properties and custom tiles perhaps. 
- Templates
- Level design: Should have tileset outside of the map instead of inside.

### Moving between Maps
- Reach the end of the level, moving to next level or level selector.
- 

### Terrain
- keys and doors
- levers that opens doors
- teleporters
- traps
- crumbling walls

