# Project Source Code

Generated on: 5/8/2026, 7:32:18 PM

---

## File: `assets/pets/cirno/actions/circles.js`

```js
module.exports = {
	id: "circles",

	start(pet) {
		pet.animation.switchTo("circles");
	},

	update(pet) {
		if (pet.animation.isLastFrame) {
			//pet.playAudio("touch.wav")
			pet.action.switchTo("idle")
		}
	}
}
```

---

## File: `assets/pets/cirno/actions/down.js`

```js
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
```

---

## File: `assets/pets/cirno/actions/fall.js`

```js
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
```

---

## File: `assets/pets/cirno/actions/idle.js`

```js
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
```

---

## File: `assets/pets/cirno/actions/main.js`

```js
module.exports = {
	
}
```

---

## File: `assets/pets/cirno/actions/mouse_round.js`

```js
module.exports = {
	id: "mouse_round",
	
	start(pet) {
		
	}
}
```

---

## File: `assets/pets/cirno/actions/spin.js`

```js
module.exports = {
	id: "spin",

	start(pet) {
		// 调用 spin 材质动画
		pet.animation.switchTo("spin");

		// 初始速度：随机一个方向，速度稍微快一点才有台球感
		const speedBase = 32;
		let angle = Math.random() * Math.PI * 2;
		this.imgW = pet.getImageView().width;
		this.imgH = pet.getImageView().height;
		this.vx = Math.cos(angle) * speedBase;
		this.vy = Math.sin(angle) * speedBase;

		this.duration = 600 + Math.random() * 600;
		this.timer = 0;
	},

	update(pet) {
		let currentX = pet.getX();
		let currentY = pet.getY();


		// 1. 预测下一帧的位置
		let nextX = currentX + this.vx;
		let nextY = currentY + this.vy;
		
		// 1.5. 模拟阻力
		this.vx -= this.vx / 2000
		this.vy -= this.vy / 2000
		
		// 2. 边界碰撞检测 (台球反弹逻辑)

		// 左右撞墙：速度 X 取反
		if (nextX < 0 || nextX > device.width - this.imgW) {
			this.vx *= -1;
			// 修正位置防止卡墙
			nextX = nextX < 0 ? 0 : device.width - this.imgW;
		}

		// 上下撞墙：速度 Y 取反
		if (nextY < 0 || nextY > device.height - this.imgH) {
			this.vy *= -1;
			// 修正位置防止卡墙
			nextY = nextY < 0 ? 0 : device.height - this.imgH;
		}

		// 3. 执行位移
		if (pet.dragging === false) {
			// 注意：这里直接计算出增量传给 move，或者用 setPosition
			pet.move(this.vx, this.vy);
		}

		// 4. 自转反馈 (可选：根据速度方向轻微改变镜像，或者保持 spin 动画原样)
		if (this.vx < 0) {
			pet.getImageView().scaleX = -1;
		} else {
			pet.getImageView().scaleX = 1;
		}

		// 5. 计时结束逻辑
		this.timer++;
		if (this.timer > this.duration) {
			pet.action.switchTo("idle");
		}
	}
};
```

---

## File: `assets/pets/cirno/actions/walk.js`

```js
module.exports = {
	id: "walk",

	start(pet) {
		pet.animation.switchTo("walk_front");
		this.angle = Math.random() * Math.PI * 2;
		this.speed = 4;

		// 【优化 1：启动时缓存所有静态/初始数据】
		// 避免在 update() 循环中重复跨语言读取
		this.imgW = pet._view.width;  
		this.imgH = pet._view.height;
		
		// 将 Java 层的真实坐标拉取到 JS 内存中作为“影子坐标”
		this.curX = pet.getX();
		this.curY = pet.getY();

		this.changeDirTimer = 0;
		this.nextChangeTime = Math.random() * 60 + 30;
		this.totalTimer = 0;
	},

	update(pet) {
		// 【防抽搐逻辑】：被拖动时暂停运算，并重新同步 Java 层的新坐标
		if (pet.dragging) {
			this.curX = pet.getX(); 
			this.curY = pet.getY();
			return;
		}

		this.changeDirTimer++;
		if (this.changeDirTimer > this.nextChangeTime) {
			this.angle += (Math.random() - 0.5) * (Math.PI / 2);
			this.changeDirTimer = 0;
			this.nextChangeTime = Math.random() * 60 + 60;
		}

		let vx = Math.cos(this.angle) * this.speed;
		let vy = Math.sin(this.angle) * this.speed;
		let hitWall = false;

		// 【优化 2：纯 JS 内存计算】
		// 不再调用 pet.getX() 和 pet.getY()，直接累加影子坐标，耗时几乎为 0
		this.curX += vx;
		this.curY += vy;

		// 边界碰撞检测与强行修正
		if (this.curX < 0) {
			this.curX = 10;
			this.angle = 0; // 强制向右
			hitWall = true;
		} else if (this.curX > device.width - this.imgW) {
			this.curX = device.width - this.imgW - 10;
			this.angle = Math.PI; // 强制向左
			hitWall = true;
		}

		if (this.curY < 0) {
			this.curY = 10;
			this.angle = Math.PI / 2; // 强制向下
			hitWall = true;
		} else if (this.curY > device.height - this.imgH) {
			this.curY = device.height - this.imgH - 10;
			this.angle = -Math.PI / 2; // 强制向上
			hitWall = true;
		}

		if (hitWall) {
			this.changeDirTimer = -30; // 撞墙冷却
		}

		// 【优化 3：单向推送 UI 更新】
		// 算完之后，每帧只调一次 JNI，把结果塞给 Java
		pet.setPosition(Math.round(this.curX), Math.round(this.curY));

		// 【优化 4：状态差异化更新】
		// 只有在方向真的发生反转时，才去触发 JNI 修改 scaleX
		if (Math.abs(vx) > 0.5) {
			let targetScale = (vx < 0) ? -1 : 1;
			pet._view.scaleX = targetScale;
		}

		this.totalTimer++;
		if (this.totalTimer > 600) {
			pet.action.switchTo("idle");
		}
	},
	
	touch(pet) {
		pet.action.switchTo("fall")
	}
};
```

