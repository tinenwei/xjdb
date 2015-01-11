package com.sun.tools.example.debug.tty;

import java.util.*;
import java.io.*;
import org.python.util.PythonInterpreter; 
import org.python.core.*; 
import java.util.Properties;

public class JythonManager implements ScriptManager {
    public static TTY tty;
    static private JythonManager jym = null;    
    PrintStream console = System.out;
    static private PythonInterpreter interpreter ;

    public JythonManager(Object tty) {
        this.tty = (TTY) tty;
        jym = this;
    }

    public JythonManager() {}    

    public void initialize(TTY tty) {
        try {
            
            Properties props =new Properties();  
            props.setProperty("python.path","./");
            PythonInterpreter.initialize(System.getProperties(),props,null);
            interpreter = new PythonInterpreter();
            this.tty=tty;
            jym = this;
            interpreter.set("jdb", this);
                
            String prePycmd="def e(expr): return jdb.expr(expr)\n";
            prePycmd += "def _(cmd): jdb.cmd(cmd)\n";
            prePycmd += "def __(cmd): return jdb.cmdret(cmd)\n";
            prePycmd += "def _c(cmd): jdb.cmdPassPy(cmd)\n";
            prePycmd += "def __c(cmd): return jdb.cmdretPassPy(cmd)\n";
            prePycmd += "def _p(str): return jdb.println(str)\n";
            prePycmd += "def _pp(str): return jdb.print(str)\n";
            prePycmd += "def load(str): return jdb.load(str)\n";
            prePycmd += "def syscmd(str): return jdb.syscmd(str)";
            interpreter.exec(prePycmd);   

            InputStream is = TTY.class.getClassLoader().getResourceAsStream("py/init.py");
            if (is != null) {
                System.out.println("execute /py/init.py ...");            
                interpreter.execfile(is);             
            }
        } catch (Exception ex) {    
            ex.printStackTrace();  
        }            
            
    }

    static public JythonManager getInstance() {
        return jym;
    }

    public static PythonInterpreter getInterpreter(){
        return interpreter;
    }

    @Override
    public boolean readScriptFile(String filename) {
        try {                        
            File fh = new File(filename);  
            if (fh.canRead()) {
                MessageOutput.println("*** Reading commands from", fh.getCanonicalPath());                    
                InputStream is = new FileInputStream(fh);        
                String pyFilename="xjdbPyFile=\"" + fh.getAbsolutePath() + "\"";
                interpreter.exec(pyFilename);  
                interpreter.execfile(is);     
            }        
        
        } catch (Exception ex) {    
            ex.printStackTrace();  
            return false;
        }            

        return true;        
    }

    @Override
    public String getScriptSuffix() {
        return ".jdbrc.py";
    }    

    @Override
    public boolean executeString(String s) {
        boolean res;
        PyObject result = interpreter.eval(s);
        if (result == null)
            res = false;
        else
            res = Py.py2boolean(result);

        return res;
    }

    Integer getPyInteger(String attr, PyObject pyObj) {
        PyObject obj = pyObj.__getattr__(attr);
        if (obj == null) {
            System.out.println("The value of " + attr + " attribute is wrong!!");
            return null;
        }            
        return Py.py2int(obj);
    }
    
    Boolean getPyBoolean(String attr, PyObject pyObj) {
        PyObject obj = pyObj.__getattr__(attr);
        if (obj == null) {
            System.out.println("The value of " + attr + " attribute is wrong!!");
            return null;
        }
        return Py.py2boolean(obj);
    }

    String getPyString(String attr, PyObject pyObj)
    {
        String ret = null;
        PyObject obj = pyObj.__getattr__(attr);
        if (obj instanceof PyString) 
            ret = ((PyString)obj).getString();
        else        
            System.out.println("The value of " + attr + " attribute is wrong!!");
        return ret;        
    }

    
    PyFunction getPyFunction(String attr, PyObject pyObj) {
        PyFunction ret = null;
        PyObject obj = pyObj.__getattr__(attr);
        if (obj instanceof PyFunction) 
            ret = (PyFunction)obj;
        else        
            System.out.println("The value of "+attr+" attribute is wrong!!");
        return ret;        
    }    

