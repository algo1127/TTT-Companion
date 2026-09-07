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

	# ISSUE 3 FIX: PackedScene Workaround
	# Godot 4 has a bug where runtime-loaded GLTF scenes can deform on the first frame.
	# Packing and instantiating forces the skeleton to initialize correctly.
	var packed_scene = PackedScene.new()
	var pack_result = packed_scene.pack(generated_node)
	if pack_result != OK:
		printerr("VRMLoader: Failed to pack scene: ", pack_result)
		return generated_node

	var final_node = packed_scene.instantiate()
	generated_node.free()

	return final_node