---

## File: `assets/pets/cirno/menus/touch.json`

```json

```

---

## File: `assets/pets/cirno/motions/circles/meta.json`

```json
{
	"id": "circles",
	"name": "转圈圈",
	
	"frame_num": 15,
	"interval": 150,
	
	"next": "idle"
}
```

---

## File: `assets/pets/cirno/motions/down/meta.json`

```json
{
  "frame_num": 11,
  "interval": 100
}
```

---

## File: `assets/pets/cirno/motions/fall/meta.json`

```json
{
	"frame_num": 7,
	"interval": 50,
	"cycle": false
}
```

---

## File: `assets/pets/cirno/motions/idle/meta.json`

```json
{
	"id": "idle",
	"name": "空闲",
	
	"frame_num": 6,
	"frame_interval": 100
}
```

---

## File: `assets/pets/cirno/motions/spin/meta.json`

```json
{
	"id": "spin",
	"name": "旋转",
	
	"frame_num": 8,
	"frame_interval": 25
}
```

---

## File: `assets/pets/cirno/motions/walk_back/meta.json`

```json
{
	"id": "walk_back",
	"name": "后退",
	"frame_num": 8,
	"interval": 100
}
```

---

## File: `assets/pets/cirno/motions/walk_front/meta.json`

```json
{
	"id": "walk_front",
	"name": "前进",
	
	
	"frame_num": 8,
	"frame_interval": 100
}
```

---

## File: `assets/pets/cirno/pet.json`

```json
{
	"id": "cirno",
	"name": "琪露诺",
	
	"size": "40dp",
	
	"actions": {
		"default": "idle",
		"idle": "idle.js",
		"walk": "walk.js",
		"circles": "circles.js",
		"spin": "spin.js",
		"fall": "fall.js",
		"down": "down.js"
	},
	
	"motions": {
		"idle": "idle",
		"walk_front": "walk_front",
		"circles": "circles",
		"spin": "spin",
		"fall": "fall",
		"down": "down"
	}
}
```

---

## File: `assets/pets/suika/actions/run.js`

```js
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
```

---

## File: `assets/pets/suika/motions/run/meta.json`

```json
{
	"id": "run",
	"name": "奔跑",
	"frame_num": 8,
	"interval": 150
}
```

---

## File: `assets/pets/suika/pet.json`

```json
{
	"id": "suika",
	"name": "伊吹萃香",
	
	"size": "50dp",
	
	"actions": {
		"default": "run",
		"run": "run.js"
	},
	
	"motions": {
		"run": "run"
	}
}
```

---

## File: `java/com/cirno9half/bakapet/ActionController.java`

```java
package com.cirno9half.bakapet;

import android.util.Log;
import com.quickjs.JSContext;

public class ActionController {
    private final PetInstance pet;
    private final JSContext jsContext;
    private final String petVarName;
    private String currentActionId;

    public ActionController(PetInstance pet, JSContext jsContext, String petVarName) {
        this.pet = pet;
        this.jsContext = jsContext;
        this.petVarName = petVarName;
    }

    public void load(String id, String scriptContent) {
        String wrapper = "(function() {\n" +
                "  if (typeof __actionModules === 'undefined') __actionModules = {};\n" +
                "  var module = { exports: {} };\n" +
                "  var exports = module.exports;\n" +
                "  var action = undefined;\n" +
                "  " + scriptContent + "\n" +
                "  var finalAction = (typeof action !== 'undefined' && action !== null) ? action : module.exports;\n" +
                "  __actionModules['" + id + "'] = finalAction;\n" +
                "})();";
        try {
            jsContext.executeVoidScript(wrapper, "action_load_" + id);
        } catch (Exception e) {
            Log.e("ActionController", "加载动作 '" + id + "' 失败", e);
        }
    }

    public void switchTo(String actionId) {
        if (actionId == null || actionId.isEmpty()) return;
        this.currentActionId = actionId;
        String var = "__action_" + petVarName;
        String script = String.format(
                "if (__actionModules['%s']) {\n" +
                "  %s = Object.assign({}, __actionModules['%s']);\n" +
                "  if (typeof %s.start === 'function') %s.start(%s);\n" +
                "}", actionId, var, actionId, var, var, petVarName);
        jsContext.executeVoidScript(script, "action_switch_" + petVarName);
    }

    public void update() {
        if (currentActionId == null) return;
        String var = "__action_" + petVarName;
        String script = String.format(
                "if (typeof %s !== 'undefined' && typeof %s.update === 'function') {\n" +
                "  %s.update(%s);\n" +
                "}", var, var, var, petVarName);
        try {
            jsContext.executeVoidScript(script, "action_update_" + petVarName);
        } catch (Exception e) {
            Log.e("ActionController", "Update 异常：" + e.getMessage());
        }
    }

    public void onTouch(int x, int y) {
        String var = "__action_" + petVarName;
        String script = String.format(
                "if (typeof %1$s !== 'undefined' && typeof %1$s.touch === 'function') {\n" +
                "  %1$s.touch(%2$s, %3$d, %4$d);\n" +
                "}", var, petVarName, x, y);
        jsContext.executeVoidScript(script, "action_ontouch_" + petVarName);
    }

    public void onHold(int x, int y) {
        String var = "__action_" + petVarName;
        String script = String.format(
                "if (typeof %1$s !== 'undefined' && typeof %1$s.hold === 'function') {\n" +
                "  %1$s.hold(%2$s, %3$d, %4$d);\n" +
                "}", var, petVarName, x, y);
        jsContext.executeVoidScript(script, "action_onhold_" + petVarName);
    }

    public void destroy() {
        jsContext.executeVoidScript(
                "delete __action_" + petVarName + ";",
                "action_cleanup_" + petVarName);
    }
}
```