    boolean showJscommandAddMessage = false;
    public void setJscommandAddMessage(boolean value) {
        showJscommandAddMessage = value;
    }

    public int addPycommand(PyObject pyObj) {
        String cmd = getPyString("cmd", pyObj);
        PyFunction onPyommand = getPyFunction("onCommand", pyObj);
        if (cmd == null || onPyommand == null || cmd.length() <= 0) {
            System.out.println("Failed to add addPycommand !!");
            return -1;
        }        

        if (showJscommandAddMessage)
            System.out.println("add command:" + cmd);

        String cmdabbrev = getPyString("cmdabbrev", pyObj);
        Integer completer = getPyInteger("completer", pyObj);
        Boolean _disconnected = getPyBoolean("disconnected", pyObj);
        Boolean isOverride = getPyBoolean("override", pyObj);
        Boolean _readonly = getPyBoolean("readonly", pyObj);
 
        String disconnected = "y";
        String readonly = "n";
         
        if (_disconnected != null && !_disconnected )
             disconnected = "n";
             
        if (_readonly != null && _readonly )
             readonly = "y"; 

        if (isOverride == null || !isOverride ) {
            if (tty.isCommand(cmd) > 0) {
                System.out.println("The command already exists !!");
                return -1;
            }

            for(int i = 0 ; i < tty.abbrCmdTable.length; i++) {
                if (cmdabbrev!=null && cmdabbrev.equals(tty.abbrCmdTable[i][1])) {
                    System.out.println("The abbreviation of command already exists !!");
                    return -1;            
                }
            }
        }
            
        if (completer != null && completer != -1) {
            CommandCompleter.completerCmdMap.put(cmd, (int)completer); 
            if (cmdabbrev!=null && cmdabbrev.length() > 0)
                CommandCompleter.completerCmdMap.put(cmdabbrev, (int)completer); 
        }

        String[] cmdinfo = new String[4];
        cmdinfo[0] = cmd;
        cmdinfo[1] = cmdabbrev;
        cmdinfo[2] = disconnected;
        cmdinfo[3] = readonly;        
        tty.scriptCommandList.put(cmd, cmdinfo); 
        tty.scriptCommandFuncMap.put(cmd, new JythonObject(pyObj));
        return 0;
    }

    int stopEventId = 0;
    int exitedEventId = 0;
    int continueEventId = 0;
    
    public int addStopEvent(PyObject pyObj) {
    
        PyFunction onEvent = getPyFunction("onEvent",pyObj);        
        if (onEvent == null ) {
            System.out.println("Failed to add Stop Event !!");
            return -1;
        }    

        for( ScriptObject sobj : tty.stopEventList) {
            JythonObject jobj = (JythonObject) sobj;
            PyFunction listFObj = getPyFunction("onEvent",jobj.getObj());
            if (listFObj.equals(onEvent)) {            
                System.out.println("Stop Event function already exists !!");
                return -1;
            }
        }

        tty.stopEventList.add(new JythonObject(pyObj));
        
        return 0;
    }

    public int removeStopEvent(PyObject pyObj){
        int index = -1;
        int i = 0;

        for(ScriptObject sobj : tty.stopEventList) {
            JythonObject jobj = (JythonObject)sobj;
            PyFunction listFObj = getPyFunction("onEvent",jobj.getObj());
            if (listFObj.equals(pyObj)) {            
                index = i;
                break;
            }
            i++;
        }
        
        if (index != -1) {
            tty.stopEventList.remove(index);
            return 0;
        } else {
            System.out.println("Stop Event function doesn't exist !!");
            return -1;
        }
    }

    
    public int addExitedEvent(PyObject pyObj){
        PyFunction onEvent = getPyFunction("onEvent",pyObj);        
        if (onEvent == null ) {
            System.out.println("Failed to add Exited Event !!");
            return -1;
        }    

        for(ScriptObject sobj : tty.exitedEventList) {
            JythonObject jobj = (JythonObject)sobj;
            PyFunction listFObj = getPyFunction("onEvent",jobj.getObj());
            if (listFObj.equals(onEvent)) {            
                System.out.println("Exited Event function already exists !!");
                return -1;
            }
        }

        tty.exitedEventList.add(new JythonObject(pyObj));
        
        return 0;      
    }

