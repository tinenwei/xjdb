package com.sun.tools.example.debug.tty;

import java.util.*;
import java.io.*;
import org.mozilla.javascript.*; 
import org.mozilla.javascript.annotations.JSFunction;
import org.mozilla.javascript.annotations.JSConstructor;
import org.mozilla.javascript.annotations.JSGetter;

public class JavaScriptManager  extends ScriptableObject implements ScriptManager {
    public static TTY tty;
    static private JavaScriptManager jsm = null;
    static private Scriptable scope;    
    PrintStream console = System.out;

    @JSConstructor
    public JavaScriptManager(Object tty) {
        this.tty = (TTY) tty;
        jsm = this;
    }

    public JavaScriptManager() {
    }

    public void initialize(TTY tty) {
    }

    static public void  init( TTY tty)
    {
        Context cx = Context.enter();           
            
        try {
            scope = cx.initStandardObjects();                                

            Object jsOut = Context.javaToJS(System.out, scope);
            ScriptableObject.putProperty(scope, "out", jsOut);              

            ScriptableObject.defineClass(scope, JavaScriptManager.class);
            
            Object[] arg = {tty};
            Scriptable jdb = cx.newObject(scope, "JavaScriptManager", arg);
            scope.put("jdb", scope, jdb);

            String preJscmd="function $(expr){ return jdb.expr(expr);};";
            preJscmd += "function _(cmd){jdb.cmd(cmd);};";
            preJscmd += "function __(cmd){return jdb.cmdret(cmd);};";
            preJscmd += "function _c(cmd){jdb.cmdPassJs(cmd);};";
            preJscmd += "function __c(cmd){return jdb.cmdretPassJs(cmd);};";            
            preJscmd += "function _p(str){return out.println(str);};";
            preJscmd += "function _pp(str){return out.print(str);};";
            preJscmd += "function load(str){return jdb.load(str);};";
            preJscmd += "function syscmd(str){return jdb.syscmd(str);};";
            Object result = cx.evaluateString(scope, preJscmd, "preJscmd", 1, null);                    

            InputStream is = TTY.class.getClassLoader().getResourceAsStream("js/init.js");            

            if (is!=null) {
                Reader reader = new InputStreamReader(is);
                System.out.println("execute /js/init.js ...");
                cx.evaluateReader(scope, reader, "init.js", 1, null);
            }
                
        } catch (Exception ex) {
            ex.printStackTrace();  
        } finally {
            Context.exit();
        }

    }

    static public ScriptManager getInstance() {
        return jsm;
    }

    public static Scriptable getScope() {
        return scope;
    }

    @Override
    public boolean readScriptFile(String filename) {
        Context cx = Context.enter();
        try {
        
            Scriptable scope = JavaScriptManager.getScope();                            
            File fh = new File(filename);  
            if (fh.canRead()) {
                MessageOutput.println("*** Reading commands from", fh.getCanonicalPath());
                FileReader fr = new FileReader(fh);     
                String jsFileName="var xjdbJsFile=\"" + fh.getAbsolutePath() + "\";";
                Object result = cx.evaluateString(scope, jsFileName, "<jsFileName>", 1, null);    
                cx.evaluateReader(scope, fr, fh.getAbsolutePath(), 1, null);
            }        
        
        } catch (Exception ex) {    
            ex.printStackTrace();  
            return false;
        } finally {        
            Context.exit();
        }                    
        
        return true;
    }

    @Override
    public String getScriptSuffix() {
        return ".jdbrc.js";
    }

    @Override
    public boolean executeString(String s) {
        boolean res = false;
        Context cx = Context.enter();
        Scriptable scope = JavaScriptManager.getScope();
        Object result = cx.evaluateString(scope, s, "s", 1, null);  
        
        if (result == Scriptable.NOT_FOUND || !(result instanceof Boolean))
            res = false;          
        else
            res = (boolean) Context.toBoolean(result);
            
        Context.exit();
        return res;
    }

    @Override  
    public String getClassName() {
        return "JavaScriptManager";  
    }  