---

## File: `java/com/cirno9half/bakapet/AnimationController.java`

```java
package com.cirno9half.bakapet;

import org.json.JSONObject;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class AnimationController {
    private final PetInstance pet;
    private final PetView view;
    private final Map<String, Anim> anims = new HashMap<>();
    private String currentId;
    private int frame = 0;
    private long lastTime = 0;
    private boolean isPause = false;
    private boolean finished = false;

    private static class Anim {
        int frameNum, interval;
        String next, baseUrl;
        boolean cycle = true;
    }

    public AnimationController(PetInstance pet, PetView view) {
        this.pet = pet;
        this.view = view;
    }

    public void pause() { isPause = true; }
    public void resume() { isPause = false; }

    public void load(String id, String path) {
        try (InputStream is = view.getContext().getAssets().open(path + "/meta.json")) {
            byte[] b = new byte[is.available()];
            is.read(b);
            JSONObject json = new JSONObject(new String(b));
            Anim a = new Anim();
            a.frameNum = json.getInt("frame_num");
            a.interval = json.optInt("frame_interval", 100);
            a.next = json.optString("next", null);
            a.baseUrl = path;
            a.cycle = json.optBoolean("cycle", true);
            anims.put(id, a);
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void switchTo(String id) {
        if (id.equals(currentId) || !anims.containsKey(id)) return;
        currentId = id;
        isPause = false;
        finished = false;
        frame = 0;
        lastTime = System.currentTimeMillis();
        updateUI();
    }

    public boolean isLastFrame() {
        if (currentId == null) return false;
        Anim a = anims.get(currentId);
        return a != null && (frame + 1) >= a.frameNum;
    }

    public void update() {
        if (currentId == null) return;
        Anim a = anims.get(currentId);
        if (finished && !a.cycle && a.next == null) return;
        long now = System.currentTimeMillis();
        if (now - lastTime > a.interval) {
            if (!isPause && !finished) frame++;
            lastTime = now;
            if (frame >= a.frameNum) {
                if (a.next != null && anims.containsKey(a.next)) {
                    switchTo(a.next);
                } else {
                    if (a.cycle) {
                        frame = 0;
                        updateUI();
                    } else {
                        frame = a.frameNum - 1;
                        updateUI();
                        finished = true;
                    }
                }
            } else {
                updateUI();
            }
        }
    }

    private void updateUI() {
        if (currentId == null) return;
        Anim a = anims.get(currentId);
        pet.updateImg(a.baseUrl + "/" + frame + ".png");
    }
}
```

---

## File: `java/com/cirno9half/bakapet/JSValueHelper.java`

```java
package com.quickjs;

/**
 * 这是一个桥接类，利用包级访问权限调用 JSValue 的 protected close() 方法
 */
public class JSValueHelper {
    public static void close(JSValue value) {
        if (value != null) {
            try {
                // 因为 JSValueHelper 和 JSValue 都在 com.quickjs 包下
                // 所以可以直接调用 protected 的 close()
                value.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
```

---

## File: `java/com/cirno9half/bakapet/MainActivity.java`

```java
package com.cirno9half.bakapet;

import android.view.View;
import com.cirno9half.bakapet.R;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_OVERLAY_PERMISSION = 1001; //
    private View settingOverlay; // 设置浮层根视图
    private int screenHeight;
    private OverlayWindow settingWindow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // 设置全局异常捕获
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            saveCrashLog(throwable);
            System.exit(1);
        });
        super.onCreate(savedInstanceState);

        // 检查悬浮窗权限
        if (checkOverlayPermission()) {
            startFloatService();
        } else {
            requestOverlayPermission();
        }

        setContentView(R.layout.activity_main);
        
        // 初始化设置窗口（从顶部滑入）
        settingWindow = new OverlayWindow.Builder(this)
                .setContentView(R.layout.setting_layout) // 简化后的布局
                .setAnimDirection(OverlayWindow.AnimDirection.TOP)
                .setDismissOnBackPressed(true)
                .setDismissOnOutsideClick(true)
                .build();

        // 绑定关闭按钮（注意：现在关闭按钮在卡片内部）
        MaterialButton btnClose = settingWindow.findViewById(R.id.btn_close_setting);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> settingWindow.dismiss());
        }

        // 左上角设置图标点击显示窗口
        MaterialButton btnSettings = findViewById(R.id.btn_settings);
        btnSettings.setOnClickListener(v -> settingWindow.show());
    }

    private void saveCrashLog(Throwable t) {
        try {
            // 获取 Android/data/com.cirno9half.bakapet/files 目录
            java.io.File dir = getExternalFilesDir(null);
            if (dir != null) {
                java.io.File file = new java.io.File(dir, "crash_log.txt");
                // 使用 true 表示追加模式，不加则每次覆盖
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(file, true));
                pw.println("\n--- 崩溃时间: " + new java.util.Date().toString() + " ---");
                t.printStackTrace(pw);
                pw.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this); //
        }
        return true;
    }

    private void requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName())); //
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (checkOverlayPermission()) {
                Toast.makeText(this, "悬浮窗权限已授予", Toast.LENGTH_SHORT).show(); //
                startFloatService();
            } else {
                Toast.makeText(this, "未授予悬浮窗权限，无法启动", Toast.LENGTH_SHORT).show(); //
            }
        }
    }

    private void startFloatService() {
        Intent serviceIntent = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8.0 及以上使用前台服务启动
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

}

```

