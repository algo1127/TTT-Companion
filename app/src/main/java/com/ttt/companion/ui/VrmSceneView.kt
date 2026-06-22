package com.ttt.companion.ui

import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.model.ModelInstance
import java.io.File

@Composable
fun VrmSceneView(
    modelPath: String?,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    onLoaded: () -> Unit,
    onError: (String) -> Unit
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    
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

    // Check 4: Explicit Camera and Light configuration using Sceneview's Compose helpers
    val cameraNode = rememberCameraNode(engine) {
        position = Position(x = 0f, y = 1.1f, z = 1.3f)
        lookAt(Position(x = 0f, y = 1.1f, z = 0f))
    }
    
    val mainLightNode = rememberMainLightNode(engine) {
        intensity = 100_000f
    }

    SceneView(
        modifier = modifier,
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        mainLightNode = mainLightNode,
        isOpaque = true
    ) {
        modelInstance?.let { instance ->
            // Add the model to the scene using the DSL's ModelNode
            ModelNode(
                modelInstance = instance,
                scaleToUnits = 1.0f,
                position = Position(x = 0f, y = -1.2f, z = -1.5f),
                apply = {
                    // Primitive lip sync logic: oscillates morph targets while speaking
                    if (isSpeaking) {
                        val t = System.currentTimeMillis() / 1000f
                        val value = (Math.sin(t.toDouble() * 12).toFloat() * 0.4f + 0.4f)
                        setMorphWeights(floatArrayOf(value))
                    } else {
                        setMorphWeights(floatArrayOf(0f))
                    }
                }
            )
        }
    }
}
