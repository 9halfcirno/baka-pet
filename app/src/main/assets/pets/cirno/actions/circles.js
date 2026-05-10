module.exports = {
	id: "circles",

	start(pet) {
		pet.animation.switchTo("circles");
	},

	update(pet) {
		if (pet.animation.isLastFrame) {
			//pet.playAudio("touch.wav")
			pet.action.switchTo("idle");
		}
	}
}