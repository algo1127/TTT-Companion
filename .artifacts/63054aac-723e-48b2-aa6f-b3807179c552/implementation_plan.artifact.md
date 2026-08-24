# Replace Filament with Godot Engine (Zero-Editor Pipeline)

This plan outlines the replacement of the Filament-based `SceneView` with a hosted Godot Engine instance to render VRM avatars. We will follow a "Zero-Editor" approach where Godot acts as a runtime renderer driven by Kotlin instructions.

## User Review Required

> [!IMPORTANT]
> **Godot Engine AAR**: This plan assumes we will use the `org.godotengine:godot` Maven dependency. If a specific custom build `.aar` is required, you will need to place it in the `app/libs` directory manually.
> **godot-vrm Plugin**: You will need to provide the `addons/vrm` folder content. I will create the directory structure in `assets/`, but the plugin logic must be present for Godot to import VRM files.

## Proposed Changes

### Build Configuration

#### [MODIFY] [libs.versions.toml](file:///C:/Users/victo/AndroidStudioProjects/TTTCompanion/gradle/libs.versions.toml)
- Add Godot version and library definition.
- Remove SceneView definitions.

#### [MODIFY] [app/build.gradle.kts](file:///C:/Users/victo/AndroidStudioProjects/TTTCompanion/app/build.gradle.kts)
- Swap SceneView dependency for Godot.
- Add `aaptOptions` to prevent compression of Godot assets (`.pck`, `.gd`, `.gdc`, `.gdshader`).

---

### Assets (Godot Runtime)

#### [NEW] [project.godot](file:///C:/Users/victo/AndroidStudioProjects/TTTCompanion/app/src/main/assets/project.godot)
- A minimal Godot project file to initialize the engine.

#### [NEW] [main.gd](file:///C:/Users/victo/AndroidStudioProjects/TTTCompanion/app/src/main/assets/main.gd)
- The core Godot script that:
    - Loads the `godot-vrm` importer.
    - Exposes a `load_vrm(path)` function.
    - Handles lip-sync and blinking via signals/methods called from Kotlin.

#### [NEW] `addons/vrm/` directory
- The location for the `godot-vrm` plugin files.

---

### UI & Bridge Logic

#### [MODIFY] [MainActivity.kt](file:///C:/Users/victo/AndroidStudioProjects/TTTCompanion/app/src/main/java/com/ttt/companion/MainActivity.kt)
- Change base class to `FragmentActivity` (if necessary for `FragmentContainerView` support).
- Implement `GodotHost` to manage the engine lifecycle.

#### [NEW] [GodotVrmView.kt](file:///C:/Users/victo/AndroidStudioProjects/TTTCompanion/app/src/main/java/com/ttt/companion/ui/GodotVrmView.kt)
- A new Composable that hosts the `GodotFragment`.
- Implements the bridge to send `isSpeaking` and `vrmUrl` updates to the Godot engine.

#### [DELETE] [VrmSceneView.kt](file:///C:/Users/victo/AndroidStudioProjects/TTTCompanion/app/src/main/java/com/ttt/companion/ui/VrmSceneView.kt)
- Remove the old Filament-based implementation.

#### [MODIFY] [VrmScreen.kt](file:///C:/Users/victo/AndroidStudioProjects/TTTCompanion/app/src/main/java/com/ttt/companion/ui/VrmScreen.kt)
- Replace `VrmSceneView` usage with `GodotVrmView`.

## Verification Plan

### Automated Tests
- Build the project to verify Godot dependency resolution.
- Verify that `aaptOptions` are correctly configured via a build log check.

### Manual Verification
- Deploy to a device.
- Verify the Godot splash screen appears (initialization).
- Select a VRM and verify it loads via the `godot-vrm` importer.
- Check lip-sync reactivity when the AI is "speaking".
