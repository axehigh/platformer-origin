<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.12.2" name="hazards" tilewidth="129" tileheight="128" tilecount="5" columns="2">
 <grid orientation="orthogonal" width="128" height="128"/>
 <tile id="2" type="Floating">
  <properties>
   <property name="amplitudeX" type="float" value="0"/>
   <property name="amplitudeY" type="float" value="0"/>
   <property name="speed" type="float" value="1"/>
   <property name="type" value="platform"/>
  </properties>
  <image source="../gfx/tiles/dungeon/platform-float.png" width="128" height="128"/>
 </tile>
 <tile id="4" type="trap">
  <properties>
   <property name="cooldown" type="float" value="1.5"/>
   <property name="damage" type="int" value="1"/>
   <property name="direction" value="up"/>
   <property name="duration" type="int" value="2"/>
   <property name="pulseSpeed" type="float" value="2"/>
   <property name="trapType" value="flame"/>
  </properties>
  <image source="../gfx/tiles/lava.png" width="128" height="128"/>
 </tile>
 <tile id="5" type="trap">
  <properties>
   <property name="damage" type="int" value="1"/>
   <property name="direction" value="down"/>
   <property name="interval" type="float" value="3"/>
   <property name="speed" type="int" value="100"/>
   <property name="trapType" value="acidDrop"/>
  </properties>
  <image source="../gfx/hazards/acid_tube3.png" width="128" height="128"/>
 </tile>
 <tile id="6">
  <image source="../gfx/tiles/caves/platform-float.png" width="128" height="128"/>
 </tile>
 <tile id="7" type="crumble">
  <properties>
   <property name="crumble" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/lava/platform-one-way.png" width="129" height="128"/>
 </tile>
</tileset>
