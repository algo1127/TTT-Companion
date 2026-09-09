extends Node3D

var avatar = null
var extensions_registered = false
var is_camera_locked = false
var pending_anim_path = ""

var cam_pivot: Node3D
var camera: Camera3D
var target_rotation = Vector3.ZERO
var current_rotation = Vector3.ZERO
var zoom_distance = 1.3

# Tracking variables for character focusing
var skeleton_node: Skeleton3D = null
var hips_bone_idx = -1

# Blinking logic variables
var blink_timer = 0.0
var next_blink_time = 3.0
var blink_duration = 0.15
var is_blinking = false
var blink_value = 0.0
var blink_shapes = [] # Array of { "mesh": MeshInstance3D, "idx": int }

func _ready():
	print("[Godot] main.gd _ready")

	# ISSUE 1 FIX: Force 8-bone skinning weights at runtime
	# This override ensures VRM models don't explode when project.godot is ignored.
	ProjectSettings.set_setting("rendering/mesh_storage/skinning/max_blend_weights", 8)
	ProjectSettings.save() # Ensure it persists if needed, though runtime set is often enough

	# Fix orientation constant (SCREEN_PORTRAIT is the correct 4.x member)
	DisplayServer.screen_set_orientation(DisplayServer.SCREEN_PORTRAIT)

	setup_scene()

	if not extensions_registered:
		_register_vrm_extensions()
		extensions_registered = true

	if Engine.has_singleton("GodotVrmPlugin"):
		var plugin = Engine.get_singleton("GodotVrmPlugin")
		plugin.connect("load_vrm_requested", _on_load_vrm_requested)
		plugin.connect("load_anim_requested", _on_load_anim_requested)
		plugin.connect("speaking_changed", _on_speaking_changed)
		plugin.connect("camera_lock_changed", _on_camera_lock_changed)
		plugin.connect("camera_init_requested", _on_camera_init_requested)
		print("[Godot] Connected to GodotVrmPlugin")

func _on_load_anim_requested(path: String):
	print("[Godot] load_anim_requested: ", path)
	if not avatar:
		print("[Godot] Avatar not ready, storing animation path as pending: ", path)
		pending_anim_path = path
		return

	if not FileAccess.file_exists(path):
		print("Error: Target VRMA file path not found: ", path)
		return

	var VRMLoaderScript = load("res://addons/vrm/vrm_loader.gd")
	var vrm_loader = VRMLoaderScript.new()
	var lib = vrm_loader.load_vrma_from_path(path)

	if lib == null:
		print("Error: Failed to load animation library from path.")
		return

	var anim_player: AnimationPlayer = _find_animation_player(avatar)
	if anim_player == null:
		print("[Godot] AnimationPlayer not found on avatar, creating one.")
		anim_player = AnimationPlayer.new()
		avatar.add_child(anim_player)
		# Set root node to parent of skeleton (usually the avatar itself)
		anim_player.root_node = anim_player.get_path_to(avatar)
	else:
		print("[Godot] AnimationPlayer found on avatar at: ", anim_player.get_path())
		# FIX: Ensure the root node is correctly set to the avatar root for imported scenes
		var current_root = anim_player.get_node_or_null(anim_player.root_node)
		if not current_root or current_root != avatar:
			anim_player.root_node = anim_player.get_path_to(avatar)

	# Clean old library if exists
	if anim_player.has_animation_library("vrma"):
		anim_player.remove_animation_library("vrma")

	# Retarget tracks to the actual skeleton name in the loaded avatar
	var skeleton = _find_skeleton(avatar)
	if skeleton:
		# Pass anim_player so the loader can calculate the correct relative path
		vrm_loader.retarget_to_skeleton(lib, skeleton, anim_player)
	else:
		print("Warning: No skeleton found in avatar, tracks might not resolve.")

	anim_player.add_animation_library("vrma", lib)
	print("[Godot] Animation library added. Animations: ", lib.get_animation_list())

	var anim_names = lib.get_animation_list()
	if anim_names.size() > 0:
		var anim_to_play = "vrma/" + anim_names[0]

		# If there are multiple, look for "idle" specifically
		for n in anim_names:
			if n.to_lower().contains("idle"):
				anim_to_play = "vrma/" + n
				break

		var anim = anim_player.get_animation(anim_to_play)
		if anim:
			anim.loop_mode = Animation.LOOP_LINEAR
			anim_player.play(anim_to_play)
			print("[Godot] Successfully playing animation: ", anim_to_play)
		else:
			print("Error: Animation found but could not be played: ", anim_to_play)
	else:
		print("Error: No animations found in the library.")

