AI Skills:

New-Item -ItemType SymbolicLink -Path "c:\skuld\dev_olona\libgdx\platformer-origin\.opencode\skills" -Target "c:\skuld\dev_olona\libgdx\platformer-origin\.junie\skills"

Map Generator:
python3 /Users/lona/dev/libgdx/origin/.junie/skills/tmx-map-generator/scripts/generate_tmx.py  --rooms 3 --platforms 3 --seed 42 --tilesets-dir . --out ../world_demo/my_map.tmx
