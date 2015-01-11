package com.sun.tools.example.debug.tty;

interface ScriptManager {
    boolean readScriptFile(String filename);
    boolean executeString(String s);
    void initialize(TTY tty);
    String getScriptSuffix();    
}
