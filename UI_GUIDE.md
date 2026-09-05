# UI Skin Configuration and Scaling Guide

## 1. Skin Configuration
LibGDX skins (`Skin`) load assets from a `skin.json` (or similar). The `.scmp` file is a tool-specific format (likely for a UI editor like `SkinComposer`) that compiles into the actual `skin.json` and `atlas` that the game uses.

To support different configurations (e.g., desktop vs. mobile), you should avoid hardcoding paths or sizes in the final `skin.json`. Instead:
- Use **multi-resolution atlases**: Keep separate folders for `desktop` (1x, 2x) and `mobile` (higher resolution assets if needed).
- **Programmatic Loading**: In your `AssetManager` initialization, detect the platform/density and load the appropriate folder:
  ```java
  String skinPath = Gdx.app.getType() == Application.ApplicationType.Android ? "skin/mobile/" : "skin/desktop/";
  manager.load(skinPath + "ui.json", Skin.class);
  ```

## 2. Managing Font Scales
Instead of hardcoding font scales in Java:
- **Define Named Styles in JSON**: Define different styles in your `skin.json` for different sizes:
  ```json
  {
    "com.badlogic.gdx.scenes.scene2d.ui.Label$LabelStyle": {
      "small": { "font": "default-font", "fontColor": "white" },
      "body": { "font": "default-font", "fontColor": "white" },
      "title": { "font": "default-font", "fontColor": "yellow" }
    }
  }
  ```
- **Apply Scale via Style**: When creating a `Label` in Java:
  ```java
  // Instead of setFontScale()
  Label label = new Label("Text", skin, "small");
  ```
- **Dynamic Scaling**: If you need to support different screen densities, use `FreeTypeFontGenerator` to generate fonts at runtime based on the screen height, rather than scaling pre-rendered font textures.
