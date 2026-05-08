action = {
	start(pet) {
		pet.animation.switchTo("fall");
		this.startTime = Date.now();
		this.a = 1920; // 加速度（像素/秒²，可调整至合适值）
		this.vy = 0; // 初始垂直速度
		this.lastTime = this.startTime;
	},

	update(pet) {
		// 如果正在被拖动，暂停物理计算，避免坐标跳变
		if (pet.dragging) {
			this.lastTime = Date.now();
			this.vy = 0;
			return;
		}

		const now = Date.now();
		const dt = (now - this.lastTime) / 1000; // 转换为秒
		if (dt <= 0) return;
		this.lastTime = now;

		// 模拟重力加速度：v = v0 + a * t
		this.vy += this.a * dt;
		// 本帧位移（向下为正）
		const dy = this.vy * dt;
		pet.move(0, dy);

		// 判断是否完全掉出屏幕底部
		if (pet.y > device.height) {
			// 将宠物放到屏幕底边处（刚好完全不可见的位置）
			//pet.setPosition(pet.x, device.height);
			// 切换回漫步动作
			pet.action.switchTo("down");
		}
	}
};