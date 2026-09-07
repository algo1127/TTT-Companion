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