---

## File: `java/com/cirno9half/bakapet/OverlayWindow.java`

```java
package com.cirno9half.bakapet;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

/** 通用的悬浮窗口管理器 支持自定义内容布局、动画方向、背景点击关闭等 */
public class OverlayWindow {

    public enum AnimDirection {
        TOP, // 从顶部滑入/滑出
        BOTTOM, // 从底部滑入/滑出
        CENTER // 淡入淡出 + 缩放（居中）
    }

    private final Activity activity;
    private final View contentView;
    private final AnimDirection animDirection;
    private final boolean dismissOnBackPressed;
    private final boolean dismissOnOutsideClick;

    private ViewGroup container; // 全屏容器（半透明背景 + 内容）
    private View overlayBg; // 半透明背景
    private ViewGroup contentContainer; // 内容容器（FrameLayout），用来做位移动画
    private boolean isShowing = false;

    private static final int DURATION = 300;

    private OverlayWindow(Builder builder) {
        this.activity = builder.activity;
        this.contentView = builder.contentView;
        this.animDirection = builder.animDirection;
        this.dismissOnBackPressed = builder.dismissOnBackPressed;
        this.dismissOnOutsideClick = builder.dismissOnOutsideClick;

        initView();
    }

    private void initView() {
        // 创建一个全屏 FrameLayout 作为根容器
        container = new FrameLayout(activity);
        container.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
        container.setVisibility(View.GONE);
        container.setClickable(true);

        // 半透明背景（用于淡入淡出）
        overlayBg = new View(activity);
        overlayBg.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT));
        overlayBg.setBackgroundColor(0x80000000);
        overlayBg.setAlpha(0f);
        container.addView(overlayBg);

        // 内容容器（FrameLayout）
        contentContainer = new FrameLayout(activity);
        contentContainer.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT));

        // 【修改1】为内容容器添加内边距，保证内容与屏幕边缘有距离
        int marginPx = (int) (16 * activity.getResources().getDisplayMetrics().density); // 16dp
        contentContainer.setPadding(marginPx, marginPx, marginPx, marginPx);

        // 根据动画方向设置初始位置
        switch (animDirection) {
            case TOP:
                contentContainer.setTranslationY(-getScreenHeight());
                break;
            case BOTTOM:
                contentContainer.setTranslationY(getScreenHeight());
                break;
            case CENTER:
                contentContainer.setScaleX(0.8f);
                contentContainer.setScaleY(0.8f);
                contentContainer.setAlpha(0f);
                break;
        }
        container.addView(contentContainer);

        // 将用户的内容布局添加到内容容器中
        contentContainer.addView(contentView);

        // 关键修复：拦截点击事件，防止穿透到背景蒙层
        contentView.setClickable(true);
        contentView.setFocusable(true);
        // 设置点击背景关闭
        if (dismissOnOutsideClick) {
            overlayBg.setOnClickListener(v -> dismiss());
        }

        // 设置返回键关闭
        if (dismissOnBackPressed) {
            container.setFocusableInTouchMode(true);
            container.requestFocus();
            container.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP && isShowing) {
                    dismiss();
                    return true;
                }
                return false;
            });
        }

        // 添加到 Activity 的根视图
        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(container);
    }

    private int getScreenHeight() {
        return activity.getResources().getDisplayMetrics().heightPixels;
    }

    public void show() {
        if (isShowing) return;
        isShowing = true;
        container.setVisibility(View.VISIBLE);

        overlayBg.animate()
                .alpha(1f)
                .setDuration(DURATION)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        switch (animDirection) {
            case TOP:
                contentContainer.animate()
                        .translationY(0)
                        .setDuration(DURATION)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                break;
            case BOTTOM:
                contentContainer.animate()
                        .translationY(0)
                        .setDuration(DURATION)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                break;
            case CENTER:
                contentContainer.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .alpha(1f)
                        .setDuration(DURATION)
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
                break;
        }
    }

    public void dismiss() {
        if (!isShowing) return;
        isShowing = false;

        overlayBg.animate()
                .alpha(0f)
                .setDuration(DURATION)
                .setInterpolator(new AccelerateInterpolator())
                .start();

        switch (animDirection) {
            case TOP:
                contentContainer.animate()
                        .translationY(-getScreenHeight())
                        .setDuration(DURATION)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> container.setVisibility(View.GONE))
                        .start();
                break;
            case BOTTOM:
                contentContainer.animate()
                        .translationY(getScreenHeight())
                        .setDuration(DURATION)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> container.setVisibility(View.GONE))
                        .start();
                break;
            case CENTER:
                contentContainer.animate()
                        .scaleX(0.8f)
                        .scaleY(0.8f)
                        .alpha(0f)
                        .setDuration(DURATION)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> container.setVisibility(View.GONE))
                        .start();
                break;
        }
    }

    public boolean isShowing() {
        return isShowing;
    }

    @SuppressWarnings("unchecked")
    public <T extends View> T findViewById(int id) {
        return contentView.findViewById(id);
    }

    public static class Builder {
        private final Activity activity;
        private View contentView;
        private int layoutResId;
        private AnimDirection animDirection = AnimDirection.TOP;
        private boolean dismissOnBackPressed = true;
        private boolean dismissOnOutsideClick = true;

        public Builder(Activity activity) {
            this.activity = activity;
        }

        public Builder setContentView(View view) {
            this.contentView = view;
            return this;
        }

        public Builder setContentView(int layoutResId) {
            this.layoutResId = layoutResId;
            return this;
        }

        public Builder setAnimDirection(AnimDirection direction) {
            this.animDirection = direction;
            return this;
        }

        public Builder setDismissOnBackPressed(boolean dismiss) {
            this.dismissOnBackPressed = dismiss;
            return this;
        }

        public Builder setDismissOnOutsideClick(boolean dismiss) {
            this.dismissOnOutsideClick = dismiss;
            return this;
        }

        public OverlayWindow build() {
            if (contentView == null && layoutResId != 0) {
                contentView = LayoutInflater.from(activity).inflate(layoutResId, null);
            }
            if (contentView == null) {
                throw new IllegalStateException("必须设置 contentView 或 layoutResId");
            }
            return new OverlayWindow(this);
        }
    }
}
```

