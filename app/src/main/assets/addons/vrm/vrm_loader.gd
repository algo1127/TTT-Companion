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

	return doc.generate_scene(state)
