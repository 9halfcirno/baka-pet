action = {
	start(pet) {
		pet.animation.switchTo("down");
		pet.say("啊呀！")
	},

	update(pet) {
		if (pet.animation.isLastFrame) {
			pet.action.switchTo("idle")
		}
	}
}