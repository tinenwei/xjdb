package com.sun.tools.example.debug.tty;
import org.mozilla.javascript.*; 

public class JavaScriptObject implements ScriptObject {
    private ScriptableObject jsObj = null;
    Scriptable scope = null;
    
    public JavaScriptObject (ScriptableObject obj, Scriptable scope) {
        this.jsObj = obj;    
        this.scope = scope;
    }

    public ScriptableObject getObj() {
        return jsObj;
    }

    @Override
    public boolean hasProperty(String Name) {
        Object obj = jsObj.get(Name, jsObj);
        if (obj != Scriptable.NOT_FOUND)
            return true;    
        return false;
    }

    @Override
    public boolean call(String functionName, Object[] args) {
        Object functionObj = jsObj.get(functionName,jsObj);
        
        if (functionObj != Scriptable.NOT_FOUND && (functionObj instanceof Function)) {
            Context cx = Context.enter();
            try {
                Object ft = (Object)((Function)functionObj).call(cx, jsObj, jsObj, args);

                if (ft == Scriptable.NOT_FOUND || !(ft instanceof Boolean)) {
                    return true;
                } else {
                    return Context.toBoolean(ft);
                }                    
                        
            } catch (Exception ex) {    
                ex.printStackTrace();  
            } finally {
                Context.exit();
            }
        }        
        return false;         
    }

    @Override
    public String toString(String ObjectName) {
        String ret = null;
        Context cx = Context.enter(); 
        Object obj = jsObj.get(ObjectName,jsObj);
        if (obj != null && obj != Scriptable.NOT_FOUND )
             ret = Context.toString(obj);
        Context.exit(); 
        return ret;
    }    
}
