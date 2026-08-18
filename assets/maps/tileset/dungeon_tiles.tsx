<?xml version="1.0" encoding="UTF-8"?>
<tileset version="1.10" tiledversion="1.12.2" name="dungeon_tiles" tilewidth="256" tileheight="257" tilecount="56" columns="0">
 <grid orientation="orthogonal" width="1" height="1"/>
 <tile id="0" type="Ground">
  <properties>
   <property name="oneWay" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/dungeon/01.png" width="128" height="128"/>
 </tile>
 <tile id="1" type="Ground">
  <image source="../gfx/tiles/dungeon/02.png" width="128" height="128"/>
 </tile>
 <tile id="2" type="Ground">
  <image source="../gfx/tiles/dungeon/02b.png" width="128" height="128"/>
 </tile>
 <tile id="3" type="Ground">
  <image source="../gfx/tiles/dungeon/02-broken.png" width="128" height="128"/>
 </tile>
 <tile id="4" type="Ground">
  <image source="../gfx/tiles/dungeon/02c.png" width="128" height="128"/>
 </tile>
 <tile id="5">
  <image source="../gfx/tiles/dungeon/03a.png" width="128" height="128"/>
 </tile>
 <tile id="6">
  <image source="../gfx/tiles/dungeon/03b.png" width="128" height="128"/>
 </tile>
 <tile id="7">
  <image source="../gfx/tiles/dungeon/04a.png" width="128" height="128"/>
 </tile>
 <tile id="8">
  <image source="../gfx/tiles/dungeon/04b.png" width="128" height="128"/>
 </tile>
 <tile id="9">
  <image source="../gfx/tiles/dungeon/04bb.png" width="128" height="128"/>
 </tile>
 <tile id="10">
  <image source="../gfx/tiles/dungeon/04bc.png" width="128" height="128"/>
 </tile>
 <tile id="11">
  <image source="../gfx/tiles/dungeon/bg-barrel.png" width="128" height="128"/>
 </tile>
 <tile id="12">
  <image source="../gfx/tiles/dungeon/bg-crate.png" width="128" height="128"/>
 </tile>
 <tile id="13">
  <properties>
   <property name="secret" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/dungeon/breakable.png" width="128" height="128"/>
 </tile>
 <tile id="14">
  <properties>
   <property name="oneWay" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/dungeon/bridge.png" width="128" height="128"/>
 </tile>
 <tile id="15">
  <image source="../gfx/tiles/dungeon/bridge-2.png" width="128" height="128"/>
  <objectgroup draworder="index" id="4">
   <object id="8" x="0" y="1" width="128" height="33"/>
   <object id="9" x="27" y="34" width="75" height="92"/>
  </objectgroup>
 </tile>
 <tile id="16">
  <image source="../gfx/tiles/dungeon/bridge-3.png" width="128" height="128"/>
 </tile>
 <tile id="17">
  <image source="../gfx/tiles/dungeon/bridge-4.png" width="128" height="128"/>
 </tile>
 <tile id="18">
  <image source="../gfx/tiles/dungeon/crate-3.png" width="256" height="257"/>
 </tile>
 <tile id="19">
  <image source="../gfx/tiles/dungeon/crystal-bottom-1.png" width="128" height="128"/>
 </tile>
 <tile id="20">
  <image source="../gfx/tiles/dungeon/crystal-bottom-2.png" width="128" height="128"/>
 </tile>
 <tile id="21" type="Door">
  <image source="../gfx/tiles/dungeon/door.png" width="128" height="256"/>
 </tile>
 <tile id="22">
  <image source="../gfx/tiles/dungeon/doorwall.png" width="128" height="128"/>
 </tile>
 <tile id="23">
  <image source="../gfx/tiles/dungeon/ladder.png" width="128" height="128"/>
 </tile>
 <tile id="24">
  <image source="../gfx/tiles/dungeon/ladder-broken.png" width="128" height="128"/>
 </tile>
 <tile id="25">
  <image source="../gfx/tiles/dungeon/pedestal.png" width="128" height="128"/>
 </tile>
 <tile id="26">
  <image source="../gfx/tiles/dungeon/platform-float.png" width="128" height="128"/>
 </tile>
 <tile id="27">
  <image source="../gfx/tiles/dungeon/platform-one-way.png" width="129" height="128"/>
 </tile>
 <tile id="28">
  <image source="../gfx/tiles/dungeon/rock.png" width="128" height="128"/>
 </tile>
 <tile id="29">
  <image source="../gfx/tiles/dungeon/signboard.png" width="128" height="128"/>
 </tile>
 <tile id="30">
  <image source="../gfx/tiles/dungeon/signboard-left.png" width="128" height="128"/>
 </tile>
 <tile id="31">
  <image source="../gfx/tiles/dungeon/signboard-right.png" width="128" height="128"/>
 </tile>
 <tile id="32">
  <properties>
   <property name="hazard" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/dungeon/spikes.png" width="128" height="128"/>
  <objectgroup draworder="index" id="2">
   <object id="1" x="0" y="96" width="128" height="32"/>
  </objectgroup>
 </tile>
 <tile id="37">
  <image source="../gfx/tiles/dungeon/water-middle.png" width="128" height="128"/>
 </tile>
 <tile id="38">
  <image source="../gfx/tiles/dungeon/water-surface.png" width="128" height="128"/>
 </tile>
 <tile id="39" type="Ground">
  <properties>
   <property name="oneWay" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/caves/01.png" width="128" height="128"/>
 </tile>
 <tile id="40" type="Ground">
  <properties>
   <property name="oneWay" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/caves/01alt.png" width="128" height="128"/>
 </tile>
 <tile id="41" type="Ground">
  <image source="../gfx/tiles/caves/02.png" width="128" height="128"/>
 </tile>
 <tile id="42" type="Ground">
  <image source="../gfx/tiles/caves/02alt.png" width="128" height="128"/>
 </tile>
 <tile id="43" type="Ground">
  <image source="../gfx/tiles/caves/02b-broken.png" width="128" height="128"/>
 </tile>
 <tile id="44">
  <image source="../gfx/tiles/caves/03a.png" width="128" height="128"/>
 </tile>
 <tile id="45">
  <image source="../gfx/tiles/caves/03b.png" width="128" height="128"/>
 </tile>
 <tile id="46">
  <image source="../gfx/tiles/caves/04a.png" width="129" height="129"/>
 </tile>
 <tile id="47">
  <image source="../gfx/tiles/caves/04b.png" width="129" height="129"/>
 </tile>
 <tile id="48">
  <image source="../gfx/tiles/caves/04bb.png" width="129" height="129"/>
 </tile>
 <tile id="49">
  <image source="../gfx/tiles/caves/04bc.png" width="129" height="129"/>
 </tile>
 <tile id="50">
  <properties>
   <property name="hazard" type="bool" value="true"/>
  </properties>
  <image source="../gfx/tiles/caves/bg-stalactite.png" width="128" height="128"/>
  <objectgroup draworder="index" id="5">
   <object id="4" x="0" y="0" width="128" height="32"/>
  </objectgroup>
 </tile>
 <tile id="51">
  <image source="../gfx/tiles/caves/bridge-3.png" width="128" height="129"/>
 </tile>
 <tile id="52">
  <image source="../gfx/tiles/caves/bridge-4.png" width="128" height="129"/>
 </tile>
 <tile id="53" type="Door">
  <image source="../gfx/tiles/caves/door.png" width="129" height="256"/>
 </tile>
 <tile id="54">
  <image source="../gfx/tiles/caves/pedestal.png" width="128" height="128"/>
 </tile>
 <tile id="56">
  <image source="../gfx/tiles/caves/bg-barrel.png" width="128" height="128"/>
 </tile>
 <tile id="57">
  <image source="../gfx/tiles/caves/platform-float.png" width="128" height="128"/>
 </tile>
 <tile id="58">
  <image source="../gfx/tiles/bg/pillar01.png" width="113" height="256"/>
 </tile>
 <tile id="59">
  <image source="../gfx/tiles/new.png" width="128" height="128"/>
 </tile>
 <tile id="60">
  <image source="../gfx/tiles/new2.png" width="128" height="128"/>
 </tile>
</tileset>
