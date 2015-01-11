package com.sun.tools.example.debug.tty;
import java.lang.reflect.Method;

public abstract class  ScriptManagerBridge {
    private static ScriptManager scm = null;
    public static  void init(TTY tty) throws Exception {
        try { 
            if (exists("com.sun.tools.example.debug.tty.JythonManager")) {
                scm = (ScriptManager)Class.forName("com.sun.tools.example.debug.tty.JythonManager").newInstance(); 
                scm.initialize(tty);
            } else if (exists( "com.sun.tools.example.debug.tty.JavaScriptManager")) {
                Class cls = Class.forName("com.sun.tools.example.debug.tty.JavaScriptManager");
                Method m = cls.getDeclaredMethod("init", TTY.class);
                m.invoke(null, tty);
                m = cls.getDeclaredMethod("getInstance");
                scm = (ScriptManager) m.invoke(null);
            } else {
                throw new Exception("Can't find Script Manager class !!"); 
            }
            
        } catch (Exception exception) { 
            throw new Exception("Can't find Script Manager class !!"); 
        } 
    }

    public static boolean exists(String className) { 
        try { 
            Class.forName(className); 
            return true; 
        } catch (ClassNotFoundException exception) { 
            return false; 
        } 
    } 

    public static boolean readScriptFile(String filename) {
        return scm.readScriptFile(filename);
    }

    public static String getScriptSuffix() {
        return scm.getScriptSuffix();
    }

    public static boolean executeString(String s) {
        return scm.executeString(s);
    }
}
