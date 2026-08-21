<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.12.2" name="hazards" tilewidth="128" tileheight="128" tilecount="3" columns="2">
 <tile id="0">
  <properties>
   <property name="hazard" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/spikes.png" width="128" height="128"/>
 </tile>
 <tile id="1">
  <properties>
   <property name="hazard" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/lava.png" width="128" height="128"/>
 </tile>
 <tile id="2" type="Floating">
  <properties>
   <property name="amplitudeX" value="0"/>
   <property name="amplitudeY" type="int" value="0"/>
   <property name="speed" value="1"/>
   <property name="type" value="platform"/>
  </properties>
  <image source="../gfx/tiles/dungeon/platform-float.png" width="128" height="128"/>
 </tile>
</tileset>
