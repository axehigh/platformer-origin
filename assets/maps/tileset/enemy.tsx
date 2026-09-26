<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.11.2" name="enemy" tilewidth="256" tileheight="256" tilecount="7" columns="0">
 <grid orientation="orthogonal" width="1" height="1"/>
 <tile id="2" type="enemy">
  <properties>
   <property name="enemyType" value="skeleton"/>
   <property name="loot" value=""/>
   <property name="size" value="medium"/>
  </properties>
  <image source="../gfx/enemies/skeleton01_idle1.png" width="128" height="128"/>
 </tile>
 <tile id="3" type="enemy">
  <properties>
   <property name="enemyType" value="knight"/>
   <property name="loot" value=""/>
   <property name="size" value="medium"/>
  </properties>
  <image source="../gfx/enemies/big_knight01_idle1.png" width="256" height="256"/>
 </tile>
 <tile id="4" type="enemy">
  <properties>
   <property name="enemyType" value="ghost"/>
   <property name="loot" value=""/>
   <property name="patrolRange" type="float" value="0"/>
   <property name="size" value="medium"/>
  </properties>
  <image source="../gfx/enemies/ghost01_idle1.png" width="128" height="128"/>
 </tile>
 <tile id="5" type="enemy">
  <properties>
   <property name="detectionRange" type="float" value="3"/>
   <property name="enemyType" value="shooter"/>
   <property name="loot" value=""/>
   <property name="patrolRange" type="float" value="2"/>
   <property name="shootRange" type="float" value="4"/>
   <property name="size" value="medium"/>
  </properties>
  <image source="../gfx/enemies/spider_0spider.png" width="128" height="128"/>
 </tile>
 <tile id="6" type="enemy">
  <properties>
   <property name="enemyType" value="flyer"/>
   <property name="loot" value=""/>
   <property name="size" value="medium"/>
  </properties>
  <image source="../gfx/enemies/mosquito_0mosquito.png" width="128" height="128"/>
 </tile>
 <tile id="7" type="enemy">
  <properties>
   <property name="enemyType" value="walker"/>
   <property name="loot" value=""/>
   <property name="size" value="medium"/>
  </properties>
  <image source="../gfx/enemies/goblin_0goblin.png" width="128" height="128"/>
 </tile>
 <tile id="8" type="enemy">
  <properties>
   <property name="enemyType" value="knight"/>
   <property name="loot" value=""/>
   <property name="size" value="medium"/>
  </properties>
  <image source="../gfx/enemies/goblin_large.png" width="128" height="104"/>
 </tile>
</tileset>