    Integer getPropertyInteger(String property, ScriptableObject scrpObj) {
        Object obj = scrpObj.get(property, scrpObj);        
        
        if (obj == Scriptable.NOT_FOUND || !(obj instanceof Number)) {
            System.out.println("The value of "+property+" property is wrong!!");
            return null;
        } else {
            if (obj instanceof Double)
                return new Integer(((Double)obj).intValue());
            else if (obj instanceof Long)
                return new Integer(((Long)obj).intValue());                
            else if (obj instanceof Integer)
                return (Integer) obj;
        }
        
        return null;        
    }

    String getPropertyString(String property, ScriptableObject scrpObj) {
        Object obj = scrpObj.get(property, scrpObj);
        if (obj == Scriptable.NOT_FOUND || !(obj instanceof String)) {
            System.out.println("The value of "+property + " property is wrong!!");
            return null;
        } else
            return Context.toString(obj);
    }

    Boolean getPropertyBoolean(String property, ScriptableObject scrpObj) {
        Object obj = scrpObj.get(property, scrpObj);
        if (obj == Scriptable.NOT_FOUND || !(obj instanceof Boolean)) {
            System.out.println("The value of " + property + " property is wrong!!");
            return null;
        }else
            return Context.toBoolean(obj);
    }

    Function getPropertyFunction(String property, ScriptableObject scrpObj) {
        Object obj = scrpObj.get(property, scrpObj);
        if (obj == Scriptable.NOT_FOUND || !(obj instanceof Function)) {
            System.out.println("The value of " + property + " property is wrong!!");
            return null;
        }else
            return (Function)obj;
    }

    boolean showJscommandAddMessage = false;

    @JSFunction
    public void setJscommandAddMessage(boolean value) {
        showJscommandAddMessage = value;
    }

    @JSFunction
    public int addJscommand(ScriptableObject scrpObj) {
        String cmd = getPropertyString("cmd",scrpObj);
        Function onJsommand = getPropertyFunction("onCommand",scrpObj);
        if (cmd == null || onJsommand == null || cmd.length() <= 0) {
            System.out.println("Failed to add jscommand !!");
            return -1;
        }        

        if (showJscommandAddMessage)
            System.out.println("add command:" + cmd);
        
        String cmdabbrev= getPropertyString("cmdabbrev",scrpObj);
        Integer completer= getPropertyInteger("completer",scrpObj);
        Boolean _disconnected= getPropertyBoolean("disconnected",scrpObj);
        Boolean isOverride= getPropertyBoolean("override",scrpObj);
        Boolean _readonly= getPropertyBoolean("readonly",scrpObj);

        String disconnected = "y";
        String readonly = "n";
        
        if (_disconnected != null && !_disconnected )
            disconnected = "n";
            
        if (_readonly != null && _readonly )
            readonly = "y";    

        if (isOverride==null || !isOverride ) {
            if (tty.isCommand(cmd) > 0) {
                System.out.println("The command already exists !!");
                return -1;
            }

            for (int i = 0 ; i < tty.abbrCmdTable.length ; i++) {
                if (cmdabbrev != null && cmdabbrev.equals(tty.abbrCmdTable[i][1])) {
                    System.out.println("The abbreviation of command already exists !!");
                    return -1;            
                }
            }
        }
        
        
        if (completer != null && completer != -1) {
            CommandCompleter.completerCmdMap.put(cmd, (int)completer); 
            if (cmdabbrev!=null &&  cmdabbrev.length() > 0)
                CommandCompleter.completerCmdMap.put(cmdabbrev, (int)completer); 
        }

        String[] cmdinfo = new String[4];
        cmdinfo[0] = cmd;
        cmdinfo[1] = cmdabbrev;
        cmdinfo[2] = disconnected;
        cmdinfo[3] = readonly;        
        tty.scriptCommandList.put(cmd, cmdinfo); 
        tty.scriptCommandFuncMap.put(cmd, new JavaScriptObject(scrpObj,scope));
        return 0;
    }