---

## File: `java/com/cirno9half/bakapet/PetInstance.java`

```java
package com.cirno9half.bakapet;

import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import com.quickjs.JSContext;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PetInstance {

    private final PetService service;
    private final int instanceId;
    private final String assetPath;
    private final String petVarName;

    private final PetView petView;
    private final WindowManager wm;
    private final WindowManager.LayoutParams params;

    private final AnimationController animController;
    private final ActionController actionController;

    private boolean dragging = false;
    private final Map<String, Object> attrs = new HashMap<>();
    private final Handler mainHandler;

    // ---------- 触摸事件区分所需字段 ----------
    private static final int LONG_PRESS_TIMEOUT = 500;   // 长按判定时长（毫秒）
    private static final float TOUCH_SLOP = 10;          // 移动阈值，小于此距离视为点击
    private long downTime;
    private float downX, downY;
    private boolean hasMoved = false;
    private boolean isLongPressed = false;
    private Runnable longPressRunnable;
    private float savedDownParamsX, savedDownParamsY;    // 记录按下时的窗口坐标，便于拖动

    public PetInstance(PetService service, int instanceId, String assetPath,
                       String petVarName, Handler mainHandler, JSContext jsContext) {
        this.service = service;
        this.instanceId = instanceId;
        this.assetPath = assetPath;
        this.petVarName = petVarName;
        this.mainHandler = mainHandler;
        this.wm = service.getWm();

        // 视图
        petView = new PetView(service);
        params = new WindowManager.LayoutParams(
                150, 150,
                Build.VERSION.SDK_INT >= 26 ? 2038 : 2002,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        petView.setOnTouchListener(new TouchHandler());
        wm.addView(petView, params);

        // 设置初始位置到屏幕中央，防止被状态栏遮挡
        android.util.DisplayMetrics dm = service.getResources().getDisplayMetrics();
        params.x = (dm.widthPixels - params.width) / 2;
        params.y = (dm.heightPixels - params.height) / 2;
        wm.updateViewLayout(petView, params);

        // 控制器
        animController = new AnimationController(this, petView);
        actionController = new ActionController(this, jsContext, petVarName);

        // 注意：不在此处自动加载资源和启动循环，由 PetService.addPet() 在 instanceMap 注册后调用 init()
    }

    /** 公开的初始化方法，由 PetService 在添加实例后调用 */
    public void init() {
        loadResources();
        startLoop();
    }

    // --------------------------------------------------------
    // 触碰事件处理器（区分点击、长按、拖动）
    // --------------------------------------------------------
    private class TouchHandler implements View.OnTouchListener {
        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    hasMoved = false;
                    isLongPressed = false;
                    downTime = System.currentTimeMillis();
                    downX = e.getRawX();
                    downY = e.getRawY();
                    // 记录按下时的窗口坐标，用于后续拖动的增量计算
                    savedDownParamsX = params.x;
                    savedDownParamsY = params.y;

                    // 取消之前的长按任务（如果有）
                    if (longPressRunnable != null) {
                        mainHandler.removeCallbacks(longPressRunnable);
                    }

                    // 准备长按检测
                    longPressRunnable = () -> {
                        if (!hasMoved && !isLongPressed) {
                            isLongPressed = true;
                            actionController.onHold((int) downX, (int) downY);
                        }
                    };
                    mainHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT);
                    break;

                case MotionEvent.ACTION_MOVE:
                    float movedX = e.getRawX() - downX;
                    float movedY = e.getRawY() - downY;
                    float distance = (float) Math.hypot(movedX, movedY);

                    if (!isLongPressed && distance > TOUCH_SLOP) {
                        // 移动超过阈值，取消长按检测
                        if (longPressRunnable != null) {
                            mainHandler.removeCallbacks(longPressRunnable);
                        }
                        hasMoved = true;
                        dragging = true;
                    }

                    if (dragging || isLongPressed) {
                        // 拖动（长按后的移动也可拖动）
                        params.x = (int) (e.getRawX() - downX + savedDownParamsX);
                        params.y = (int) (e.getRawY() - downY + savedDownParamsY);
                        wm.updateViewLayout(petView, params);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                    // 取消长按任务
                    if (longPressRunnable != null) {
                        mainHandler.removeCallbacks(longPressRunnable);
                    }

                    if (!hasMoved && !isLongPressed) {
                        // 短按（点击）
                        actionController.onTouch((int) downX, (int) downY);
                    }
                    dragging = false;
                    break;

                case MotionEvent.ACTION_CANCEL:
                    if (longPressRunnable != null) {
                        mainHandler.removeCallbacks(longPressRunnable);
                    }
                    dragging = false;
                    break;
            }
            return true;
        }
    }

    // ---------- 资源加载与主循环 ----------
    private void loadResources() {
        try {
            JSONObject config = new JSONObject(readAsset(assetPath + "/pet.json"));
            if (config.has("size")) {
                int px = (int)(Integer.parseInt(config.getString("size").replace("dp", ""))
                        * service.getResources().getDisplayMetrics().density);
                params.width = params.height = px;
                mainHandler.post(() -> wm.updateViewLayout(petView, params));
            }
            JSONObject motions = config.getJSONObject("motions");
            for (Iterator<String> it = motions.keys(); it.hasNext(); ) {
                String key = it.next();
                animController.load(key, assetPath + "/motions/" + motions.getString(key));
            }
            JSONObject actions = config.getJSONObject("actions");
            for (Iterator<String> it = actions.keys(); it.hasNext(); ) {
                String key = it.next();
                if (!key.equals("default")) {
                    String script = readAsset(assetPath + "/actions/" + actions.getString(key));
                    actionController.load(key, script);
                }
            }
            if (actions.has("default")) {
                actionController.switchTo(actions.getString("default"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startLoop() {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                animController.update();
                actionController.update();
                mainHandler.postDelayed(this, 16);
            }
        });
    }

    private String readAsset(String path) throws Exception {
        try (InputStream is = service.getAssets().open(path)) {
            byte[] b = new byte[is.available()];
            is.read(b);
            return new String(b, "UTF-8");
        }
    }

    // ---------- 对外暴露的操作方法 ----------
    public void setPosition(int x, int y) {
        params.x = x; params.y = y;
        mainHandler.post(() -> wm.updateViewLayout(petView, params));
    }
    public void move(int dx, int dy) {
        params.x += dx; params.y += dy;
        mainHandler.post(() -> wm.updateViewLayout(petView, params));
    }
    public int getX() { return params.x; }
    public int getY() { return params.y; }
    public int getWidth() { return params.width; }
    public int getHeight() { return params.height; }
    public boolean isDragging() { return dragging; }
    public void setScaleX(float sx) { mainHandler.post(() -> petView.setScaleX(sx)); }
    public void setScale(float sx, float sy) {
        mainHandler.post(() -> { petView.setScaleX(sx); petView.setScaleY(sy); });
    }
    /** 获取当前 X 方向的缩放值（用于 JS 侧读取 scaleX） */
    public float getScaleX() {
        return petView.getScaleX();
    }
    /** 获取资源根路径（如 "pets/cirno"），供音频等功能使用 */
    public String getAssetPath() {
        return assetPath;
    }
    public void updateImg(String path) { mainHandler.post(() -> petView.updateImgFromAssets(path)); }
    public void setAttr(String key, Object val) { attrs.put(key, val); }
    public Object getAttr(String key) { return attrs.get(key); }
    public boolean hasAttr(String key) { return attrs.containsKey(key); }
    public void deleteAttr(String key) { attrs.remove(key); }

    public AnimationController getAnimationController() { return animController; }
    public ActionController getActionController() { return actionController; }
    public String getPetVarName() { return petVarName; }
    public int getInstanceId() { return instanceId; }

    public void destroy() {
        mainHandler.removeCallbacksAndMessages(null);
        wm.removeView(petView);
        actionController.destroy();
        petView.clearCache();
    }
}
```

