# Agent Instructions: Features ideas

### Moving between Maps
- Reach the end of the level, moving to next level or level selector.
- 

### Terrain
- Terrain obstacles (acid, fire, spikes)
- Moving platforms, side to side, up and down
- keys and doors
- levers that opens doors
- teleporters
- traps
- crumbling walls

### Player Movements

### Enemies


### Level Map Ideas:
Please implement a Room-Based Camera and Entity management system using our Tiled map setup.

1. The system should look for an Object Layer named "Rooms" containing custom rectangle shapes. Each rectangle defines a distinct room zone of variable size.
2. The player controller or game manager must track which Room rectangle the player is currently inside.
3. The camera must dynamically clamp its viewport boundaries to match the edges of the active Room rectangle. If a room matches the screen resolution, the camera remains static. If a room is larger than the viewport, it allows continuous scrolling but remains clamped within the room's edges.
4. Let the system manage enemy entities based on these rooms, ensuring enemies only activate/spawn when their corresponding room becomes the active zone.

Grill-Me on the feature. 
