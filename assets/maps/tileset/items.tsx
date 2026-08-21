<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.12.2" name="items" tilewidth="128" tileheight="156" tilecount="23" columns="0">
 <grid orientation="orthogonal" width="1" height="1"/>
 <tile id="1">
  <image source="../gfx/items/Apple.png" width="128" height="128"/>
 </tile>
 <tile id="2">
  <image source="../gfx/items/Bubble.png" width="128" height="128"/>
 </tile>
 <tile id="3" type="chest">
  <image source="../gfx/items/Chest_01_Locked.png" width="128" height="128"/>
 </tile>
 <tile id="4" type="chest_open">
  <image source="../gfx/items/Chest_01_Unlocked.png" width="128" height="128"/>
 </tile>
 <tile id="5" type="chest_elite">
  <image source="../gfx/items/Chest_02_Locked.png" width="128" height="128"/>
 </tile>
 <tile id="6" type="chest_elite_open">
  <image source="../gfx/items/Chest_02_Unlocked.png" width="128" height="128"/>
 </tile>
 <tile id="7" type="coin">
  <image source="../gfx/items/Coin_01.png" width="128" height="128"/>
 </tile>
 <tile id="8" type="coin">
  <image source="../gfx/items/Coin_02.png" width="128" height="128"/>
 </tile>
 <tile id="9" type="coin">
  <image source="../gfx/items/Coin_03.png" width="128" height="128"/>
 </tile>
 <tile id="10" type="coin">
  <image source="../gfx/items/Coin_04.png" width="128" height="128"/>
 </tile>
 <tile id="11" type="coin">
  <image source="../gfx/items/Coin_05.png" width="128" height="128"/>
 </tile>
 <tile id="12" type="coin">
  <image source="../gfx/items/Coin_06.png" width="128" height="128"/>
  <animation>
   <frame tileid="7" duration="100"/>
   <frame tileid="8" duration="100"/>
   <frame tileid="9" duration="100"/>
   <frame tileid="10" duration="100"/>
   <frame tileid="11" duration="100"/>
   <frame tileid="12" duration="100"/>
  </animation>
 </tile>
 <tile id="13">
  <image source="../gfx/items/Diamond.png" width="128" height="128"/>
 </tile>
 <tile id="14" type="key">
  <image source="../gfx/items/Key_01.png" width="128" height="64"/>
 </tile>
 <tile id="15" type="key_elite">
  <image source="../gfx/items/Key_02.png" width="128" height="64"/>
 </tile>
 <tile id="16" type="heart">
  <image source="../gfx/items/Life.png" width="128" height="128"/>
 </tile>
 <tile id="17">
  <properties>
   <property name="effect" value="light"/>
  </properties>
  <image source="../gfx/items/Light.png" width="128" height="128"/>
  <objectgroup draworder="index" id="2">
   <object id="1" x="61" y="59">
    <point/>
   </object>
  </objectgroup>
 </tile>
 <tile id="18" type="star">
  <image source="../gfx/items/Star.png" width="128" height="128"/>
 </tile>
 <tile id="20">
  <properties>
   <property name="effect" value="light"/>
  </properties>
  <image source="../gfx/tiles/bg/torch.png" width="128" height="156"/>
  <objectgroup draworder="index" id="2">
   <object id="3" x="60" y="51">
    <point/>
   </object>
  </objectgroup>
 </tile>
 <tile id="25">
  <properties>
   <property name="potionType" value="Speed"/>
   <property name="type" value="potion"/>
  </properties>
  <image source="../gfx/tiles/items/fish.png" width="53" height="22"/>
 </tile>
 <tile id="26">
  <properties>
   <property name="potionType" value="Healing"/>
   <property name="type" value="potion"/>
  </properties>
  <image source="../gfx/tiles/items/heart.png" width="101" height="101"/>
 </tile>
 <tile id="27">
  <properties>
   <property name="potionType" value="Strength"/>
   <property name="type" value="potion"/>
  </properties>
  <image source="../gfx/tiles/items/meat.png" width="101" height="101"/>
 </tile>
 <tile id="28">
  <properties>
   <property name="potionType" value="Invulnerability"/>
   <property name="type" value="potion"/>
  </properties>
  <image source="../gfx/tiles/items/orange.png" width="100" height="100"/>
 </tile>
</tileset>
