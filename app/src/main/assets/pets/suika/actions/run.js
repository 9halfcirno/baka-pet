module.exports = {
	id: "run",

	start(pet) {
		pet.animation.switchTo("run");
		this.targetPos = {
			x: Math.random() * device.width - pet.getImageView().width,
			y: Math.random() * device.height - pet.getImageView().height
		};
		this.img = pet.getImageView();
	},

	update(pet) {
		if (pet.dragging) return;
		let currentX = pet.x;
		let currentY = pet.y;
		let targetX = this.targetPos.x;
		let targetY = this.targetPos.y;

		let dx = targetX - currentX;
		let dy = targetY - currentY;
		let distance = Math.hypot(dx, dy);

		if (dx < 0) {
			this.img.scaleX = 1
		} else this.img.scaleX = -1

		let speed = 6; // 每帧移动 6 像素

		if (distance < speed) {
			this.targetPos = {
				x: Math.random() * device.width - this.img.width,
				y: Math.random() * device.height - this.img.height
			};
		} else {
			// 向目标点移动一个步长
			let stepX = (dx / distance) * speed;
			let stepY = (dy / distance) * speed;
			pet.setPosition(stepX + currentX, stepY + currentY)
		}
	}
};