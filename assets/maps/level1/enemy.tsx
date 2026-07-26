<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.12.2" name="enemy" tilewidth="256" tileheight="256" tilecount="3" columns="0">
 <grid orientation="orthogonal" width="1" height="1"/>
 <tile id="2" type="enemy">
  <image source="../gfx/enemies/skeleton01_idle1.png" width="128" height="128"/>
 </tile>
 <tile id="3" type="enemy">
  <image source="../gfx/enemies/big_knight01_idle1.png" width="256" height="256"/>
 </tile>
 <tile id="4" type="enemy">
  <properties>
   <property name="enemyType" value="shooter"/>
  </properties>
  <image source="../gfx/enemies/ghost01_idle1.png" width="128" height="128"/>
 </tile>
</tileset>
