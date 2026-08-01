# Agent Instructions: Features ideas

# TODO
- Need to remove calls to version over 8 in java, instead use libgdx lists, arrays etc.
- Collision layer, since we wont have strict square collision, ex half tiles etc. How do we do this best?
- Should components have public values? Should the final values be in component or in a game constant?
- When jumping, walls should not reset jump count. Walls should not be climbable, but only ladders and vines.
- 

### Game Loop
- Dead, has 3 tries each have 3 base lives. One dead, will reset to start of level.

### Level and Tiled Map
- Need to update the tiled set and add gfx and custom properties and custom tiles perhaps. 
- Templates
- Level design: Should have tileset outside of the map instead of inside.
- 

### Moving between Maps
- Reach the end of the level, moving to next level or level selector.
- 

### Terrain
- Terrain obstacles (acid, fire, spikes)
- keys and doors
- levers that opens doors
- teleporters
- traps
- crumbling walls

### Player Movements
Should be able to double jump. Should be able to climb certain walls, not all walls but don't have gfx for that, 
so perhaps instead climb ladders or wines is better. Remove possibility to clim walls along the side.


### Enemies

### Skills to generate
## Room generator
Based on this request and scripts used, create a skill named tmx-map-generator that generates a tmx map for this game,
with a defined number of rooms, default 3 rooms, unless otherwise specified, add some enemies and items.

grill me for this skill.

create this so that is running as a sub-agent? If there any advantages to that.