---

## File: `java/com/cirno9half/bakapet/PetService.java`

```java
package com.cirno9half.bakapet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.WindowManager;
import androidx.core.app.NotificationCompat;
import com.quickjs.JSContext;
import com.quickjs.JSValueHelper;
import com.quickjs.QuickJS;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
// 在 PetService 顶部添加 import
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

public class PetService extends Service {

    private WindowManager wm;
    private QuickJS quickJS;
    private JSContext jsContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final Map<Integer, PetInstance> instanceMap = new ConcurrentHashMap<>();
    private int nextId = 0;
    // 类内部添加成员
    private BroadcastReceiver configReceiver;
    // 音频播放
    private SoundPool soundPool;
    private final Map<String, Integer> soundCache = new HashMap<>();

    /** 宠物 Java 侧代理，直接操作 PetInstance，无需 JS 调用。 */
    public static class Pet {
        private final PetInstance instance;

        Pet(PetInstance instance) {
            this.instance = instance;
        }

        public void setPosition(int x, int y) {
            instance.setPosition(x, y);
        }

        public void move(int dx, int dy) {
            instance.move(dx, dy);
        }

        public int getX() {
            return instance.getX();
        }

        public int getY() {
            return instance.getY();
        }

        public void setScale(float sx, float sy) {
            instance.setScale(sx, sy);
        }

        public void updateImg(String path) {
            instance.updateImg(path);
        }

        public void setAttr(String key, Object val) {
            instance.setAttr(key, val);
        }

        public Object getAttr(String key) {
            return instance.getAttr(key);
        }

        public boolean hasAttr(String key) {
            return instance.hasAttr(key);
        }

        public void deleteAttr(String key) {
            instance.deleteAttr(key);
        }

        public boolean isDragging() {
            return instance.isDragging();
        }

        public void destroy() {
            instance.destroy();
        }

        public int getInstanceId() {
            return instance.getInstanceId();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // 全局崩溃日志
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                java.io.File f = new java.io.File(getExternalFilesDir(null), "crash_log.txt");
                java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(f, true));
                e.printStackTrace(pw);
                pw.close();
            } catch (Exception ignored) {
            }
            System.exit(1);
        });

        // 前台通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String CHANNEL_ID = "pet_service_channel";
            NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID, "桌宠服务", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
            startForeground(1, new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Baka Pet 运行中")
                    .setContentText("多桌宠模式")
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .build());
        }

        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        // 初始化音频（SoundPool）
        AudioAttributes audioAttrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            soundPool = new SoundPool.Builder()
                    .setMaxStreams(4)
                    .setAudioAttributes(audioAttrs)
                    .build();
        } else {
            soundPool = new SoundPool(4, AudioManager.STREAM_MUSIC, 0);
        }

        // 初始化共享 JS 环境
        quickJS = QuickJS.createRuntime();
        jsContext = quickJS.createContext();

        // 注入屏幕尺寸
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        jsContext.executeVoidScript(String.format(
                "var device = { width: %d, height: %d };", metrics.widthPixels, metrics.heightPixels),
                "device.js");

        // 默认创建一只 cirno（可按需创建更多）
        addPet("pets/cirno");

        registerConfigReceiver();
    }

    // 在 onCreate() 末尾注册广播
    private void registerConfigReceiver() {
        configReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                updateDeviceInfo();
            }
        };
        IntentFilter filter = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
        registerReceiver(configReceiver, filter);
    }

    private void updateDeviceInfo() {
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        String script = String.format(
                "device.width = %d; device.height = %d;",
                metrics.widthPixels, metrics.heightPixels);
        jsContext.executeVoidScript(script, "device_update");
    }

    /** 为指定实例注册所有底层桥接方法（名称包含实例ID，防止冲突） */
    private void registerInstanceMethods(int instanceId) {
        String suffix = "_" + instanceId;

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null)
                    inst.setPosition((int) args.getDouble(0), (int) args.getDouble(1));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_setPosition" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.move((int) args.getDouble(0), (int) args.getDouble(1));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_move" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getX() : 0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getX" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getY() : 0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getY" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.setScaleX((float) args.getDouble(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_setScaleX" + suffix);

        // 获取 scaleX
        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getScaleX() : 1.0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getScaleX" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null)
                    inst.setScale((float) args.getDouble(0), (float) args.getDouble(1));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_setScale" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.updateImg(args.getString(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_updateImg" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null && inst.isDragging();
            } finally {
                JSValueHelper.close(args);
            }
        }, "_dragging" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null && inst.getAnimationController().isLastFrame();
            } finally {
                JSValueHelper.close(args);
            }
        }, "_isLastFrame" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getWidth() : 0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getImgWidth" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getHeight() : 0;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getImgHeight" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.setAttr(args.getString(0), args.getString(1));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_setAttr" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null ? inst.getAttr(args.getString(0)) : null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_getAttr" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                return inst != null && inst.hasAttr(args.getString(0));
            } finally {
                JSValueHelper.close(args);
            }
        }, "_hasAttr" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.deleteAttr(args.getString(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_deleteAttr" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.getAnimationController().switchTo(args.getString(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_animSwitchTo" + suffix);

        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) inst.getActionController().switchTo(args.getString(0));
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_actionSwitchTo" + suffix);

        // 播放音频桥接
        jsContext.registerJavaMethod((receiver, args) -> {
            try {
                String audioName = args.getString(0);
                PetInstance inst = instanceMap.get(instanceId);
                if (inst != null) playAudio(inst, audioName);
                return null;
            } finally {
                JSValueHelper.close(args);
            }
        }, "_playAudio" + suffix);
    }

    /** 根据桌宠屏幕位置播放双声道音频 */
    private void playAudio(PetInstance inst, String audioName) {
        String fullPath = inst.getAssetPath() + "/audio/" + audioName;
        Integer soundId = soundCache.get(fullPath);
        if (soundId == null) {
            try (AssetFileDescriptor afd = getAssets().openFd(fullPath)) {
                soundId = soundPool.load(afd, 1);
                soundCache.put(fullPath, soundId);
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        // 计算左右声道音量（基于宠物中心相对于屏幕中心的位置）
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        float screenW = dm.widthPixels;
        float petCenterX = inst.getX() + inst.getWidth() / 2f;
        float pan = (petCenterX - screenW / 2f) / (screenW / 2f); // -1（最左）到 1（最右）

        float leftVol = Math.min(1f, 1f - pan);
        float rightVol = Math.min(1f, 1f + pan);

        soundPool.play(soundId, leftVol, rightVol, 1, 0, 1.0f);
    }

    /** 在 JS 中创建该实例专属的 pet 对象 */
    private String createPetObject(int instanceId) {
        String varName = "__pet_" + instanceId;
        String suffix = "_" + instanceId;

        String script = String.format(
                "var %1$s = {\n" +
                        "  setPosition: _setPosition%2$s,\n" +
                        "  move: _move%2$s,\n" +
                        "  getX: _getX%2$s,\n" +
                        "  getY: _getY%2$s,\n" +
                        "  setScale: function(x, y) {\n" +
                        "    if (x === undefined) x = 1.0;\n" +
                        "    if (y === undefined) y = x;\n" +
                        "    _setScale%2$s(x, y);\n" +
                        "  },\n" +
                        "  updateImg: _updateImg%2$s,\n" +
                        "  setAttr: _setAttr%2$s,\n" +
                        "  getAttr: _getAttr%2$s,\n" +
                        "  hasAttr: _hasAttr%2$s,\n" +
                        "  deleteAttr: _deleteAttr%2$s,\n" +
                        "  animation: { switchTo: _animSwitchTo%2$s },\n" +
                        "  action: { switchTo: _actionSwitchTo%2$s },\n" +
                        "  playAudio: _playAudio%2$s,\n" +
                        "  _view: {},\n" +
                        "  getImageView: function() { return this._view; }\n" +
                        "};\n" +
                        "Object.defineProperty(%1$s, 'dragging', { get: _dragging%2$s });\n" +
                        "Object.defineProperty(%1$s, 'x', { get: _getX%2$s });\n" +
                        "Object.defineProperty(%1$s, 'y', { get: _getY%2$s });\n" +
                        "Object.defineProperty(%1$s.animation, 'isLastFrame', { get: _isLastFrame%2$s });\n" +
                        "Object.defineProperty(%1$s._view, 'width', { get: _getImgWidth%2$s });\n" +
                        "Object.defineProperty(%1$s._view, 'height', { get: _getImgHeight%2$s });\n" +
                        "Object.defineProperty(%1$s._view, 'scaleX', { \n" +
                        "  get: function() { return _getScaleX%2$s(); }, \n" +
                        "  set: function(v) { _setScaleX%2$s(v); } \n" +
                        "});",
                varName, suffix);

        jsContext.executeVoidScript(script, "pet_init_" + instanceId);
        return varName;
    }

    // ---------- 工厂方法 ----------
    public Pet addPet(String assetPath) {
        int id = nextId++;
        registerInstanceMethods(id);
        String petVarName = createPetObject(id);
        PetInstance inst = new PetInstance(this, id, assetPath, petVarName, mainHandler, jsContext);

        // 先将实例放入 Map（用于 JS 回调定位），再执行后续初始化
        instanceMap.put(id, inst);
        inst.init(); // 调用 PetInstance 的 init()（需确保该类有此方法）

        return new Pet(inst);
    }

    public void removePet(Pet pet) {
        PetInstance inst = instanceMap.remove(pet.getInstanceId());
        if (inst != null) {
            inst.destroy();
        }
    }

    public void removeAllPets() {
        for (int id : instanceMap.keySet()) {
            PetInstance inst = instanceMap.remove(id);
            if (inst != null) inst.destroy();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        removeAllPets();
        if (soundPool != null) {
            soundPool.release();
        }
        if (configReceiver != null) {
            unregisterReceiver(configReceiver);
        }

        if (jsContext != null) jsContext.close();
        if (quickJS != null) quickJS.close();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // 内部使用
    WindowManager getWm() {
        return wm;
    }

    JSContext getJsContext() {
        return jsContext;
    }

    Handler getMainHandler() {
        return mainHandler;
    }
}

```

