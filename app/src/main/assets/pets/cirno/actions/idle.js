const nextAct = [
	"walk",
	//"spin",
	"circles"
]

action = {
	id: "idle",

	start(pet) {
		pet.animation.switchTo("idle");
		this.startTime = Date.now();
		this.during = Math.floor(Math.random() * 8000 + 2000)
	},

	update(pet) {
		if (Date.now() - this.startTime > this.during) {
			pet.action.switchTo(nextAct[Math.floor(Math.random() * nextAct.length)])
		}
	},
	
	touch(pet) {
		pet.action.switchTo("down")
	}
}