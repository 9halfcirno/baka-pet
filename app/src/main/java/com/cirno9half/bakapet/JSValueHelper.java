package com.quickjs;

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