---

## File: `java/com/cirno9half/bakapet/PetView.java`

```java
package com.cirno9half.bakapet;

import android.content.Context;
import android.graphics.*;
import android.util.LruCache;
import android.view.View;
import java.io.InputStream;

public class PetView extends View {
    private Bitmap currentBitmap;
    private final Paint paint;
    private final LruCache<String, Bitmap> bitmapCache;

    // 帧率相关变量
    private long lastFrameTimeNs = 0;
    private float fps = 0f;
    private final Paint textPaint;
    private final Paint bgPaint;
    private final Rect textBounds = new Rect();

    public PetView(Context context) {
        super(context);
        paint = new Paint();
        paint.setFilterBitmap(false); // 保持像素锐利
        paint.setAntiAlias(false);

        // 复刻原代码的 _maxCacheSize = 70
        bitmapCache = new LruCache<String, Bitmap>(70) {
            @Override
            protected void entryRemoved(boolean evicted, String key, Bitmap oldValue, Bitmap newValue) {
                if (oldValue != null && oldValue != currentBitmap && !oldValue.isRecycled()) {
                    oldValue.recycle();
                }
            }
        };

        // 初始化文字画笔（白色，带阴影）
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40);
        textPaint.setShadowLayer(2, 0, 0, Color.BLACK);

        // 初始化背景画笔（半透明黑色）
        bgPaint = new Paint();
        bgPaint.setColor(Color.argb(128, 0, 0, 0));
        bgPaint.setStyle(Paint.Style.FILL);
    }

    public void updateImgFromAssets(String path) {
        Bitmap bmp = bitmapCache.get(path);
        if (bmp == null || bmp.isRecycled()) {
            try (InputStream is = getContext().getAssets().open(path)) {
                bmp = BitmapFactory.decodeStream(is);
                if (bmp != null) bitmapCache.put(path, bmp);
            } catch (Exception e) { e.printStackTrace(); }
        }
        this.currentBitmap = bmp;
        postInvalidate(); // 触发重绘
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // 清空画布，防止透明通道叠加残影
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        // 绘制宠物图片
        if (currentBitmap != null && !currentBitmap.isRecycled()) {
            float scale = Math.min((float) getWidth() / currentBitmap.getWidth(),
                                   (float) getHeight() / currentBitmap.getHeight());
            float dstW = currentBitmap.getWidth() * scale;
            float dstH = currentBitmap.getHeight() * scale;
            float dx = (getWidth() - dstW) / 2f;
            float dy = (getHeight() - dstH) / 2f;

            Rect srcRect = new Rect(0, 0, currentBitmap.getWidth(), currentBitmap.getHeight());
            RectF dstRect = new RectF(dx, dy, dx + dstW, dy + dstH);
            canvas.drawBitmap(currentBitmap, srcRect, dstRect, paint);
        }

        // // 计算并更新帧率（使用指数平滑）
        // long nowNs = System.nanoTime();
        // if (lastFrameTimeNs != 0) {
            // float deltaSec = (nowNs - lastFrameTimeNs) / 1_000_000_000.0f;
            // if (deltaSec > 0) {
                // float instantFps = 1.0f / deltaSec;
                // if (fps == 0) {
                    // fps = instantFps;
                // } else {
                    // fps = fps * 0.9f + instantFps * 0.1f; // 平滑因子 0.1
                // }
            // }
        // }
        // lastFrameTimeNs = nowNs;

        // // 绘制帧率文字（左上角）
        // String fpsText = String.format("FPS: %.1f", fps);
        // textPaint.getTextBounds(fpsText, 0, fpsText.length(), textBounds);

        // int padding = 10;
        // int textWidth = textBounds.width();
        // int textHeight = textBounds.height();
        // int x = padding;
        // int y = padding + textHeight; // baseline 位置

        // 绘制半透明背景矩形
        //canvas.drawRect(x - padding / 2,
                        // y - textHeight - padding / 2,
                        // x + textWidth + padding / 2,
                        // y + padding / 2,
                        // bgPaint);
        // 绘制文字
        //canvas.drawText(fpsText, x, y, textPaint);
    }

    public void clearCache() {
        bitmapCache.evictAll();
    }
}
```

---

