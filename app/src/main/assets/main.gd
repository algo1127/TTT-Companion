extends Node3D

var avatar = null
var extensions_registered = false

func _ready():
	print("[Godot] main.gd _ready")

	# Fix orientation constant (SCREEN_PORTRAIT is the correct 4.x member)
	DisplayServer.screen_set_orientation(DisplayServer.SCREEN_PORTRAIT)

	setup_scene()

	if not extensions_registered:
		_register_vrm_extensions()
		extensions_registered = true

	if Engine.has_singleton("GodotVrmPlugin"):
		var plugin = Engine.get_singleton("GodotVrmPlugin")
		plugin.connect("load_vrm_requested", _on_load_vrm_requested)
		plugin.connect("speaking_changed", _on_speaking_changed)
		print("[Godot] Connected to GodotVrmPlugin")

func _register_vrm_extensions():
	# Registering all necessary extensions for a runtime GLTFDocument load
	var vrm_ext = load("res://addons/vrm/vrm_extension.gd")
	var vrmc_vrm = load("res://addons/vrm/1.0/VRMC_vrm.gd")
	var mtoon = load("res://addons/vrm/1.0/VRMC_materials_mtoon.gd")

	if vrm_ext: GLTFDocument.register_gltf_document_extension(vrm_ext.new(), true)
	if vrmc_vrm: GLTFDocument.register_gltf_document_extension(vrmc_vrm.new(), true)
	if mtoon: GLTFDocument.register_gltf_document_extension(mtoon.new(), true)
	print("[Godot] VRM extensions registered")

func setup_scene():
	var cam = Camera3D.new()
	add_child(cam)
	cam.position = Vector3(0, 1.2, 3.0) # Pulled back to see full body proportions
	cam.look_at(Vector3(0, 1.0, 0))

	var light = DirectionalLight3D.new()
	add_child(light)
	light.quaternion = Quaternion(Vector3.RIGHT, -PI/4)
	light.light_energy = 1.0
	light.shadow_enabled = true

	var env = WorldEnvironment.new()
	add_child(env)
	env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR
	env.environment.background_color = Color(0.1, 0.1, 0.12)
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color.WHITE
	env.environment.ambient_light_energy = 0.5

func _on_load_vrm_requested(path: String):
	print("[Godot] load_vrm_requested via Addon Loader: ", path)
	# Check if our target file path exists on the Android storage system
	if not FileAccess.file_exists(path):
		print("Error: Target VRM file path not found: ", path)
		return

	# Explicitly load the script to avoid "Identifier not declared" parse errors
	# in standalone runtime environments where the class cache might be missing.
	var VRMLoaderScript = load("res://addons/vrm/vrm_loader.gd")
	if not VRMLoaderScript:
		print("Error: Could not load vrm_loader.gd script")
		return

	var vrm_loader = VRMLoaderScript.new()

	# Execute the addon's internal matrix-safe binding routine
	# This automatically processes skins, blendshapes, and springbones securely
	var avatar_node = vrm_loader.load_vrm_from_path(path)

	if avatar_node == null:
		print("Error: The VRM Loader returned an empty or corrupted asset.")
		return

	if avatar:
		avatar.queue_free()

	avatar = avatar_node

	# Double-check the absolute structural nodes before attaching to world space
	var skeleton: Skeleton3D = _find_skeleton(avatar)
	if skeleton:
		# Re-verify and force the retargeting profiles to stay clean
		skeleton.motion_scale = 1.0

		# Reset bone pose scales to absolute baseline (Fixes bulged thighs/knees)
		for i in range(skeleton.get_bone_count()):
			skeleton.set_bone_pose_scale(i, Vector3.ONE)

		# Clear out any hardcoded initialization offsets from raw storage
		skeleton.reset_bone_poses()

	# Mount the fully processed, un-distorted avatar node directly into your app view
	add_child(avatar)
	avatar.position = Vector3(0, 0, 0)
	avatar.rotation_degrees = Vector3(0, 180, 0) # Face camera

	# Notify Kotlin
	var plugin = Engine.get_singleton("GodotVrmPlugin")
	if plugin: plugin.onVrmLoaded()

var is_speaking_active = false
func _on_speaking_changed(is_speaking: bool):
	print("[Godot] speaking_changed: ", is_speaking)
	is_speaking_active = is_speaking

func load_vrm(path: String):
	# Re-routing legacy call to the new signal-based logic
	_on_load_vrm_requested(path)

# Helper function to dig through the imported nodes and isolate the skeleton
func _find_skeleton(node: Node) -> Skeleton3D:
	if node is Skeleton3D:
		return node
	for child in node.get_children():
		var found = _find_skeleton(child)
		if found:
			return found
	return null

func _process(_delta):
	if avatar and is_speaking_active:
		var t = Time.get_ticks_msec() / 1000.0
		var value = (sin(t * 15.0) + 1.0) * 0.35
		_apply_mouth_blend_shape(avatar, value)

func _apply_mouth_blend_shape(node: Node, value: float):
	if node is MeshInstance3D:
		var idx = node.find_blend_shape_by_name("A")
		if idx == -1: idx = node.find_blend_shape_by_name("Mouth_A")
		if idx == -1: idx = node.find_blend_shape_by_name("jawOpen")
		if idx != -1:
			node.set_blend_shape_value(idx, value)
	for child in node.get_children():
		_apply_mouth_blend_shape(child, value)
