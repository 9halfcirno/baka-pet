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