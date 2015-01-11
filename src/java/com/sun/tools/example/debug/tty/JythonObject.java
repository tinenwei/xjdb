package com.sun.tools.example.debug.tty;
import org.python.util.PythonInterpreter; 
import org.python.core.*; 

public class JythonObject implements ScriptObject {
    private PyObject pyObj = null;
    
    public JythonObject (PyObject obj) {
        pyObj = obj;        
    }

    public PyObject getObj() {
        return pyObj;
    }

    @Override
    public boolean hasProperty(String Name) {
        PyObject obj = pyObj.__getattr__(Name);
         if (obj != null)
            return true;
         return false;
    }
    
    @Override
    public boolean call(String functionName, Object[] args) {
        PyObject functionObj= pyObj.__getattr__(functionName);
        if (functionObj != null && (functionObj instanceof PyFunction)) {
            PyObject[] pargs = null;
            
            if (args != null) {
                pargs = new PyObject[args.length];
                for (int i = 0; i < args.length; i++) {
                    if (args[i] instanceof Integer)
                        pargs[i] = new PyInteger((Integer)args[i]);
                    else if (args[i] instanceof String)
                        pargs[i] = new PyString((String)args[i]);
                    else if (args[i] instanceof Boolean)
                         pargs[i] = new PyBoolean((Boolean)args[i]);
                         
                 }
            }
            PyObject ft = null;
            if (pargs != null)
                 ft = functionObj.__call__(pargs);
            else 
                ft = functionObj.__call__();

            if (ft == null || ft == Py.None)
                return  true;
            else            
                return Py.py2boolean(ft);

        } else
            return false;
    }

    @Override
    public String toString(String ObjectName) {
        PyObject obj = pyObj.__getattr__(ObjectName);
         if (obj != null)
            return obj.toString();
         return null;
    }    
}