func _find_animation_player(node: Node) -> AnimationPlayer:
	if node is AnimationPlayer:
		return node
	for child in node.get_children():
		var found = _find_animation_player(child)
		if found:
			return found
	return null

func _on_camera_init_requested(rx: float, ry: float, zoom: float):
	print("[Godot] camera_init_requested: R(", rx, ",", ry, ") Z(", zoom, ")")
	target_rotation.x = rx
	target_rotation.y = ry
	zoom_distance = zoom
	current_rotation = target_rotation
	if cam_pivot:
		cam_pivot.rotation.x = rx
		cam_pivot.rotation.y = ry
	if camera:
		camera.position.z = zoom

func _on_camera_lock_changed(locked: bool):
	print("[Godot] camera_lock_changed: ", locked)
	is_camera_locked = locked

func _register_vrm_extensions():
	# ISSUE 2 FIX: Register ALL V-Sekai extensions for runtime GLTFDocument load
	var vrm_ext = load("res://addons/vrm/vrm_extension.gd")
	var vrmc_vrm = load("res://addons/vrm/1.0/VRMC_vrm.gd")
	var mtoon = load("res://addons/vrm/1.0/VRMC_materials_mtoon.gd")
	var constraint = load("res://addons/vrm/1.0/VRMC_node_constraint.gd")
	var springbone = load("res://addons/vrm/1.0/VRMC_springBone.gd")
	var emissive = load("res://addons/vrm/1.0/VRMC_materials_hdr_emissiveMultiplier.gd")

	if vrm_ext: GLTFDocument.register_gltf_document_extension(vrm_ext.new(), true)
	if vrmc_vrm: GLTFDocument.register_gltf_document_extension(vrmc_vrm.new(), true)
	if mtoon: GLTFDocument.register_gltf_document_extension(mtoon.new(), true)
	if constraint: GLTFDocument.register_gltf_document_extension(constraint.new(), true)
	if springbone: GLTFDocument.register_gltf_document_extension(springbone.new(), true)
	if emissive: GLTFDocument.register_gltf_document_extension(emissive.new(), true)

	print("[Godot] VRM extensions registered (Full Set)")

func setup_scene():
	cam_pivot = Node3D.new()
	add_child(cam_pivot)
	cam_pivot.position = Vector3(0, 1.1, 0) # Focus on upper body/face

	camera = Camera3D.new()
	cam_pivot.add_child(camera)
	camera.position = Vector3(0, 0, zoom_distance)

	var light = DirectionalLight3D.new()
	add_child(light)
	light.quaternion = Quaternion(Vector3.RIGHT, -PI/4)
	light.light_energy = 2.0 # Increased for better visibility
	light.shadow_enabled = true

	var env = WorldEnvironment.new()
	add_child(env)
	env.environment = Environment.new()
	env.environment.background_mode = Environment.BG_COLOR
	env.environment.background_color = Color(0.1, 0.1, 0.12)
	env.environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	env.environment.ambient_light_color = Color.WHITE
	env.environment.ambient_light_energy = 1.0 # Increased for better visibility

func _input(event):
	if is_camera_locked: return

	if event is InputEventScreenDrag:
		# Orbit rotation
		target_rotation.y -= event.relative.x * 0.005
		target_rotation.x -= event.relative.y * 0.005
		target_rotation.x = clamp(target_rotation.x, -0.5, 0.5)
		_save_camera()

	elif event is InputEventMagnifyGesture:
		# Zoom support
		zoom_distance = clamp(zoom_distance / event.factor, 0.5, 3.0)
		_save_camera()