    public int removeExitedEvent(PyObject pyObj) {
        int index = -1;
        int i = 0;

        for (ScriptObject sobj : tty.exitedEventList) {
            JythonObject jobj = (JythonObject)sobj;
            PyFunction listFObj = getPyFunction("onEvent",jobj.getObj());
            if (listFObj.equals(pyObj)) {            
                index = i;
                break;
            }
            i++;
        }
        
        if (index != -1) {
            tty.exitedEventList.remove(index);
            return 0;
        } else {
            System.out.println("Exited Event function doesn't exist !!");
            return -1;
        }
    }


    public int addContinueEvent(PyObject pyObj) {
        PyFunction onEvent = getPyFunction("onEvent",pyObj);        
        if (onEvent == null) {
            System.out.println("Failed to add Continue Event !!");
            return -1;
        }    

        for (ScriptObject sobj : tty.continueEventList) {
            JythonObject jobj = (JythonObject) sobj;
            PyFunction listFObj = getPyFunction("onEvent",jobj.getObj());
            if (listFObj.equals(onEvent)) {            
                System.out.println("Continue Event function already exists !!");
                return -1;
            }
        }

        tty.continueEventList.add(new JythonObject(pyObj));        
        return 0;      
    }    

    public int removeContinueEvent(PyObject pyObj) {
        int index = -1;
        int i = 0;

        for(ScriptObject sobj : tty.continueEventList) {
            JythonObject jobj = (JythonObject) sobj;
            PyFunction listFObj = getPyFunction("onEvent",jobj.getObj());
            if (listFObj.equals(pyObj)) {            
                index = i;
                break;
            }
            i++;
        }
        
        if (index != -1) {
            tty.continueEventList.remove(index);
            return 0;
        } else {
            System.out.println("Continue Event function doesn't exist !!");
            return -1;
        }    

    }

    public int addBreakpointScriptFunc(int id, PyObject pyObj) {
        EventRequestSpecList.Property p = (EventRequestSpecList.Property)Env.specList.eventRequestSpecsMap().get(id);
        if (p == null) {
            System.out.println("id is wrong !!");
            return -1;
        }
        
        PyFunction onBreakPoint = getPyFunction("onBreakPoint", pyObj);
        if (onBreakPoint == null) {
            System.out.println("Failed to add breakpoint !!");
            return -1;
        }

        PyObject pyfile = interpreter.get("xjdbJsFile");
        String pyFilename = null;
        
        if (pyfile != null && pyfile instanceof PyString)
            pyFilename = ((PyString)pyfile).getString();

        p.scriptObj = new JythonObject(pyObj);
        
        if (p.cmdList == null)
            p.cmdList = new ArrayList();
    
        if (pyFilename != null)
            p.cmdList.add("[script] " + pyFilename);   
        else
            p.cmdList.add("[script]");          

        return p.id;        
    }    

    public int addBreakpoint(PyObject pyObj) {
        String breakpoint = getPyString("breakpoint",pyObj);
        PyFunction onBreakPoint = getPyFunction("onBreakPoint",pyObj);
        if (breakpoint == null || onBreakPoint == null) {
            System.out.println("Failed to add breakpoint !!");
            return -1;
        }

        Boolean temporary = getPyBoolean("temporary",pyObj);        
        if (temporary == null)
            temporary = false;

        String eventType = getPyString("eventType",pyObj);
        if (eventType == null) {
            System.out.println("Failed to add breakpoint !!");
            return -1;
        }

        Commands evaluator = new Commands(this.tty);  
        if (eventType.equals("breakpoint")) {        
            StringTokenizer t = TTY.parseStopCommand(new StringTokenizer(breakpoint));                     
            boolean res = evaluator.commandStop(t, true, temporary);

            if (!res) {
                System.out.println("Failed to set breapoint!!");            
                return -1;
            }
        } else if (eventType.equals("watchpoint")) {
            boolean res = evaluator.commandWatch(new StringTokenizer(breakpoint), temporary);
            if (!res) {
                System.out.println("Failed to set watchpoint!!");            
                return -1;
            }
        }

        Boolean enable = getPyBoolean("enable",pyObj);        
        if (enable != null && !enable)
            tty.executeCommand(new StringTokenizer("disable !!"));   

        EventRequestSpecList.Property p = Commands.getLastBreakPoint();
        
        if (p == null) {
            System.out.println("The property of breakpoint is null !!");            
            return -1;
        }

        p.scriptObj = new JythonObject(pyObj);    
        PyObject pyfile = interpreter.get("xjdbJsFile");
        
        String pyFilename=null;
        
        if (pyfile != null && pyfile instanceof PyString)
            pyFilename = ((PyString)pyfile).getString();

         if (p.cmdList == null)
            p.cmdList = new ArrayList();

        if (pyFilename != null)
            p.cmdList.add("[script] " + pyFilename);   
        else
            p.cmdList.add("[script]");   
        
        return p.id;        
    } 



