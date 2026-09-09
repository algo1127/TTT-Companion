extends RefCounted
class_name VRMLoader

func load_vrm_from_path(path: String) -> Node:
	var doc = GLTFDocument.new()
	var state = GLTFState.new()

	# Pass essential VRM metadata to the state BEFORE loading
	state.set_additional_data("vrm/head_hiding_method", 0)
	state.handle_binary_image = GLTFState.HANDLE_BINARY_EMBED_AS_UNCOMPRESSED

	# Use standard binds (0) for runtime stability
	var error = doc.append_from_file(path, state, 0)
	if error != OK:
		printerr("VRMLoader: Failed to parse VRM: ", error)
		return null

	var generated_node = doc.generate_scene(state)

	# ISSUE 3 FIX: Node Duplication Workaround
	# Godot 4 has a bug where runtime-loaded GLTF scenes can deform on the first frame.
	# Duplicating the node tree forces the skeleton and bone-skin mapping to initialize correctly
	# without the strict type-mismatch errors caused by PackedScene.
	var final_node = generated_node.duplicate(Node.DUPLICATE_SIGNALS | Node.DUPLICATE_GROUPS | Node.DUPLICATE_SCRIPTS)
	generated_node.free()

	return final_node

func load_vrma_from_path(path: String) -> AnimationLibrary:
	print("[Godot] VRMLoader: Loading VRMA from path: ", path)
	var doc = GLTFDocument.new()
	var state = GLTFState.new()

	state.handle_binary_image = GLTFState.HANDLE_BINARY_EMBED_AS_UNCOMPRESSED

	# Use standard binds (0)
	var error = doc.append_from_file(path, state, 0)
	if error != OK:
		print("[Godot] VRMLoader: Failed to parse VRMA (Error code: ", error, ")")
		return null

	print("[Godot] VRMLoader: GLTF append success, generating scene...")
	var generated_node = doc.generate_scene(state)
	var anim_player: AnimationPlayer = null

	if generated_node is AnimationPlayer:
		anim_player = generated_node
	else:
		anim_player = _find_animation_player(generated_node)

	var lib = null
	if anim_player:
		print("[Godot] VRMLoader: AnimationPlayer found in VRMA scene.")
		var names = anim_player.get_animation_library_list()
		if names.size() > 0:
			print("[Godot] VRMLoader: Animation libraries found: ", names)
			lib = anim_player.get_animation_library(names[0])
		else:
			print("[Godot] VRMLoader: No libraries found, collecting individual animations.")
			# Fallback: if animations are not in a library, collect them
			var anim_list = anim_player.get_animation_list()
			if anim_list.size() > 0:
				lib = AnimationLibrary.new()
				for a_name in anim_list:
					lib.add_animation(a_name, anim_player.get_animation(a_name))

		if lib:
			pass # We will fix tracks in main.gd where we have the skeleton
	else:
		print("[Godot] VRMLoader: No AnimationPlayer found in VRMA scene.")

	generated_node.free()
	return lib

func retarget_to_skeleton(lib: AnimationLibrary, skeleton: Skeleton3D, anim_player: AnimationPlayer = null):
	var skeleton_path = anim_player.get_path_to(skeleton) if anim_player else NodePath(skeleton.name)
	print("[Godot] Retargeting to skeleton at: ", skeleton_path)

	for anim_name in lib.get_animation_list():
		var anim = lib.get_animation(anim_name)
		print("[Godot] Processing animation: ", anim_name)

		# We'll build a list of changes to apply after iterating to avoid index issues
		var changes = []

		for i in range(anim.get_track_count()):
			var old_path = anim.track_get_path(i)
			var bone_name = ""

			# 1. Try to get bone name from property (standard bone track: Skeleton:Bone)
			if old_path.get_subname_count() > 0:
				bone_name = str(old_path.get_subname(0))
			else:
				# 2. Try to get bone name from the last node name (node-based track: Armature/Hips)
				bone_name = str(old_path.get_name(old_path.get_name_count() - 1))

			if bone_name == "": continue

			# 3. Fuzzy match the bone in our VRM skeleton
			var target_bone = ""
			if skeleton.find_bone(bone_name) != -1:
				target_bone = bone_name
			else:
				# Clean common prefixes like mixamorig_
				var clean = bone_name.replace("mixamorig_", "").replace("mixamorig:", "")
				if skeleton.find_bone(clean) != -1:
					target_bone = clean
				else:
					# Case-insensitive fallback
					for b in range(skeleton.get_bone_count()):
						var b_name = skeleton.get_bone_name(b)
						if b_name.to_lower() == bone_name.to_lower() or b_name.to_lower() == clean.to_lower():
							target_bone = b_name
							break

			if target_bone != "":
				# 4. Construct the correct Godot 4 bone path: "Path/To/Skeleton:BoneName"
				var new_path = NodePath(str(skeleton_path) + ":" + target_bone)
				changes.append({"idx": i, "path": new_path})

		for change in changes:
			anim.track_set_path(change.idx, change.path)

	print("[Godot] Retargeting complete.")

func _find_animation_player(node: Node) -> AnimationPlayer:
	if node is AnimationPlayer:
		return node
	for child in node.get_children():
		var found = _find_animation_player(child)
		if found:
			return found
	return null
