action = {
	start(pet) {
		pet.animation.switchTo("down");
	},

	update(pet) {
		if (pet.animation.isLastFrame) {
			pet.action.switchTo("idle")
		}
	}
}