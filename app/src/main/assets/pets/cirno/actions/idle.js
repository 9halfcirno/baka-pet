const nextAct = [
	"walk",
	//"spin",
	"circles"
]

const say = [
	"咱可是最强的！",
	"大酱在哪里呢？",
	"喂！看看咱！",
	"Baka!",
	"咱做过一个梦，梦见咱变成勇者去救大酱了！",
	"斯塔、桑尼、露娜她们三个老是对咱恶作剧！",
	"Fumo？咱可不是那种软踏踏的玩偶！",
	"因为咱是最强的，所以咱就是最强的！",
	"大酱说咱是最强的，咱就是最强的！"
]

action = {
	id: "idle",

	start(pet) {
		pet.animation.switchTo("idle");
		this.startTime = Date.now();
		this.during = Math.floor(Math.random() * 8000 + 2000)
	},

	update(pet) {
		if (Math.random() < 0.01 && !pet.isSaying) {
			pet.say(say[Math.floor(Math.random() * say.length)])
		}
		
		if (Date.now() - this.startTime > this.during) {
			pet.action.switchTo(nextAct[Math.floor(Math.random() * nextAct.length)])
		}
	},
	
	touch(pet) {
		pet.action.switchTo("down")
	}
}