    int stopEventId=0;
    int exitedEventId=0;
    int continueEventId=0;

    @JSFunction
    public int addStopEvent(ScriptableObject scrpObj) {
        Function onEvent= getPropertyFunction("onEvent",scrpObj);        
        if (onEvent == null ) {
            System.out.println("Failed to add Stop Event !!");
            return -1;
        }    

        ScriptableObject fObj = (ScriptableObject)getPropertyFunction("funcObj",scrpObj);
        for (ScriptObject sobj : tty.stopEventList) {
            JavaScriptObject jobj = (JavaScriptObject) sobj;
            Function listFObj = getPropertyFunction("funcObj",jobj.getObj());
            if (listFObj == fObj) {            
                System.out.println("Stop Event function already exists !!");
                return -1;
            }
        }

        tty.stopEventList.add(new JavaScriptObject(scrpObj,scope));        
        return 0;
    }

    @JSFunction
    public int removeStopEvent(ScriptableObject scrpObj){
        int index = -1;
        int i = 0;

        for (ScriptObject sobj : tty.stopEventList) {
            JavaScriptObject jobj = (JavaScriptObject) sobj;
            Function listFObj = getPropertyFunction("funcObj",jobj.getObj());
            if (listFObj == scrpObj) {            
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

    @JSFunction
    public int addExitedEvent(ScriptableObject scrpObj) {
        Function onEvent= getPropertyFunction("onEvent",scrpObj);        
        if (onEvent == null ) {
            System.out.println("Failed to add Exited Event !!");
            return -1;
        }    

        ScriptableObject fObj = (ScriptableObject)getPropertyFunction("funcObj",scrpObj);
        for (ScriptObject sobj : tty.exitedEventList) {
            JavaScriptObject jobj = (JavaScriptObject) sobj;
            Function listFObj = getPropertyFunction("funcObj",jobj.getObj());
            if (listFObj == fObj){            
                System.out.println("Exited Event function already exists !!");
                return -1;
            }
        }

        tty.exitedEventList.add(new JavaScriptObject(scrpObj,scope));        
        return 0;
       
    }


    @JSFunction
    public int removeExitedEvent(ScriptableObject scrpObj) {
        int index = -1;
        int i = 0;

        for (ScriptObject sobj : tty.exitedEventList) {
            JavaScriptObject jobj = (JavaScriptObject) sobj;
            Function listFObj = getPropertyFunction("funcObj",jobj.getObj());
            if (listFObj == scrpObj) {            
                index = i;
                break;
            }
            i++;
        }
        
        if (index !=  -1) {
            tty.exitedEventList.remove(index);
            return 0;
        } else {
            System.out.println("Exited Event function doesn't exist !!");
            return -1;
        }       
    
    }


    @JSFunction
    public int addContinueEvent(ScriptableObject scrpObj){

        Function onEvent= getPropertyFunction("onEvent",scrpObj);        
        if (onEvent == null ) {
            System.out.println("Failed to add Continue Event !!");
            return -1;
        }    

        ScriptableObject fObj = (ScriptableObject)getPropertyFunction("funcObj",scrpObj);
        for (ScriptObject sobj : tty.continueEventList) {
            JavaScriptObject jobj = (JavaScriptObject)sobj;
            Function listFObj = getPropertyFunction("funcObj",jobj.getObj());
            if (listFObj == fObj) {            
                System.out.println("Continue Event function already exists !!");
                return -1;
            }
        }

        tty.continueEventList.add(new JavaScriptObject(scrpObj,scope));        
        return 0;
    }    

    @JSFunction
    public int removeContinueEvent(ScriptableObject scrpObj){
        int index = -1;
        int i = 0;

        for (ScriptObject sobj : tty.continueEventList) {
            JavaScriptObject jobj = (JavaScriptObject) sobj;
            Function listFObj = getPropertyFunction("funcObj",jobj.getObj());
            if (listFObj == scrpObj) {            
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


    @JSFunction
    public int addBreakpointScriptFunc(int id, ScriptableObject scrpObj) {
        EventRequestSpecList.Property p = (EventRequestSpecList.Property)Env.specList.eventRequestSpecsMap().get(id);

        if (p == null) {
            System.out.println("id is wrong !!");
            return -1;
        }

        Function onBreakPoint = getPropertyFunction("onBreakPoint",scrpObj);
        if (onBreakPoint == null) {
            System.out.println("Failed to add breakpoint !!");
            return -1;
        }

        Object jsfile = scope.get("xjdbJsFile", scope);
        String jsFilename = null;
        
        if (jsfile != Scriptable.NOT_FOUND)
            jsFilename = Context.toString(jsfile);        

        p.scriptObj = new JavaScriptObject(scrpObj, scope); 
        if (p.cmdList == null)
            p.cmdList = new ArrayList();                    

        if (jsFilename != null)
            p.cmdList.add("[script] " + jsFilename);   
        else
            p.cmdList.add("[script]");        
            
        return p.id;        
    }

    @JSFunction
    public int addBreakpoint(ScriptableObject scrpObj) {
        String breakpoint = getPropertyString("breakpoint",scrpObj);
        Function onBreakPoint = getPropertyFunction("onBreakPoint",scrpObj);
        if (breakpoint == null || onBreakPoint == null) {
            System.out.println("Failed to add breakpoint !!");
            return -1;
        }
        
        Boolean temporary = getPropertyBoolean("temporary",scrpObj);        
        if (temporary == null )
            temporary = false;

        String eventType = getPropertyString("eventType",scrpObj);
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

        Boolean enable = getPropertyBoolean("enable",scrpObj);        
        if (enable!=null && !enable)
            tty.executeCommand(new StringTokenizer("disable !!"));   

        EventRequestSpecList.Property p = Commands.getLastBreakPoint();
        
        if (p == null) {
            System.out.println("The property of breakpoint is null !!");
            //return Context.javaToJS(null,scope);
            return -1;
        }

        p.scriptObj = new JavaScriptObject(scrpObj, scope);

        Object jsfile = scope.get("xjdbJsFile", scope);
        String jsFilename = null;
        
        if (jsfile != Scriptable.NOT_FOUND)        
            jsFilename = Context.toString(jsfile);    

        if (p.cmdList == null)
            p.cmdList = new ArrayList();

        if (jsFilename != null)
            p.cmdList.add("[script] " + jsFilename);   
        else
            p.cmdList.add("[script]");   
        return p.id;            
        
    } 

    @JSFunction
    public void println(ScriptableObject o) { 
        System.out.println(Context.toString(o));
    }    
    
    @JSFunction
    public void print(ScriptableObject o) { 
        System.out.print(Context.toString(o));
    }      

    @JSFunction
    public void cmd(String cmd) {
        StringTokenizer t = new StringTokenizer(cmd);
            tty.executeCommand(t);        
    }   

    @JSFunction
    public void cmdPassJs(String cmd) {
        tty.passScriptCommand = true;
        cmd(cmd);
    }     
    
    @JSFunction
    public String cmdret(String cmd) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream(512);
        PrintStream ps = new PrintStream(bs);
        System.setOut(ps);
        StringTokenizer t = new StringTokenizer(cmd);
        tty.executeCommand(t); 

        System.setOut(console);
                
        return new String(bs.toByteArray());        
    }     

    @JSFunction
    public String cmdretPassJs(String cmd) {
        tty.passScriptCommand = true;
        return cmdret(cmd);    
    } 

    @JSFunction
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

    @JSGetter
    public String getSyscmdError() {
        return tty.getSystemCmdError();    
    } 


    @JSFunction
    public boolean initload(String filename) {
        Context cx = Context.enter();        
        try {

            File fh = new File(TTY.class.getClassLoader().getResource(filename).getFile());                         
            if (fh.canRead()) {
                System.out.println("jdb.initload: "+fh.getAbsolutePath());
                FileReader fr = new FileReader(fh);                                     
                cx.evaluateReader(scope, fr, fh.getAbsolutePath(), 1, null);            
            }        
        
        } catch (Exception ex) { 
            ex.printStackTrace();  
            return false;
        } finally {        
            Context.exit();
        }                    
        return true;        
    }     

    @JSFunction
    public boolean load(String filename) {
        Context cx = Context.enter();        
        try {
            File fh = new File(filename);  
            if (fh.canRead()) {
                System.out.println("jdb.load: " + fh.getAbsolutePath());
                FileReader fr = new FileReader(fh);                
                cx.evaluateReader(scope, fr, fh.getAbsolutePath(), 1, null);            
            } else {
                System.out.println("jdb.load: " + fh.getAbsolutePath() + " can't be read.");
            }
        
        } catch (Exception ex) { 
            System.out.println("jdb.load: error");
            ex.printStackTrace();  
            return false;
        } finally {        
            Context.exit();
        }                    
        return true;
    }        

    @JSFunction
    public Object expr(String expr) {
        StringTokenizer t = new StringTokenizer(expr);
        Commands evaluator = new Commands(this.tty);
        String s =  evaluator.commandExpr(t);                 
        return Context.javaToJS(s, scope);
    }

    @JSGetter
    public Object getBreakpoints() {
        Object result = null;
        Context cx = Context.enter();
        try {
            Iterator iter = Env.specList.eventRequestSpecs().iterator();
            Object[] array = new Object[Env.specList.eventRequestSpecs().size()];
            int i = 0;
            while (iter.hasNext()) {
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                if (spec instanceof BreakpointSpec || spec instanceof WatchpointSpec) {
                    EventRequestSpecList.Property p =  (EventRequestSpecList.Property) Env.specList.eventRequestSpecsID().get(i);                          
                              
                    Scriptable b= cx.newObject(scope);
                    Object pp = Context.javaToJS(p, scope);
                    ScriptableObject.putProperty(b, "property", pp); 
                    String[] s = spec.toString().split(" ");     
                    ScriptableObject.putProperty(b, "breakpoints",s[1]);
                    Object t = ScriptableObject.getProperty(b, "breakpoints");                    
                    array[i++]= b;
                }
             }
             result= cx.newArray(scope,array); 
             return array[0];
        } finally {
            Context.exit();
        }
    }        
    
    @JSGetter
    public String getLocation() {
        return tty.getCurrentLocation();            
    }    
    
    @JSGetter
    public String getCurrentThreadName() {
        return tty.getCurrentThreadName();            
    }    

    @JSGetter
    public String getCurrentSourceLocation() {
        return tty.getCurrentSourceLocation();            
    }    

    @JSGetter
    public String getCurrentLocationClassName() {
        String className = null;
        className=tty.getCurrentLocationClassName();
        if (className != null)
            return className;
        else 
            return "";        
    }

    @JSFunction
    public String getClassLocations(String className, int lineno) {
        return tty.getClassLocations(className,lineno);
    }        

    @JSGetter
    public int getLastBreakPointId() {
        EventRequestSpecList.Property p = Commands.getLastBreakPoint();
        if (p != null)
            return p.id;
        else
            return -1;
    }    


    void throwJavaScriptException(String message, int cause, String where) {
        Context cx = Context.enter();
        try {
            Scriptable err = cx.newObject(scope);            
            ScriptableObject.putProperty(err, "message", message);
            ScriptableObject.putProperty(err, "cause", new Integer(cause));
            throw new JavaScriptException(err, where, 0);    
        } finally {
            Context.exit();
        }
    }    

    @JSFunction
    public void testException() {
        throwJavaScriptException("This is test exception", 2, "testException");
    } 

    @JSFunction
    public void testParms(Object _parms) {
        ScriptableObject parms = (ScriptableObject) _parms;
        if (parms != null) {
            Object[] ids = parms.getAllIds();
            for (int i = 0; i < ids.length; i++) {
                      String id = ScriptRuntime.toString(ids[i]); 
                      String value = getPropertyString(id, parms);  
                      System.out.println(id + "=" + value);
            }
        }       
    }
}
