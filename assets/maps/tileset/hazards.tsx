<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.12.2" name="hazards" tilewidth="128" tileheight="128" tilecount="3" columns="2">
 <tile id="2" type="Floating">
  <properties>
   <property name="amplitudeX" value="0"/>
   <property name="amplitudeY" type="int" value="0"/>
   <property name="speed" value="1"/>
   <property name="type" value="platform"/>
  </properties>
  <image source="../gfx/tiles/dungeon/platform-float.png" width="128" height="128"/>
 </tile>
 <tile id="3" type="trap">
  <properties>
   <property name="damage" type="int" value="1"/>
   <property name="direction" value="down"/>
   <property name="interval" type="float" value="2"/>
   <property name="speed" type="int" value="200"/>
   <property name="trapType" value="acidDrop"/>
  </properties>
  <image source="../gfx/hazards/acid_trap.png" width="128" height="128"/>
  <objectgroup draworder="index" id="2">
   <object id="1" x="64" y="93">
    <point/>
   </object>
  </objectgroup>
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
</tileset>