    public void println(String s) {
        System.out.println(s);
    }

    public void print(String s) {
        System.out.print(s);
    }   

    public void cmd(String cmd) {
        StringTokenizer t = new StringTokenizer(cmd);
        tty.executeCommand(t);        
    }   

    public void cmdPassPy(String cmd) {
        tty.passScriptCommand = true;
        cmd(cmd);
    }   
    
    public String cmdret(String cmd) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream(512);
        PrintStream ps = new PrintStream(bs);
        System.setOut(ps);
        StringTokenizer t = new StringTokenizer(cmd);
        tty.executeCommand(t);  
        System.setOut(console);
        return new String(bs.toByteArray());        
    }     
    
    public String cmdretPassPy(String cmd) {
        tty.passScriptCommand = true;
        return cmdret(cmd);    
    } 

    public String syscmd(String syscmd, boolean showMessage) {
        if (!showMessage) {
            ByteArrayOutputStream bs = new ByteArrayOutputStream(512);
            PrintStream ps = new PrintStream(bs);
            System.setOut(ps);        
        }
        tty.executeSystemCmd(syscmd);
        System.setOut(console);
        return tty.getSystemCmdResult();    
    } 

    public String getSyscmdError() {        
         return tty.getSystemCmdError();    
    } 

    public boolean initload(String filename) {
        try {        
            File fh = new File(TTY.class.getClassLoader().getResource(filename).getFile()); 
            if (fh.canRead()) {
                System.out.println("jdb.initload: "+fh.getAbsolutePath());            
                InputStream is = new FileInputStream(fh);        
                interpreter.execfile(is);
            }                
        } catch (Exception ex) { 
            ex.printStackTrace();  
            return false;
        }   
        
        return true;        
    }     


    public boolean load(String filename) {
        try {            
            File fh = new File(filename);  
            if (fh.canRead()) {
                System.out.println("jdb.load: "+fh.getAbsolutePath());
                InputStream is = new FileInputStream(fh);        
                interpreter.execfile(is); 
            } else {
                System.out.println("jdb.load: "+fh.getAbsolutePath()+" can't be read.");
            }
        
        }catch (Exception ex) { 
            System.out.println("jdb.load: error");
            ex.printStackTrace();  
            return false;
        }
        
        return true;
    }    
    
    public Object expr(String expr){
        StringTokenizer t = new StringTokenizer(expr);
        Commands evaluator = new Commands(this.tty);
        String s =  evaluator.commandExpr(t);    
        return s;
    }
    
    public String getLocation() {
        return tty.getCurrentLocation();            
    }       

    public String getCurrentThreadName() {
        return tty.getCurrentThreadName();            
    }    


    public String getCurrentSourceLocation() {
        return tty.getCurrentSourceLocation();            
    }    


    public String getCurrentLocationClassName() {
        String className = null;
        className = tty.getCurrentLocationClassName();
        if (className != null)
            return className;
        else 
            return "";
    }

    public String getClassLocations(String className, int lineno) {
        return tty.getClassLocations(className,lineno);
    }        

    public int getLastBreakPointId() {
        EventRequestSpecList.Property p = Commands.getLastBreakPoint();
        if (p != null)
            return p.id;
        else
            return -1;
    }    

    void throwPythonScriptException(String message, int cause, String where)
    {
        throw new PyException(Py.None, message);            
    }

}
