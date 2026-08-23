# Fix VRM Lip Sync Logic

The current lip sync implementation in `VrmSceneView` fails to animate because the oscillation logic is placed inside the `apply` block of `ModelNode`, which only runs during initialization or when the model instance changes. Additionally, it targets morph target index 0, which is typically not the mouth-open shape for VRM models.

## Proposed Changes

### UI Component

#### [MODIFY] [VrmSceneView.kt](file:///C:/Users/victo/AndroidStudioProjects/TTTCompanion/app/src/main/java/com/ttt/companion/ui/VrmSceneView.kt)
- Use `rememberUpdatedState` to capture the `isSpeaking` state for the frame loop.
- Move the lip sync oscillation logic from the `ModelNode` `apply` block to its `onFrame` callback.
- Update `setMorphWeights` to target VRM standard mouth shapes (indices 1-5).

## Verification Plan

### Manual Verification
- Deploy the app to a device.
- Send a message to the AI.
- Observe the 3D avatar while the AI speaks: the mouth should oscillate between open and closed.
- Verify the mouth remains closed when the AI is not speaking.
