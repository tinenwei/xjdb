package com.sun.tools.example.debug.tty;

interface ScriptObject {
    boolean hasProperty(String Name);
    boolean call(String functionName, Object[] args);
    String toString(String ObjectName);
}