func _save_camera():
	if Engine.has_singleton("GodotVrmPlugin"):
		var plugin = Engine.get_singleton("GodotVrmPlugin")
		plugin.onCameraMoved(target_rotation.x, target_rotation.y, zoom_distance)

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

	# Search for blink morph targets across all meshes
	blink_shapes.clear()
	_find_blink_shapes(avatar)
	print("[Godot] Found ", blink_shapes.size(), " blink morph targets")

	# Setup dynamic tracking target
	skeleton_node = _find_skeleton(avatar)
	if skeleton_node:
		var skeleton = skeleton_node
		# Re-verify and force the retargeting profiles to stay clean
		skeleton.motion_scale = 1.0

		hips_bone_idx = skeleton.find_bone("Hips")
		if hips_bone_idx == -1:
			hips_bone_idx = skeleton.find_bone("hips")
		print("[Godot] Tracking bone index (Hips): ", hips_bone_idx)

		# Reset bone pose scales to absolute baseline (Fixes bulged thighs/knees)
		for i in range(skeleton.get_bone_count()):
			skeleton.set_bone_pose_scale(i, Vector3.ONE)

		# Clear out any hardcoded initialization offsets from raw storage
		skeleton.reset_bone_poses()

	# Mount the fully processed, un-distorted avatar node directly into your app view
	add_child(avatar)
	avatar.position = Vector3(0, 0, 0)
	avatar.rotation_degrees = Vector3(0, 180, 0) # Face camera

	print("[Godot] Avatar structure:")
	_print_tree(avatar, "  ")

	# Notify Kotlin
	var plugin = Engine.get_singleton("GodotVrmPlugin")
	if plugin: plugin.onVrmLoaded()

	if pending_anim_path != "":
		_on_load_anim_requested(pending_anim_path)
		pending_anim_path = ""

func _print_tree(node: Node, indent: String):
	print(indent, node.name, " (", node.get_class(), ")")
	for child in node.get_children():
		_print_tree(child, indent + "  ")

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

func _find_blink_shapes(node: Node):
	if node is MeshInstance3D:
		var mi: MeshInstance3D = node
		if mi.mesh:
			for i in range(mi.mesh.get_blend_shape_count()):
				var s_name = mi.mesh.get_blend_shape_name(i).to_lower()

				var is_blink = false
				# 1. Direct "blink" or "blinking"
				if s_name == "blink" or s_name == "blinking":
					is_blink = true
				# 2. Contains "eye" and "blink"
				elif s_name.contains("eye") and s_name.contains("blink"):
					is_blink = true
				# 3. Contains "eye", "close" and ("l"/"left" or "r"/"right")
				elif s_name.contains("eye") and s_name.contains("close"):
					if s_name.contains("left") or s_name.contains("right") or s_name.contains("_l") or s_name.contains("_r"):
						is_blink = true

				if is_blink:
					blink_shapes.append({ "mesh": mi, "idx": i })

	for child in node.get_children():
		_find_blink_shapes(child)

func _process(delta):
	# Smooth camera movement
	current_rotation = current_rotation.lerp(target_rotation, delta * 10.0)
	cam_pivot.rotation.x = current_rotation.x
	cam_pivot.rotation.y = current_rotation.y
	camera.position.z = lerp(camera.position.z, zoom_distance, delta * 10.0)

	# Dynamic Character Focusing
	if skeleton_node and hips_bone_idx != -1:
		var hips_pose = skeleton_node.get_bone_global_pose(hips_bone_idx)
		var target_pos = skeleton_node.to_global(hips_pose.origin)
		# Add a vertical offset to focus on chest/face area instead of exactly the hips
		target_pos.y += 0.4
		cam_pivot.global_position = cam_pivot.global_position.lerp(target_pos, delta * 5.0)

	# --- Blinking Logic ---
	blink_timer += delta
	if not is_blinking:
		if blink_timer >= next_blink_time:
			is_blinking = true
			blink_timer = 0.0
	else:
		# Simple triangle wave for blink: 0 -> 1 -> 0
		var half_dur = blink_duration / 2.0
		if blink_timer < half_dur:
			blink_value = blink_timer / half_dur
		elif blink_timer < blink_duration:
			blink_value = 1.0 - ((blink_timer - half_dur) / half_dur)
		else:
			blink_value = 0.0
			is_blinking = false
			blink_timer = 0.0
			next_blink_time = randf_range(2.0, 6.0) # Randomize next blink

		for item in blink_shapes:
			item.mesh.set_blend_shape_value(item.idx, blink_value)

	# --- Lip Sync / Speaking ---
	# Lip sync disabled per user request
	if avatar and is_speaking_active:
		pass
		# var t = Time.get_ticks_msec() / 1000.0
		# var value = (sin(t * 15.0) + 1.0) * 0.35
		# _apply_mouth_blend_shape(avatar, value)

func _apply_mouth_blend_shape(node: Node, value: float):
	if node is MeshInstance3D:
		var idx = node.find_blend_shape_by_name("A")
		if idx == -1: idx = node.find_blend_shape_by_name("Mouth_A")
		if idx == -1: idx = node.find_blend_shape_by_name("jawOpen")
		if idx != -1:
			node.set_blend_shape_value(idx, value)
	for child in node.get_children():
		_apply_mouth_blend_shape(child, value)
