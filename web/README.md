# ORIGIN — marketing site

Plain static site for **ORIGIN** (the libGDX / Ashley hack-and-slash), published
at <https://www.olona.no/origin/>.

* No framework, no bundler, no build step. Open `index.html` and it works.
* All paths are relative, so it runs from any sub-path (that is how it is
  deployed) and straight off the file system.
* `play/` is the GWT browser build. It is **generated** — see below.

## Layout

| Path | What it is |
| --- | --- |
| `index.html` | The whole page. Every section is in this one file. |
| `css/styles.css` | Design system: dark dungeon palette, gold trim, one file. |
| `js/main.js` | Gallery, lightbox, mobile menu, sprite icons, reveals, trailer, signup. |
| `assets/brand/` | `splash_text.png` — the ORIGIN wordmark, copied from the game's splash. |
| `assets/art/` | Splash/background art copied from the game's own `assets/splash/`. |
| `assets/sprites/` | `origin-game.png` — the game atlas, used as a sprite sheet for the feature and bestiary icons (see `SPRITES` in `js/main.js`). |
| `assets/tiles/` | A few dungeon tiles used as small icons. |
| `img/shots/` | Screenshots. See `img/shots/README.md`. |
| `media/` | Trailer, when there is one (`origin-trailer.mp4`). |
| `favicon.svg` | Pixel sword. |
| `play/` | The playable GWT build. Generated, not committed. |

## Publishing the playable build

```bash
gradlew :html:distToWeb
```

That runs the normal `:html:dist` and copies the result into `play/`, so the
"Play in browser" buttons always point at a real build. Without it the buttons
land on a 404 — the rest of the site is unaffected.

`gradlew :html:dist` on its own leaves the build in `html/build/dist`.

## Screenshots

Press **P** in game (or *Take Screenshot* in the pause menu) to write a PNG of
the current frame, drop it in `img/shots/` under one of the names in the
`SHOTS` array at the top of `js/main.js`, and the gallery picks it up. Missing
files render as labelled placeholders — the page never shows a broken image.

Until you add them, the browser console logs one 404 per missing shot. That is
the placeholders doing their job, not a bug; it goes quiet as the real
screenshots land.

## Art

`assets/` holds a copy of a handful of the game's own files (the splash screens,
the wordmark, the sprite atlas, two dungeon tiles) — roughly 600 KB. The sprite
icons in the feature cards and bestiary are cropped out of `origin-game.png` by
`main.js`; the crop rectangles live in the `SPRITES` object at the top of that
file. They were taken from the atlas and checked against the PNG's alpha bounds,
so they line up — if you swap the atlas for a new one, re-check them.

## Things waiting on the site owner

Search the files for `TODO`:

* `TODO(stores)` — the App Store / Google Play buttons are disabled
  placeholders; swap in the real URLs and drop the `data-comingsoon` attribute.
* `TODO(trailer)` — drop `media/origin-trailer.mp4` in; the hero frame becomes
  a player by itself.
* `TODO(list)` — the newsletter form has no provider wired up and falls back to
  a prefilled mail draft. Point it at a real list or delete the section.
* `TODO(analytics)` — no tracker is loaded. If you add one, keep it in one
  clearly marked place and pick something cookieless.
* `TODO(press)` — the canonical/OG URLs assume `https://www.olona.no/origin/`.

## Deploying

The whole `web/` folder is the document root. The `play/` subfolder is excluded
in `robots.txt` so search engines index the marketing page, not the 21 MB build.
