package com.ttt.companion.ui

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.model.ModelInstance
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.gesture.CameraGestureDetector
import java.io.File

@Composable
fun VrmSceneView(
    modelPath: String?,
    isSpeaking: Boolean,
    isLocked: Boolean,
    initialCameraData: FloatArray?,
    modifier: Modifier = Modifier,
    onLoaded: () -> Unit,
    onError: (String) -> Unit,
    onCameraMoved: (px: Float, py: Float, pz: Float, tx: Float, ty: Float, tz: Float) -> Unit
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)

    // Ensure the frame loop always has access to the latest speaking state
    // without re-running the 'apply' block of the ModelNode.
    val currentIsSpeaking by rememberUpdatedState(isSpeaking)

    // Check 1: Detailed logging for the VRM file path and its availability
    modelPath?.let { path ->
        SideEffect {
            val file = File(path)
            Log.e("SCENEVIEW", "modelPath=$path exists=${file.exists()} size=${file.length()}")
        }
    }

    // We manage the model instance manually to capture the load result and errors (Check 2)
    var modelInstance by remember { mutableStateOf<ModelInstance?>(null) }

    LaunchedEffect(modelPath) {
        if (modelPath == null) {
            Log.e("SCENEVIEW", "modelPath is null")
            modelInstance = null
            return@LaunchedEffect
        }

        // Check 2: Ensure correct URI format and use loadModelInstanceAsync for result tracking
        val fileLocation = if (modelPath.startsWith("/")) "file://$modelPath" else modelPath

        modelLoader.loadModelInstanceAsync(
            fileLocation = fileLocation,
            onResult = { instance ->
                Log.e("SCENEVIEW", "onResult instance=$instance")
                if (instance != null) {
                    modelInstance = instance
                    onLoaded()
                } else {
                    onError("null instance")
                }
            }
        )
    }

    // Camera setup with persistent data support
    val cameraNode = rememberCameraNode(engine) {
        if (initialCameraData != null) {
            position = Position(initialCameraData[0], initialCameraData[1], initialCameraData[2])
            lookAt(Position(initialCameraData[3], initialCameraData[4], initialCameraData[5]))
        } else {
            position = Position(x = 0f, y = 1.1f, z = 1.3f)
            lookAt(Position(x = 0f, y = 1.1f, z = 0f))
        }
    }

    // Use a derived state to keep track of the latest position and target for the manipulator
    var currentEye by remember { mutableStateOf(cameraNode.position) }
    var currentTarget by remember {
        mutableStateOf(if (initialCameraData != null) {
            Position(initialCameraData[3], initialCameraData[4], initialCameraData[5])
        } else {
            Position(x = 0f, y = 1.1f, z = 0f)
        })
    }

    val cameraManipulator = if (isLocked) null else rememberCameraManipulator(
        orbitHomePosition = currentEye,
        targetPosition = currentTarget
    )

    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 100_000f
    }

    // Used to detect when the camera stops moving and we should save
    var lastSavedPos by remember { mutableStateOf(cameraNode.position) }

    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        cameraManipulator = cameraManipulator,
        mainLightNode = mainLightNode,
        isOpaque = true,
        onFrame = { _ ->
            // Update current state from the manipulator if active
            if (cameraManipulator is CameraGestureDetector.DefaultCameraManipulator) {
                try {
                    val manipulatorField = CameraGestureDetector.DefaultCameraManipulator::class.java.getDeclaredField("manipulator")
                    manipulatorField.isAccessible = true
                    val filamentManipulator = manipulatorField.get(cameraManipulator) as com.google.android.filament.utils.Manipulator

                    val eye = FloatArray(3)
                    val target = FloatArray(3)
                    val up = FloatArray(3)
                    filamentManipulator.getLookAt(eye, target, up)

                    currentEye = Position(eye[0], eye[1], eye[2])
                    currentTarget = Position(target[0], target[1], target[2])
                } catch (e: Exception) {
                    // Fallback to node position if reflection fails
                    currentEye = cameraNode.position
                }
            }

            // Save position if it changed significantly (and we aren't locked)
            if (!isLocked) {
                if (Math.abs(currentEye.x - lastSavedPos.x) > 0.01f ||
                    Math.abs(currentEye.y - lastSavedPos.y) > 0.01f ||
                    Math.abs(currentEye.z - lastSavedPos.z) > 0.01f) {

                    onCameraMoved(currentEye.x, currentEye.y, currentEye.z, currentTarget.x, currentTarget.y, currentTarget.z)
                    lastSavedPos = currentEye
                }
            }
        }
    ) {
        modelInstance?.let { instance ->
            // Add the model to the scene using the DSL's ModelNode
            ModelNode(
                modelInstance = instance,
                scaleToUnits = 1.0f,
                position = Position(x = 0f, y = -1.2f, z = -1.5f),
                apply = {
                    // Find the index of mouth-related morph targets.
                    // VRM standard uses A, I, U, E, O.
                    val mouthIndices = renderableNodes.map { node ->
                        val targetNames = node.morphTargetNames
                        val indices = targetNames.mapIndexedNotNull { i, name ->
                            val n = name.lowercase()
                            if (n == "a" || n == "aiueo_a" || n.contains("mouth_a") || 
                                n == "jawopen" || n == "mouthopen" || n == "mouthopen1") i else null
                        }
                        node to indices
                    }.filter { it.second.isNotEmpty() }

                    // Find morph indices for blinking
                    val blinkIndices = renderableNodes.map { node ->
                        val targetNames = node.morphTargetNames
                        val indices = targetNames.mapIndexedNotNull { i, name ->
                            val n = name.lowercase()
                            if (n == "eyeblinkleft" || n == "eyeblinkright" || n == "eyeblink") i else null
                        }
                        node to indices
                    }.filter { it.second.isNotEmpty() }

                    var startTimeNanos = -1L
                    var nextBlinkTime = 0f
                    var blinkStartTime = -1f

                    // Move logic to onFrame so it updates at 60fps
                    onFrame = { frameTimeNanos ->
                        if (startTimeNanos == -1L) startTimeNanos = frameTimeNanos
                        val t = (frameTimeNanos - startTimeNanos) / 1_000_000_000f

                        // ── Lip Sync ─────────────────────────────────────────
                        if (currentIsSpeaking) {
                            val osc1 = Math.sin(t.toDouble() * 15.0).toFloat() * 0.25f
                            val osc2 = Math.sin(t.toDouble() * 7.0).toFloat() * 0.25f
                            val value = (osc1 + osc2 + 0.4f).coerceIn(0f, 0.9f)
                            
                            mouthIndices.forEach { (node, indices) ->
                                val weights = FloatArray(node.morphTargetNames.size)
                                indices.forEach { idx -> weights[idx] = value }
                                node.setMorphWeights(weights, 0)
                            }
                        } else {
                            mouthIndices.forEach { (node, indices) ->
                                val weights = FloatArray(node.morphTargetNames.size)
                                indices.forEach { idx -> weights[idx] = 0f }
                                node.setMorphWeights(weights, 0)
                            }
                        }

                        // ── Blinking ─────────────────────────────────────────
                        // Initialize next blink time
                        if (nextBlinkTime == 0f) {
                            nextBlinkTime = t + (2f + Math.random().toFloat() * 4f)
                        }

                        if (t > nextBlinkTime && blinkStartTime == -1f) {
                            blinkStartTime = t
                        }

                        if (blinkStartTime != -1f) {
                            val blinkProgress = (t - blinkStartTime) / 0.12f // Fast 120ms blink
                            if (blinkProgress > 1f) {
                                blinkStartTime = -1f
                                nextBlinkTime = t + (2f + Math.random().toFloat() * 5f) // Next blink in 2-7s
                                blinkIndices.forEach { (node, indices) ->
                                    val weights = FloatArray(node.morphTargetNames.size)
                                    indices.forEach { idx -> weights[idx] = 0f }
                                    node.setMorphWeights(weights, 0)
                                }
                            } else {
                                // Smooth triangle wave for blink: 0 -> 1 -> 0
                                val blinkValue = if (blinkProgress < 0.5f) blinkProgress * 2f else 2f - (blinkProgress * 2f)
                                blinkIndices.forEach { (node, indices) ->
                                    val weights = FloatArray(node.morphTargetNames.size)
                                    indices.forEach { idx -> weights[idx] = blinkValue }
                                    node.setMorphWeights(weights, 0)
                                }
                            }
                        }
                    }
                }
            )
        }
    }
}
