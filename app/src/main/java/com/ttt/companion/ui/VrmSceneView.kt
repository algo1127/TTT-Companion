package com.ttt.companion.ui

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberMainLightNode
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.node.ModelNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCameraManipulator
import com.google.android.filament.Engine
import dev.romainguy.kotlin.math.Mat4
import dev.romainguy.kotlin.math.Float4
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.model.ModelInstance

@Composable
fun VrmSceneView(
    modelPath: String?,
    modifier: Modifier = Modifier,
    idleAnimPath: String? = null,
    isSpeaking: Boolean,
    isLocked: Boolean,
    initialCameraData: FloatArray?,
    engine: Engine,
    modelLoader: ModelLoader,
    modelInstance: ModelInstance?,
    onLoaded: () -> Unit,
    onError: (String) -> Unit,
    onCameraMoved: (px: Float, py: Float, pz: Float, tx: Float, ty: Float, tz: Float) -> Unit
) {
    val cameraNode = rememberCameraNode(engine) {
        if (initialCameraData != null && initialCameraData.size >= 6) {
            position = Position(initialCameraData[0], initialCameraData[1], initialCameraData[2])
            lookAt(Position(initialCameraData[3], initialCameraData[4], initialCameraData[5]))
        } else {
            position = Position(x = 0f, y = 1.2f, z = 1.6f)
            lookAt(Position(x = 0f, y = 1.1f, z = 0f))
        }
    }

    val cameraManipulator = rememberCameraManipulator()

    SceneView(
        modifier = modifier.fillMaxSize(),
        engine = engine,
        modelLoader = modelLoader,
        cameraNode = cameraNode,
        cameraManipulator = cameraManipulator,
        mainLightNode = rememberMainLightNode(engine) {
            intensity = 80_000f
        }
    ) {
        if (modelInstance != null) {
            val animInstance = idleAnimPath?.let { rememberModelInstance(modelLoader, it) }
            
            ModelNode(
                modelInstance = modelInstance,
                scaleToUnits = 1.0f,
                position = Position(x = 0f, y = -1.0f, z = 0f),
                apply = {
                    val boneMap = nodes.associateBy { it.name ?: "" }

                    val blinkIndices = renderableNodes.map { node ->
                        node to node.morphTargetNames.mapIndexedNotNull { i, name ->
                            if (name.lowercase().contains("blink")) i else null
                        }
                    }.filter { it.second.isNotEmpty() }

                    var startTimeNanos = -1L
                    var nextBlinkTime = 0f
                    var blinkStartTime = -1f

                    val tempTransform = FloatArray(16)
                    val tempMat = Mat4()

                    onFrame = { frameTimeNanos ->
                        if (startTimeNanos == -1L) startTimeNanos = frameTimeNanos
                        val t = (frameTimeNanos - startTimeNanos) / 1_000_000_000f

                        animInstance?.let { anim ->
                            val animator = anim.animator
                            if (animator.animationCount > 0) {
                                animator.applyAnimation(0, t % animator.getAnimationDuration(0))
                                animator.updateBoneMatrices()
                                
                                val animAsset = anim.asset
                                val transformManager = engine.transformManager
                                anim.entities.forEach { entity ->
                                    val name = animAsset.getName(entity)
                                    if (!name.isNullOrBlank() && boneMap.containsKey(name)) {
                                        val instanceIdx = transformManager.getInstance(entity)
                                        if (instanceIdx != 0) {
                                            transformManager.getTransform(instanceIdx, tempTransform)
                                            // Optimized: avoid Float4 allocation by modifying the Mat4 fields directly
                                            tempMat.x.x = tempTransform[0]; tempMat.x.y = tempTransform[1]; tempMat.x.z = tempTransform[2]; tempMat.x.w = tempTransform[3]
                                            tempMat.y.x = tempTransform[4]; tempMat.y.y = tempTransform[5]; tempMat.y.z = tempTransform[6]; tempMat.y.w = tempTransform[7]
                                            tempMat.z.x = tempTransform[8]; tempMat.z.y = tempTransform[9]; tempMat.z.z = tempTransform[10]; tempMat.z.w = tempTransform[11]
                                            tempMat.w.x = tempTransform[12]; tempMat.w.y = tempTransform[13]; tempMat.w.z = tempTransform[14]; tempMat.w.w = tempTransform[15]
                                            boneMap[name]?.transform = tempMat
                                        }
                                    }
                                }
                            }
                        }

                        if (nextBlinkTime == 0f) nextBlinkTime = t + (2f + Math.random().toFloat() * 4f)
                        if (t > nextBlinkTime && blinkStartTime == -1f) blinkStartTime = t
                        if (blinkStartTime != -1f) {
                            val progress = (t - blinkStartTime) / 0.12f
                            if (progress > 1f) {
                                blinkStartTime = -1f
                                nextBlinkTime = t + (2f + Math.random().toFloat() * 5f)
                                blinkIndices.forEach { (node, idxs) ->
                                    val weights = FloatArray(node.morphTargetNames.size)
                                    idxs.forEach { i -> weights[i] = 0f }
                                    node.setMorphWeights(weights, 0)
                                }
                            } else {
                                val v = if (progress < 0.5f) progress * 2f else 2f - (progress * 2f)
                                blinkIndices.forEach { (node, idxs) ->
                                    val weights = FloatArray(node.morphTargetNames.size)
                                    idxs.forEach { i -> weights[i] = v }
                                    node.setMorphWeights(weights, 0)
                                }
                            }
                        }
                    }
                }
            )
            
            LaunchedEffect(modelInstance) {
                onLoaded()
            }
        }
    }
}
