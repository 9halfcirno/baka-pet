package com.cirno9half.bakapet;

import org.json.JSONObject;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class AnimationController {
    private final PetInstance pet;
    private final PetView view;
    private final boolean external;
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

    public AnimationController(
            PetInstance pet,
            PetView view,
            boolean external) {

        this.pet = pet;
        this.view = view;
        this.external = external;
    }

    public void pause() {
        isPause = true;
    }

    public void resume() {
        isPause = false;
    }

    public void load(String id, String path) {
        try {
            InputStream is;
            if (external) {
                is = new java.io.FileInputStream(
                path + "/meta.json"
                );
            } else {
                is = view.getContext()
                        .getAssets()
                        .open(path + "/meta.json");
            }
            try (InputStream input = is;
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    bos.write(buffer, 0, len);
                }
                JSONObject json = new JSONObject(
                bos.toString("UTF-8")
                );
                Anim a = new Anim();
                a.frameNum = json.getInt("frame_num");
                a.interval = json.optInt("frame_interval", 100);
                a.next = json.optString("next", null);
                a.baseUrl = path;
                a.cycle = json.optBoolean("cycle", true);
                anims.put(id, a);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
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