/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
/*
 * Copyright (c) 1997-1999 by Sun Microsystems, Inc. All Rights Reserved.
 * 
 * Sun grants you ("Licensee") a non-exclusive, royalty free, license to use,
 * modify and redistribute this software in source and binary code form,
 * provided that i) this copyright notice and license appear on all copies of
 * the software; and ii) Licensee does not utilize the software in a manner
 * which is disparaging to Sun.
 * 
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING ANY
 * IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR
 * NON-INFRINGEMENT, ARE HEREBY EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE
 * LIABLE FOR ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING
 * OR DISTRIBUTING THE SOFTWARE OR ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS
 * LICENSORS BE LIABLE FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT,
 * INDIRECT, SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF THE USE OF
 * OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGES.
 * 
 * This software is not designed or intended for use in on-line control of
 * aircraft, air traffic, aircraft navigation or aircraft communications; or in
 * the design, construction, operation or maintenance of any nuclear
 * facility. Licensee represents and warrants that it will not use or
 * redistribute the Software for such purposes.
 */

package com.sun.tools.example.debug.tty;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;
import com.sun.jdi.connect.*;

import java.util.*;
import java.io.*;

import org.gnu.readline.*;
import com.sun.tools.example.debug.expr.ExpressionParser;
import java.lang.Runtime;
import java.lang.Process;
import java.lang.ProcessBuilder;

import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.jar.Attributes;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TTY implements EventNotifier, Service {

    EventHandler handler = null;

    /**
     * List of Strings to execute at each stop.
     */
    private List monitorCommands = new ArrayList();
    private int monitorCount = 0;

    /**
     * The name of this tool.
     */
    private static final String progname = "jdb";

    public void vmStartEvent(VMStartEvent se)  {
        Thread.yield();  // fetch output
        MessageOutput.lnprint("VM Started:");
    }

    public void vmDeathEvent(VMDeathEvent e)  {
    }

    public void vmDisconnectEvent(VMDisconnectEvent e)  {
    }

    public void threadStartEvent(ThreadStartEvent e)  {
    }

    public void threadDeathEvent(ThreadDeathEvent e)  {
    }

    public void classPrepareEvent(ClassPrepareEvent e)  {
    }

    public void classUnloadEvent(ClassUnloadEvent e)  {
    }


    List<ScriptObject> stopEventList = new ArrayList<ScriptObject>();
    List<ScriptObject> exitedEventList = new ArrayList<ScriptObject>();
    List<ScriptObject> continueEventList = new ArrayList<ScriptObject>();
    void ProcessEvent(Event ev) {       
        Integer id = (Integer) ev.request().getProperty((Object)"id");        
        EventRequestSpecList.Property p = (EventRequestSpecList.Property)Env.specList.eventRequestSpecsMap().get(id);
        brpproperty = p;
        event = ev;
        p.count++;
        
        if (p != null && p.cmdList != null)
            cmdList = p.cmdList;

        temporary = false;
        if (p != null && p.temporary) {
            for (int i = 0; i < Env.specList.eventRequestSpecsID().size(); i++) {
                EventRequestSpecList.Property tp = (EventRequestSpecList.Property)Env.specList.eventRequestSpecsID().get(i);
                if (id == tp.id) {
                    temporary = true;
                    Env.specList.delete(i);
                    break;
                }
            }
        }

        breakcondition = null;
        if (p != null )
            breakcondition = p.breakcondition;
       
        breakstring = ev.request().toString();
    }

    public void breakpointEvent(BreakpointEvent be)  {
        Thread.yield();  // fetch output    
        eventThreadName = be.thread().name();
        ProcessEvent(be);   
        if (cmdList == null && breakcondition==null ) {
            System.out.println("");
            if (brpproperty != null)
                System.out.println("Breakpoint hit [id="+brpproperty.id + ",count=" + brpproperty.count + "]:");
            else
                MessageOutput.lnprint("Breakpoint hit:");

            System.out.println((temporary?"(temporary) ":" ") + breakstring.substring(19,breakstring.length()));	
        }
    }

    public void fieldWatchEvent(WatchpointEvent fwe)  {
        Field field = fwe.field();
        ObjectReference obj = fwe.object();
        Thread.yield();  // fetch output
        
        eventThreadName = fwe.thread().name();
        ProcessEvent(fwe);

        if (cmdList != null || breakcondition != null )
            return;

        if (cmdList == null && breakcondition == null ) {
            System.out.println("");
            System.out.print((temporary ? "(temporary) " : ""));
        }        

        if (fwe instanceof ModificationWatchpointEvent) {   
            MessageOutput.print("Field access encountered before after",
                                  new Object [] {field,
                                                 fwe.valueCurrent(),
                                                 ((ModificationWatchpointEvent)fwe).valueToBe()});
        } else {
            MessageOutput.print("Field access encountered", field.toString());
        }
    }

    public void stepEvent(StepEvent se)  {
        Thread.yield();  // fetch output
        MessageOutput.lnprint("Step completed:");
        System.out.println("");
    }

    public void exceptionEvent(ExceptionEvent ee) {
        Thread.yield();  // fetch output
        Location catchLocation = ee.catchLocation();
        if (catchLocation == null) {
            MessageOutput.lnprint("Exception occurred uncaught",
                                  ee.exception().referenceType().name());
        } else {
            MessageOutput.lnprint("Exception occurred caught",
                                  new Object [] {ee.exception().referenceType().name(),
                                                 Commands.locationString(catchLocation)});
        }
        System.out.println("");
    }

    public void methodEntryEvent(MethodEntryEvent me) {
        Thread.yield();  // fetch output
        /*
         * These can be very numerous, so be as efficient as possible.
         * If we are stopping here, then we will see the normal location 
         * info printed.
         */
        if (me.request().suspendPolicy() != EventRequest.SUSPEND_NONE) {
            // We are stopping; the name will be shown by the normal mechanism
            MessageOutput.lnprint("Method entered:");
        } else {
            // We aren't stopping, show the name
            MessageOutput.print("Method entered:");
            printLocationOfEvent(me);
        }
    }

    public boolean methodExitEvent(MethodExitEvent me) {
        Thread.yield();  // fetch output
        /*
         * These can be very numerous, so be as efficient as possible.
         */
        Method mmm = Env.atExitMethod();
        Method meMethod = me.method();

        if (mmm == null || mmm.equals(meMethod)) {
            // Either we are not tracing a specific method, or we are
            // and we are exitting that method.

            if (me.request().suspendPolicy() != EventRequest.SUSPEND_NONE) {
                // We will be stopping here, so do a newline
                MessageOutput.println();
            }
            if (Env.vm().canGetMethodReturnValues()) {
                MessageOutput.print("Method exitedValue:", me.returnValue() + "");
            } else {
                MessageOutput.print("Method exited:");
            }

            if (me.request().suspendPolicy() == EventRequest.SUSPEND_NONE) {
                // We won't be stopping here, so show the method name
                printLocationOfEvent(me);
                
            } 

            // In case we want to have a one shot trace exit some day, this
            // code disables the request so we don't hit it again.
            if (false) {
                // This is a one shot deal; we don't want to stop
                // here the next time. 
                Env.setAtExitMethod(null);
                EventRequestManager erm = Env.vm().eventRequestManager();
                Iterator it = erm.methodExitRequests().iterator();
                while (it.hasNext()) {
                    EventRequest eReq = (EventRequest)it.next();
                    if (eReq.equals(me.request())) {
                        eReq.disable();
                    }
                }
            }
            return true;
        }

        // We are tracing a specific method, and this isn't it.  Keep going.
        return false;
    }

    List cmdList = null;
    EventRequestSpecList.Property brpproperty = null;
    Event event;
    String breakcondition;
    String eventThreadName = null;
    
    String breakstring = null;
    boolean temporary = false;
    String[] equOps = {"==", "!=", ".equals"};
    
    boolean handleBeakExpression(String expr, Commands evaluator) throws Exception {
        String variable = null;
        String varValue = null;
        String RExpr = null;
        String RValue = null;
        boolean handleEquals = false;

        int i = 0;
        int index = -1;
        String equOp = equOps[0];
        for (i = 0; i < equOps.length; i++) {
            equOp= equOps[i];
            index = expr.indexOf(equOps[i]);
            if (index != -1) {
                handleEquals = true;
                break;
            }
        }

        if (!handleEquals) {
            boolean res = evaluator.exprValue(expr); 
            return res;
        } else {
            variable= expr.substring(0,index);
            RExpr= expr.substring(index + equOp.length());
            varValue = evaluator.expr(new StringTokenizer(variable), false);
            RValue = evaluator.expr(new StringTokenizer(RExpr), false);
            boolean testEqual = false;

            if(varValue == null && RValue == null) {
                testEqual = true;
            }else if(varValue == null || RValue == null ) {
                testEqual = false;
            }else if (varValue.length() > 0 && RValue.length() > 0
                && varValue.equals(RValue)) {
                testEqual = true;
            }

            if (i == 0 || i == 2) 
                return testEqual;
            else
                return !testEqual;

        }
    } 

    boolean showStackFrames(int depth, String matchName) {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null)
            return false;

        List stack = null;
        try {
            stack = threadInfo.getStack();
        } catch (IncompatibleThreadStateException e) {
            return false;
        }

        if (stack == null)
            return false;

        int nFrames = stack.size();

        boolean match = false;

        if (depth != -1)
            nFrames = Math.min(depth, nFrames);

        System.out.println("break to display backtrace:"); 
        for (int i = threadInfo.getCurrentFrameIndex(); i < nFrames; i++) {
            StackFrame frame = (StackFrame)stack.get(i);
            Location loc = frame.location();
            Method meth = loc.method();           
            dumpFrame (i, false, frame);
            if (!match && (depth == -1 || depth > nFrames || depth == i + 1)) {
                String methodfullName=meth.declaringType().name() + "." + meth.name();
                match = methodfullName.equals(matchName);
            }
        }
        return match;    
    }

    private void dumpFrame (int frameNumber, boolean showPC, StackFrame frame) {
        Location loc = frame.location();
        long pc = -1;
        if (showPC) {
            pc = loc.codeIndex();
        }
        Method meth = loc.method();

        long lineNumber = loc.lineNumber();
        String methodInfo = null;
        if (meth instanceof Method && ((Method)meth).isNative()) {
            methodInfo = MessageOutput.format("native method");
        } else if (lineNumber != -1) {
            try {
                methodInfo = loc.sourceName() +
                    MessageOutput.format("line number",
                                         new Object [] {new Long(lineNumber)});
            } catch (AbsentInformationException e) {
                methodInfo = MessageOutput.format("unknown");
            }
        }
        if (pc != -1) {
            MessageOutput.println("stack frame dump with pc",
                                  new Object [] {new Integer(frameNumber + 1),
                                                 meth.declaringType().name(),
                                                 meth.name(),
                                                 methodInfo,
                                                 new Long(pc)});
        } else {
            MessageOutput.println("stack frame dump",
                                  new Object [] {new Integer(frameNumber + 1),
                                                 meth.declaringType().name(),
                                                 meth.name(),
                                                 methodInfo});
        }
    }      

    void printBreakEventInformation(Event event) {
        if (event instanceof WatchpointEvent) {
            WatchpointEvent	fwe = (WatchpointEvent)event;
            Field field = fwe.field();
            if (event instanceof ModificationWatchpointEvent) {
                MessageOutput.print("Field access encountered before after",        
                                              new Object [] {field,
                                                     fwe.valueCurrent(),
                                                     ((ModificationWatchpointEvent)fwe).valueToBe()});

            } else {
                MessageOutput.println("Field access encountered", field.toString());
            }
        } else if (event instanceof BreakpointEvent) {
            System.out.println("");
            if (brpproperty != null)
                System.out.println("Breakpoint hit [id=" + brpproperty.id + ",count=" + brpproperty.count + "]:");
            else
                MessageOutput.lnprint("Breakpoint hit:");

            System.out.println((temporary?"(temporary) ":" ") + breakstring.substring(19,breakstring.length()));

            BreakCondition.println(breakcondition);
        }
    }   
    public void vmInterrupted() {
        Thread.yield();  // fetch output
        
        boolean doBreak = true;

        String breakexpr = null;
        String breakcmd = null;
        String breakThreadName = null;
        List precmdList = null;
        boolean threadNameMathch=false;
        if (breakcondition != null) {
            BreakCondition cond = new BreakCondition(breakcondition);
            breakcmd = cond.cmd();
            breakexpr = cond.expr();
            breakThreadName = cond.threadname();
        }

        if (breakThreadName != null) {
            if (breakThreadName.startsWith("r'") && breakThreadName.endsWith("'")) {
                breakThreadName = breakThreadName.substring(2, breakThreadName.length() - 1);
                Pattern p = Pattern.compile (breakThreadName) ; 
                Matcher m = p.matcher (eventThreadName ) ;

                if (m.find () == true)
                    threadNameMathch = true;
                    
            }else if (breakThreadName.equals(eventThreadName))
                threadNameMathch = true;
        }

        if (breakThreadName != null && !threadNameMathch) {
            if (precmdList == null)
                precmdList = new ArrayList();
            precmdList.add("cont");
        } else 
        if (breakcmd != null && breakcmd.equals("if")) {
            Commands evaluator = new Commands(this);
            try {
                doBreak=handleBeakExpression(breakexpr, evaluator);
            } catch (Exception e) {
                System.out.print("invalid expression: " + breakexpr);
                doBreak = false;
            }

            if (!doBreak) {
                if (precmdList == null)
                    precmdList = new ArrayList();
                precmdList.add("cont");
            }else {
                if (breakexpr != null)                   
                    printBreakEventInformation(event);                
            }
    
        } else if (breakcmd != null && breakcmd.equals("display")) {
            if (precmdList == null)
                precmdList = new ArrayList();

            if (breakexpr.equals("location") || breakexpr.equals("loc")) { 

                if (event instanceof WatchpointEvent) {
                    WatchpointEvent fwe = (WatchpointEvent)event;
                    Field field = fwe.field();
                    if (event instanceof ModificationWatchpointEvent) {
                        MessageOutput.println("Field access encountered before after",
                                                        new Object [] {field,
                                                                fwe.valueCurrent(),
                                                                ((ModificationWatchpointEvent)fwe).valueToBe()});
                        printCurrentLocation();
                    } else {
                        MessageOutput.println("Field access encountered", field.toString());
                        printCurrentLocation();
                    }

                } else 
                    printCurrentLocation();
            } else {
                precmdList.add("eps "+breakexpr);
            }

            precmdList.add("cont");
        } else if (breakcmd!=null && breakcmd.equals("bt")) {
            int trace_depth = -1;
            int index = breakexpr.indexOf(" ");
            String trace_depth_str = null;
            String trace_func_name = null;
            if(index != -1) {
                trace_depth_str=breakexpr.substring(0,index).trim();
                trace_func_name=breakexpr.substring(index+1).trim();
            }else
                trace_depth_str=breakexpr;

            try {
                trace_depth= Integer.parseInt(trace_depth_str);
            } catch (NumberFormatException exc) {
                // do nothing
            }

            boolean stopForBt = false;

            if (event instanceof WatchpointEvent) {
                WatchpointEvent fwe = (WatchpointEvent)event;
                Field field = fwe.field();
                if (event instanceof ModificationWatchpointEvent) {
                    MessageOutput.println("Field access encountered before after",
                                                    new Object [] {field,
                                                            fwe.valueCurrent(),
                                                            ((ModificationWatchpointEvent)fwe).valueToBe()});

                    stopForBt = showStackFrames(trace_depth, trace_func_name);
                } else {
                    MessageOutput.println("Field access encountered", field.toString());
                    stopForBt = showStackFrames(trace_depth, trace_func_name);
                }
            } else 
                stopForBt = showStackFrames(trace_depth, trace_func_name);

            if(!stopForBt) {
                if(precmdList == null)
                    precmdList = new ArrayList();
                precmdList.add("cont");
            } else {
                System.out.println("");
                if (brpproperty != null)
                    System.out.println("Backtrace hit [id=" + brpproperty.id + ",count=" + brpproperty.count + "]:");
                else
                    MessageOutput.lnprint("Backtrace hit:");

                System.out.println((temporary?"(temporary) ":" ") + breakstring.substring(19,breakstring.length()));
                BreakCondition.println(breakcondition);
            }            

        }else if(breakcmd!=null && breakcmd.equals("count")) {
            int countlimt = -1;
            try {
                countlimt = Integer.parseInt(breakexpr);
            }catch(NumberFormatException exc) {
                // do nothing
            }
            if (countlimt <= brpproperty.count) {                
                if (breakexpr != null) 
                    printBreakEventInformation(event);
                brpproperty.count = 0;
            } else {
                if (precmdList == null)
                    precmdList = new ArrayList();
                precmdList.add("cont");
            }           
        } else if (breakcmd != null && breakcmd.equals("script")) {
            boolean stop = ScriptManagerBridge.executeString(breakexpr);

            if (stop) {
               if (breakexpr != null) 
                   printBreakEventInformation(event);             
            } else {
                if (precmdList == null)
                    precmdList = new ArrayList();
                precmdList.add("cont");
            }
        } else if (breakcmd != null && breakcmd.equals("thread")) {
            threadNameMathch = false;
            if (breakexpr.startsWith("r'") && breakexpr.endsWith("'")) {
                breakexpr = breakexpr.substring(2, breakexpr.length() - 1);
                Pattern p = Pattern.compile(breakexpr); 
                Matcher m = p.matcher (eventThreadName );

                if (m.find ()== true)
                    threadNameMathch = true;
            } else if (breakexpr.equals(eventThreadName)) {
                threadNameMathch = true;
            }

            if (!threadNameMathch) {
                if (precmdList == null)
                    precmdList = new ArrayList();
                precmdList.add("cont");
            }
        }

        breakcondition = null;

        boolean precmdContinue = false;
        if (cmdList != null || precmdList != null) {
            if (precmdList != null) {
                for (int i = 0; i < precmdList.size() ;i++) {
                    String cmd = (String) precmdList.get(i);
                    if (cmd.equals("cont")) {
                        precmdContinue = true;
                        continue;
                    }
                    StringTokenizer t = new StringTokenizer(cmd);
                    executeCommand(t);
                }
            }

            if (cmdList != null) {
                for (int i = 0; i < cmdList.size();i++) {
                    String cmd = (String)cmdList.get(i);
                    if (cmd.startsWith("[script]") || cmd.startsWith("[ascript]")){
                        ScriptObject sobj = brpproperty.scriptObj;
                        handleBreakPointScriptObject(sobj, event);
                        continue;
                    }

                    StringTokenizer t = new StringTokenizer((String) cmdList.get(i));
                    executeCommand(t);
                }
            }

            cmdList  = null;
            if (precmdContinue)
                executeCommand(new StringTokenizer("cont"));
            else
                handleStopEvent();

            return;
        }
        executeCommand(new StringTokenizer("list"));
        Iterator it = monitorCommands.iterator();
        while (it.hasNext()) {
            StringTokenizer t = new StringTokenizer((String)it.next());
            t.nextToken();  // get rid of monitor number
            executeCommand(t);
        }

        handleStopEvent();        
        MessageOutput.printPrompt();
        System.out.print(MessageOutput.promptString()); 
    }
    
    void handleBreakPointScriptObject(ScriptObject sobj, Event event) {
        if (sobj != null) {
            Object[] args = null;

            if (event instanceof BreakpointEvent) {
                args = new Object[2];
                args[0] = new Integer(brpproperty.id);
                args[1] = new String(eventThreadName);
            } else {
                WatchpointEvent fwe = (WatchpointEvent) event;
                Field field = fwe.field();
                if (fwe instanceof ModificationWatchpointEvent) {
                    args = new Object[5];
                    args[0] = new Integer(brpproperty.id);
                    args[1] = new String(field.toString());
                    args[2] = new String(fwe.valueCurrent().toString());
                    args[3] = new String(((ModificationWatchpointEvent)fwe).valueToBe().toString());
                    args[4] = new String(eventThreadName);
                 } else {
                    args = new Object[3];
                    args[0] = new Integer(brpproperty.id);
                    args[1] = new String(field.toString());
                    args[2] = new String(eventThreadName);
                }
            }

            try {
                sobj.call("onBreakPoint", args);
            } catch (Exception ex) {
                ex.printStackTrace();  
            }
        }
    
    }

    boolean handleStopEvent() {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();

        for (ScriptObject sobj : stopEventList) {
            try {
                Object[] args = new Object[1];
                if (threadInfo != null)
                    args[0]=new String( threadInfo.getThread().name());
                else
                    args[0]=new String("");

                boolean result = sobj.call("onEvent", args);
                if (!result)
                    return true;
            } catch (Exception ex) {
                ex.printStackTrace();  
            }
        }

        return false;
    }

    public boolean handleExitedEvent(int cause) {    
        for (ScriptObject sobj : exitedEventList) {
            try {
                Object[] args = new Object[1];
                args[0]=new Integer(cause);
                boolean result = sobj.call("onEvent", args);
                if (!result)
                    return true;
            } catch (Exception ex) {
                ex.printStackTrace();  
            }
        }

        return false;
    }

    boolean handleContinueEvent() {
        for (ScriptObject sobj : continueEventList) {
            try {
                boolean result = sobj.call("onEvent", null);
                if (!result)
                    return true;
            } catch (Exception ex) {
                ex.printStackTrace();  
            }
        }

        return false;
    }    

    public void receivedEvent(Event event) {
    }

    public String getCurrentLocationClassName() {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        StackFrame frame;
        if (threadInfo==null)
            return null;

        try {
            frame = threadInfo.getCurrentFrame();
        } catch (IncompatibleThreadStateException exc) {
            MessageOutput.println("<location unavailable>");
            return null;
        }
        
        if (frame == null) {
            MessageOutput.println("No frames on the current call stack");
        } else {
            Location loc = frame.location();          
            return loc.declaringType().name();
        }
        return null;
    }
    private void printBaseLocation(String threadName, Location loc) {
        MessageOutput.println("location",
                              new Object [] {threadName,
                                             Commands.locationString(loc)});
    }
    
    void printCurrentLocation() {
 
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        StackFrame frame;
        try {
            frame = threadInfo.getCurrentFrame();
        } catch (IncompatibleThreadStateException exc) {
            MessageOutput.println("<location unavailable>");
            return;
        }
        if (frame == null) {
            MessageOutput.println("No frames on the current call stack");
        } else {
            Location loc = frame.location();
            printBaseLocation(threadInfo.getThread().name(), loc);
            if (loc.lineNumber() != -1) {
                String line;
                try {
                    line = Env.sourceLine(loc, loc.lineNumber());
                } catch (java.io.IOException e) {
                    line = null;
                }  
            }
        }
        MessageOutput.println();
    }

    private void printLocationOfEvent(LocatableEvent theEvent) {
        printBaseLocation(theEvent.thread().name(), theEvent.location());
    }

    public String getCurrentThreadName() {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        
        if (threadInfo == null)
            return "";

        String s = threadInfo.getThread().name();
        return s;
    }
     
    public String getCurrentLocation() {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        StackFrame frame;
        
        if (threadInfo == null)
            return "";

        try {
            frame = threadInfo.getCurrentFrame();
        } catch (IncompatibleThreadStateException exc) {
            return "";
        }
        
        if (frame == null) {
            return "";
        } else {
            Location loc = frame.location();
            MessageOutput.println("location",
                            new Object [] {threadInfo.getThread().name(),
                                        Commands.locationString(loc)});

            return String.format("\"thread=%s\", %s", threadInfo.getThread().name(), Commands.locationString(loc));
        }

    }

    public String getClassLocations(String idClass, int lineno) {
        ReferenceType refType = Env.getReferenceTypeFromToken(idClass);
            
        if (refType == null)
            return "";
            
        List<Location> locations = null;

        try {
            if (lineno != -1)
                locations = refType.locationsOfLine(lineno);
            else
                locations = refType.allLineLocations();
                
        } catch(Exception e) {
            locations = null;
        }

        ArrayList paths = new ArrayList();

        if (locations != null) {
            for (Location loc : locations) {
                File file = Env.sourceFile(loc);
                if (file != null) {
                    boolean found = false;
                    for (int i = 0; i < paths.size(); i++) {
                        if (paths.get(i).equals(file.getAbsolutePath())) {
                            found = true;
                            break;
                        }
                    }

                    if (!found)
                        paths.add(file.getAbsolutePath());                    
                }
            }

            String pathsString = "";
            for (int i = 0; i < paths.size(); i++) {
                if (pathsString.length() > 0)
                    pathsString = pathsString + ":" + paths.get(i);
                else
                    pathsString = (String)paths.get(i);
            }

            return pathsString;
        }
        
        return "";
    }        

    void help() {
        MessageOutput.println("zz help text");
    }
    
    void helpx() {
        MessageOutput.println("zz helpx text");
    }

    public static Map scriptCommandList = new HashMap<String , String[]>();
    public static Map scriptCommandFuncMap = new HashMap<String , ScriptObject>();

    static final String[][] commandList = {

        /* 
         * NOTE: this list must be kept sorted in ascending ASCII
         *       order by element [0].  Ref: isCommand() below.
         *
         *Command      OK when        OK when
         * name      disconnected?   readonly?
         *------------------------------------
         */
        {"!!",           "n",         "y"},
        {"?",            "y",         "y"},
        {"backtrace",       "n",         "y"},
        {"break",         "y",         "n"},
        {"bytecodes",    "n",         "y"},
        {"catch",        "y",         "n"},
        {"class",        "n",         "y"},
        {"classes",      "n",         "y"},
        {"classpath",    "n",         "y"},
        {"clear",        "y",         "n"},
        {"commands",         "y",         "n"},        
        {"condition",   "y",         "n"},
        {"connectors",   "y",         "y"},
        {"cont",         "n",         "n"},
        {"delete",        "y",         "n"},
        {"disable",    "y",         "n"},
        {"disablegc",    "n",         "n"},
        {"down",         "n",         "y"},
        {"dump",         "n",         "y"},
        {"dumps",         "n",         "y"},
        {"edump",         "n",         "y"},
        {"edumps",         "n",         "y"},
        {"enable",    "y",         "n"},        
        {"enablegc",     "n",         "n"},
        {"eprint",           "n",         "y"},  
        {"eprints",           "n",         "y"},  
        {"eval",         "n",         "y"},
        {"exclude",      "y",         "n"},
        {"exit",         "y",         "y"},
        {"extension",    "n",         "y"},
        {"fields",       "n",         "y"},
        {"frame",           "n",         "y"},       
        {"gc",           "n",         "n"},
        {"help",         "y",         "y"},
        {"helpx",         "y",         "y"},
        {"history",    "y",         "n"},    
        {"ignore",       "y",         "n"},
        {"interrupt",    "n",         "n"},
        {"kill",         "n",         "n"},
        {"lines",        "n",         "y"},
        {"list",         "n",         "y"},
        {"listcommand",         "y",         "n"},
        {"load",         "n",         "y"},
        {"locals",       "n",         "y"},
        {"lock",         "n",         "n"},
        {"memory",       "n",         "y"},
        {"methods",      "n",         "y"},
        {"monitor",      "n",         "n"},
        {"next",         "n",         "n"},
        {"pop",          "n",         "n"},
        {"port",         "y",         "n"},
        {"print",        "n",         "y"},
        {"prints",           "n",         "y"},        
        {"quit",         "y",         "y"},
        {"read",         "y",         "y"},
        {"redefine",     "n",         "n"},
        {"reenter",      "n",         "n"},
        {"resume",       "n",         "n"},
        {"run",          "y",         "n"},
        {"save",         "n",         "n"},
        {"script",        "y",        "n"},
        {"scriptread",    "y",        "n"},
        {"set",          "n",         "n"},
        {"setp",         "y",         "n"},
        {"socketsend",   "y",         "n"},        
        {"sourcepath",   "y",         "y"},
        {"sourcepaths",   "y",         "y"},
        {"step",         "n",         "n"},
        {"stepi",        "n",         "n"},
        {"stop",         "y",         "n"},
        {"suspend",      "n",         "n"},
        {"syscommand",   "y",         "n"},
        {"tbreak",       "y",         "n"},      
        {"temporary",    "y",         "n"},
        {"thread",       "n",         "y"},
        {"threadgroup",  "n",         "y"},
        {"threadgroups", "n",         "y"},
        {"threadlocks",  "n",         "y"},
        {"threads",      "n",         "y"},
        {"trace",        "n",         "n"},
        {"twatch",       "y",         "n"},        
        {"unmonitor",    "n",         "n"},
        {"untrace",      "n",         "n"},
        {"unwatch",      "y",         "n"},
        {"up",           "n",         "y"},
        {"use",          "y",         "y"},
        {"uses",          "y",         "y"},
        {"version",      "y",         "y"},
        {"watch",        "y",         "n"},
        {"where",        "n",         "y"},
        {"wherei",       "n",         "y"},
        {"write",    "y",         "n"},
        {"writea",    "y",         "n"},
    };
    
    public String[] getScriptCommandInfo(String key) {
        if (scriptCommandList.containsKey(key))
            return (String[]) scriptCommandList.get(key); 
        else {
            Iterator it = scriptCommandList.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry pairs = (Map.Entry)it.next();
                String[] entry = ((String[])pairs.getValue());
                String cmdabbrev = entry[1];
                if (key.equals(cmdabbrev))
                    return entry;
            }
            return null;
        }
    }

    /*
     * Look up the command string in commandList.
     * If found, return the index.
     * If not found, return index < 0
     */
 
    public int isCommand(String key) {     
        //Reference: binarySearch() in java/util/Arrays.java
        //           Adapted for use with String[][0].
        int low = 0;
	int high = commandList.length - 1;
        long i = 0;
	while (low <= high) {
	    int mid = (low + high) >>> 1;
	    String midVal = commandList[mid][0];
            int compare = midVal.compareTo(key);
	    if (compare < 0)
		low = mid + 1;
	    else if (compare > 0)
		high = mid - 1;
	    else
		return mid; // key found
	}
	return -(low + 1);  // key not found.
    };

    /*
     * Return true if the command is OK when disconnected. 
     */
    private boolean isDisconnectCmd(int ii) {
        if (ii < 0 || ii >= commandList.length) return false;
        return (commandList[ii][1].equals("y"));
    }

    public static String[][] abbrCmdTable = {
        {"run", "ru"},
        {"quit", "q"},
        {"cont", "c"},
        {"next", "n"},
        {"print", "p"},
        {"where", "bt"},
        {"frame", "f"},
        {"stop in", "bi"},
        {"stop at", "ba"},
        {"stop", "b"},
        {"list", "l"},    
        {"delete", "d"},
        {"down", "do"},
        {"monitor", "m"},
        {"unmonitor", "um"},
        {"dump", "du"},
        {"edump", "edu"},
        {"dumps", "dus"},
        {"edumps", "edus"},
        {"disable", "dis"},
        {"enable", "en"},
        {"watch", "wa"},
        {"twatch", "twa"},
        {"trace", "tr"},
        {"temporary", "temp"},
        {"condition", "cond"},
        {"socketsend", "ss"},
        {"syscommand","sc"},
        {"unwatch", "uw"},
        {"step", "s"},
        {"write", "w"},
        {"writea", "wra"},
        {"step up", "fin"},
        {"locals", "lo"},
        {"history", "h"},
        {"tbreak", "tb"},
        {"commands", "com"},
        {"script", "scr"},
        {"scriptread", "sr"},
        {"methods", "mt"},
        {"listcommand", "lc"},
        {"threads", "ths"},
        {"thread", "th"},
        {"read", "re"},
        {"run", "r"},
        {"eprint", "ep"},
        {"eprints", "eps"},
        {"interrupt", "int"},
        {"lines", "li"},
        {"suspend", "su"},
        {"prints", "ps"},
        {"redefine","red"},        
        {"load", "loa"}
    };

    /*
     * Return true if the command is OK when readonly.
     */
    private boolean isReadOnlyCmd(int ii) {
        if (ii < 0 || ii >= commandList.length) return false;
        return (commandList[ii][2].equals("y"));
    };

    boolean showPrompt = true;    
    public static StringTokenizer parseStopCommand(StringTokenizer t) {
        StringTokenizer r = null;
        String s = "in ";
        String m = t.nextToken();
        String suspendPolicy="";
        
        if (m.equals("go") && t.hasMoreTokens()) {
            suspendPolicy="go";
            m = t.nextToken();
        } else if (m.equals("thread") && t.hasMoreTokens()) {
            suspendPolicy = "thread";
            m = t.nextToken();
        }
        
        if (!Character.isDigit(m.charAt(0))) {
            if (m.indexOf(":") != -1)
                s = "at ";

            s = s+m;
        } else {
            s = m;
        }

        while (t.hasMoreTokens()) 
            s = s + " " + t.nextToken();

        if (suspendPolicy.length() > 0)
            s = suspendPolicy + " " + s;

        r = new StringTokenizer(s);
        return r;
   }

  
    boolean handleScriptCommand(String[] cmdinfo, StringTokenizer t) {
        String cmd = cmdinfo[0];

        if (!Env.connection().isOpen() && !cmdinfo[2].equals("y")) {
            MessageOutput.println("Command not valid until the VM is started with the run command",
                                                cmd);
            return true;
        } else if (Env.connection().isOpen() && !Env.vm().canBeModified() &&
                                                    !cmdinfo[3].equals("y")) {
            MessageOutput.println("Command is not supported on a read-only VM connection",
                                                cmd);
            return true;
        } else {
        
            ScriptObject sobj = (ScriptObject) scriptCommandFuncMap.get(cmdinfo[0]);
            if (sobj != null) {
                if (sobj.hasProperty("onCommand")) {
                    int len = t.countTokens();
                    Object[] args = new Object[len];
                    for (int i=0; i < len; i++)
                        args[i]= new String(t.nextToken());
                        
                    try {
                        return sobj.call("onCommand", args);
                    } catch (Exception e) {                
                        e.printStackTrace();
                    }

                }
            }

        }

        return false;
    }

    boolean passScriptCommand = false;
    
    void executeCommand(StringTokenizer t) {
        String cmd = t.nextToken().toLowerCase();
        String[] scriptCmdinfo = getScriptCommandInfo(cmd);
        if (scriptCmdinfo != null && !passScriptCommand) {
            boolean handled = handleScriptCommand(scriptCmdinfo,t);

            if (handled) {
                MessageOutput.printPrompt();
                return;
            }
        }
        passScriptCommand = false;
        Commands evaluator = new Commands(this);       
        
        for (int i=0 ; i < abbrCmdTable.length ; i++) {
            if (cmd.equals("finish")) {
                cmd = "fin";
            } else if (cmd.equals("break")) {
                cmd = "b";
            } else if (cmd.equals("tbreak")) {
                cmd = "tb";
            } 
            
            if (cmd.equals(abbrCmdTable[i][1])) {
                if ((cmd.equals("b") || cmd.equals("tb") )&& t.hasMoreTokens()) {
                    t=parseStopCommand(t);

                    if (cmd.equals("b"))
                        cmd = "stop";
                    else if (cmd.equals("tb"))
                        cmd = "tbreak";

                } else if (cmd.equals("bi") || cmd.equals("ba")) {
                    String s = (cmd.equals("bi")? "in":"at");
                    while (t.hasMoreTokens())
                        s = s+" "+t.nextToken();

                    t = new StringTokenizer(s);
                    cmd = "stop";
                    
                } else if (cmd.equals("fin")) {
                    String s =  "up";
                    while (t.hasMoreTokens())
                        s = s+" "+t.nextToken();

                    t = new StringTokenizer(s);
                    cmd = "step";
                } else {
                    cmd = abbrCmdTable[i][0];
                }

                break;
            }
        }

        // Normally, prompt for the next command after this one is done
        showPrompt = true;

        /*
         * Anything starting with # is discarded as a no-op or 'comment'.
         */
        if (!cmd.startsWith("#")) {
            /*
             * Next check for an integer repetition prefix.  If found,
             * recursively execute cmd that number of times.
             */
            if (Character.isDigit(cmd.charAt(0)) && t.hasMoreTokens()) {
                try {
                    int repeat = Integer.parseInt(cmd);
                    String subcom = t.nextToken("");
                    while (repeat-- > 0) {
                        executeCommand(new StringTokenizer(subcom));
                        showPrompt = false; // Bypass the printPrompt() below.
                    }
                } catch (NumberFormatException exc) {
                    MessageOutput.println("Unrecognized command.  Try help...", cmd);
                }
            } else {
                int commandNumber = isCommand(cmd);
                /*
                 * Check for an unknown command
                 */
                if (commandNumber < 0) {
                    MessageOutput.println("Unrecognized command.  Try help...", cmd);
                } else if (!Env.connection().isOpen() && !isDisconnectCmd(commandNumber)) {
                    MessageOutput.println("Command not valid until the VM is started with the run command",
                                          cmd);
                } else if (Env.connection().isOpen() && !Env.vm().canBeModified() &&
                           !isReadOnlyCmd(commandNumber)) {
                    MessageOutput.println("Command is not supported on a read-only VM connection",
                                          cmd);
                } else {

                    try {
                        if (cmd.equals("print")) {
                            evaluator.commandPrint(t, false);
                            showPrompt = false;        // asynchronous command
                        } else if (cmd.equals("eval")) {
                            evaluator.commandPrint(t, false);
                            showPrompt = false;        // asynchronous command
                        } else if (cmd.equals("set")) {
                            evaluator.commandSet(t);
                            showPrompt = false;        // asynchronous command
                        } else if (cmd.equals("dump")) {
                            evaluator.commandPrint(t, true);
                            showPrompt = false;        // asynchronous command
                        } else if (cmd.equals("locals")) {
                            evaluator.commandLocals();
                        } else if (cmd.equals("classes")) {
                            evaluator.commandClasses();
                        } else if (cmd.equals("class")) {
                            evaluator.commandClass(t);
                        } else if (cmd.equals("connectors")) {
                            evaluator.commandConnectors(Bootstrap.virtualMachineManager());
                        } else if (cmd.equals("methods")) {
                            evaluator.commandMethods(t);
                        } else if (cmd.equals("fields")) {
                            evaluator.commandFields(t);
                        } else if (cmd.equals("threads")) {
                            evaluator.commandThreads(t);
                        } else if (cmd.equals("thread")) {
                            evaluator.commandThread(t);
                        } else if (cmd.equals("suspend")) {
                            evaluator.commandSuspend(t);
                        } else if (cmd.equals("resume")) {
                            evaluator.commandResume(t);
                        } else if (cmd.equals("cont")) {
                            evaluator.commandCont();
                        } else if (cmd.equals("threadgroups")) {
                            evaluator.commandThreadGroups();
                        } else if (cmd.equals("threadgroup")) {
                            evaluator.commandThreadGroup(t);
                        } else if (cmd.equals("catch")) {
                            evaluator.commandCatchException(t);
                        } else if (cmd.equals("ignore")) {
                            evaluator.commandIgnoreException(t);
                        } else if (cmd.equals("step")) {
                            evaluator.commandStep(t);
                        } else if (cmd.equals("stepi")) {
                            evaluator.commandStepi();
                        } else if (cmd.equals("next")) {
                            evaluator.commandNext();
                        } else if (cmd.equals("kill")) {
                            evaluator.commandKill(t);
                        } else if (cmd.equals("interrupt")) {
                            evaluator.commandInterrupt(t);
                        } else if (cmd.equals("trace")) {
                            evaluator.commandTrace(t);
                        } else if (cmd.equals("untrace")) {
                            evaluator.commandUntrace(t);
                        } else if (cmd.equals("where") || cmd.equals("backtrace")) {
                            evaluator.commandWhere(t, false);
                        } else if (cmd.equals("wherei")) {
                            evaluator.commandWhere(t, true);
                        } else if (cmd.equals("up")) {
                            evaluator.commandUp(t);
                        } else if (cmd.equals("down")) {
                            evaluator.commandDown(t);
                        } else if (cmd.equals("load")) {
                            evaluator.commandLoad(t);
                        } else if (cmd.equals("run")) {
                            evaluator.commandRun(t);
                            /*
                             * Fire up an event handler, if the connection was just
                             * opened. Since this was done from the run command
                             * we don't stop the VM on its VM start event (so
                             * arg 2 is false).
                             */
                            if ((handler == null) && Env.connection().isOpen()) {
                                handler = new EventHandler(this, false);
                            }
                        } else if (cmd.equals("memory")) {
                            evaluator.commandMemory();
                        } else if (cmd.equals("gc")) {
                            evaluator.commandGC();
                        } else if (cmd.equals("stop")) {
                            evaluator.commandStop(t, true, false);
                        } else if (cmd.equals("clear")) {
                            evaluator.commandClear(t);
                        } else if (cmd.equals("watch")) {
                            evaluator.commandWatch(t, false);
                        } else if (cmd.equals("temporary")){
                            evaluator.commandTemporary(t, true);
                        } else if (cmd.equals("condition")){
                            evaluator.commandCondition(t);
                        } else if (cmd.equals("twatch")) {
                            evaluator.commandWatch(t, true);
                        }else if (cmd.equals("socketsend")) {
                            commandSoecketSend(t);
                        }else if (cmd.equals("syscommand")){
                            commandSysCmd(t);
                        } else if (cmd.equals("unwatch")) {
                            evaluator.commandUnwatch(t);
                        } else if (cmd.equals("list")) {
                            evaluator.commandList(t);
                        } else if (cmd.equals("lines")) { // Undocumented command: useful for testing.
                            evaluator.commandLines(t);
                        } else if (cmd.equals("classpath")) {
                            evaluator.commandClasspath(t);
                        } else if (cmd.equals("use") || cmd.equals("sourcepath")) {                   
                            evaluator.commandUse(t,false);   
                        }else if (cmd.equals("uses") || cmd.equals("sourcepaths")) {                        
                            evaluator.commandUse(t,true);
                        } else if (cmd.equals("monitor")) {
                            monitorCommand(t);
                        } else if (cmd.equals("unmonitor")) {
                            unmonitorCommand(t);
                        } else if (cmd.equals("lock")) {
                            evaluator.commandLock(t);
                            showPrompt = false;        // asynchronous command
                        } else if (cmd.equals("threadlocks")) {
                            evaluator.commandThreadlocks(t);
                        } else if (cmd.equals("disablegc")) {
                            evaluator.commandDisableGC(t);
                            showPrompt = false;        // asynchronous command
                        } else if (cmd.equals("enablegc")) {
                            evaluator.commandEnableGC(t);
                            showPrompt = false;        // asynchronous command
                        } else if (cmd.equals("save")) { // Undocumented command: useful for testing.
                            evaluator.commandSave(t);
                            showPrompt = false;        // asynchronous command
                        } else if (cmd.equals("bytecodes")) { // Undocumented command: useful for testing.
                            evaluator.commandBytecodes(t);
                        } else if (cmd.equals("redefine")) {
                            evaluator.commandRedefine(t);
                        } else if (cmd.equals("pop")) {
                            evaluator.commandPopFrames(t, false);
                        } else if (cmd.equals("reenter")) {
                            evaluator.commandPopFrames(t, true);
                        } else if (cmd.equals("extension")) {
                            evaluator.commandExtension(t);
                        } else if (cmd.equals("exclude")) {
                            evaluator.commandExclude(t);
                        } else if (cmd.equals("read")) {
                            readCommand(t);
                        } else if (cmd.equals("help") || cmd.equals("?")) {
                            help();
                        } else if (cmd.equals("helpx")) {
                            helpx();
                        } else if (cmd.equals("version")) {
                            evaluator.commandVersion(progname,
                                                     Bootstrap.virtualMachineManager());
                        } else if (cmd.equals("quit") || cmd.equals("exit")) {
                            ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
                            if (autosavebreak)
                                evaluator.commandWrite( new StringTokenizer(""), false);
                            if (handler != null) {
                                handler.shutdown();
                            }
                            try {      
                                if (cmdHistoryFile != null)
                                    Readline.writeHistoryFile(cmdHistoryFile.getName());
                            } catch (Exception e) {
                                System.err.println("Readline:Error writing history file!"+e.toString());
                            }
                            Readline.cleanup();
                            Env.shutdown();
                        }
                        else if (cmd.equals("frame")) {
                            evaluator.commandFrame(t);
                        } else if (cmd.equals("delete")) {
                            evaluator.commandDelete(t);
                        } else if (cmd.equals("disable")) {
                            evaluator.commandDisable(t);
                        } else if (cmd.equals("enable")) {
                            evaluator.commandEnable(t);
                        }else if (cmd.equals("tbreak")) {
                            evaluator.commandStop(t, true, true);
                        }else if (cmd.equals("write")) {
                            evaluator.commandWrite(t, false);
                        }else if (cmd.equals("writea")) {
                            evaluator.commandWrite(t, true);
                        } else if (cmd.equals("history")) {
                            historyCommand(t);
                        }else if (cmd.equals("commands")) {
                            evaluator.commandCmd(t);
                        }
                        else if (cmd.equals("port")) {
                            int port =-1;
                            if (server != null)
                                port = server.getPort();
                            System.out.println("Server port : " + port);
                        }
                        else if (cmd.equals("scriptread")) {
                            readScriptFileCommand(t);
                        } else if (cmd.equals("script")) {
                            executeScriptString(t);
                        } else if (cmd.equals("listcommand")) {
                            evaluator.commandListCmd(t);
                        } else if (cmd.equals("prints")) {
                            evaluator.commandPrintSync(t, false);
                        } else if (cmd.equals("eprint")) {
                            evaluator.commandEPrint(t, false);
                        } else if (cmd.equals("eprints")) {
                            evaluator.commandEPrintSync(t, false);
                        } else if (cmd.equals("edump")) {
                            evaluator.commandEPrint(t, true);
                            showPrompt = false;        // asynchronous command
                        } else if (cmd.equals("dumps")) {
                            evaluator.commandPrintSync(t, true);
                        } else if (cmd.equals("edumps")) {
                            evaluator.commandEPrintSync(t, true);
                        } else if (cmd.equals("setp")) {
                            evaluator.commandSetp(t);
                        }                    
                        else {
                            MessageOutput.println("Unrecognized command.  Try help...", cmd);
                        }
                    } catch (VMCannotBeModifiedException rovm) {
                        MessageOutput.println("Command is not supported on a read-only VM connection", cmd);
                    } catch (UnsupportedOperationException uoe) {
                        MessageOutput.println("Command is not supported on the target VM", cmd);
                    } catch (VMNotConnectedException vmnse) {
                        MessageOutput.println("Command not valid until the VM is started with the run command",
                                              cmd);
                    } catch (Exception e) {
                        MessageOutput.printException("Internal exception:", e);
                    }
                }
            }
        }
        if (showPrompt) {
            MessageOutput.printPrompt();
        }
    }
    
    void historyCommand(StringTokenizer t) {
        List historylist = new ArrayList();
        Readline.getHistory(historylist);
    
        if (!t.hasMoreTokens()) {
            for (int i = 0; i < historylist.size(); i++) {
                System.out.println((i+1)+"\t"+(String)historylist.get(i));
            }
        } else { 
            String args = t.nextToken();
            if (Character.isDigit(args.charAt(0))) {
                int cmdno = Integer.parseInt(args);
                if (cmdno  > 0 && cmdno <= historylist.size())
                    executeCommand(new StringTokenizer((String)historylist.get(cmdno-1)));
                else
                    System.out.println("invalid num!!");
            } else if (args.toLowerCase().equals("clear")) {
                File historyFile = new File(".jdbhistory");
                try {
                    if (historyFile.exists())
                        historyFile.delete();
                    Readline.clearHistory();
                    System.out.println("Clear history successfully.");
                } catch (Exception e) {
                    System.err.println("Error delete history file!!");
                }
            } else {
                System.out.println("usage: history num or history clear");
            }
        }
    }
    
    /*
     * Maintain a list of commands to execute each time the VM is suspended.
     */
    void monitorCommand(StringTokenizer t) {
        if (t.hasMoreTokens()) {
            ++monitorCount;
            monitorCommands.add(monitorCount + ": " + t.nextToken(""));
        } else {
            Iterator it = monitorCommands.iterator();
            while (it.hasNext()) {
                MessageOutput.printDirectln((String)it.next());// Special case: use printDirectln()
            }
        }            
    }

    void unmonitorCommand(StringTokenizer t) {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        int count = t.countTokens();
        for (int i = 0; i < count;i++) {
            if (t.hasMoreTokens()) {
                String monTok = t.nextToken();
                int monNum;
                try {            
                    monNum = Integer.parseInt(monTok);
                } catch (NumberFormatException exc) {
                    MessageOutput.println("Not a monitor number:", monTok);
                    continue;
                }
                String monStr = monTok + ":";
                Iterator it = monitorCommands.iterator();
                int index = 0;
                boolean found = false;
                while (it.hasNext()) {
                    String cmd = (String)it.next();
                    StringTokenizer ct = new StringTokenizer(cmd);
                    if (ct.nextToken().equals(monStr)) {                      
                        ids.add(index);
                        found = true;
                        break;
                    }
                    index++;
                }

                if(!found)
                    MessageOutput.println("No monitor numbered:", monTok);
            } else {
                if(i==0)
                    MessageOutput.println("Usage: unmonitor <monitor#>");
            }
        }
        for (int i = ids.size() - 1; i >= 0; i--)  {
            int id = (int) ids.get(i);
            String cmd = (String)monitorCommands.get(id);
            monitorCommands.remove(id);
            MessageOutput.println("Unmonitoring", cmd);         
        }
    }

    void readCommand(StringTokenizer t) {
        if (t.hasMoreTokens()) {
            String cmdfname = t.nextToken();
            if (!readCommandFile(cmdfname)) {
                MessageOutput.println("Could not open:", cmdfname);
            }
        } else {
            MessageOutput.println("Usage: read <command-filename>");
        }
    }
    
    void readScriptFileCommand(StringTokenizer t) {
        if (t.hasMoreTokens()) {
            String cmdfname = t.nextToken();
            if (!readScriptFile(cmdfname)) {
                MessageOutput.println("Could not open:", cmdfname);
            }
        } else {
            MessageOutput.println("Usage: scriptread <command-filename>");
        }
    }

    void executeScriptString(StringTokenizer t) {
        String s ="";

        while (t.hasMoreTokens()) {
            s=s+" "+t.nextToken();
        }
        ScriptManagerBridge.executeString(s);
    }    

    /**
     * Read and execute a command file.  Return true if the
     * file could be opened.
     */
    boolean readCommandFile(String filename) {
        File f = new File(filename);
        BufferedReader inFile = null;
        try {
            if (f.canRead()) {
                MessageOutput.println("*** Reading commands from", f.getCanonicalPath());
                // Process initial commands.
                inFile = new BufferedReader(new FileReader(f));
                String ln;
                while ((ln = inFile.readLine()) != null) {                    
                    int m = ln.indexOf("#");
                    if (m != -1)
                        ln = ln.substring(0,m);
                    if(ln.length()==0)
                        continue;
                    StringTokenizer t = new StringTokenizer(ln);
                    if (t.hasMoreTokens()) {
                        executeCommand(t);
                    }
                }
            }
        } catch (IOException e) {
        } finally {
            if (inFile != null) {
                try {
                    inFile.close();
                } catch (Exception exc) {
                }
            }
        }
        return inFile != null;
    }


    boolean readScriptFile(String filename) {
        return ScriptManagerBridge.readScriptFile(filename);
    }

    String[] historyExcludes = { 
            "cont", "c",
            "break", "b",                     
            "breakbreak", "bb",       
            "run", "r",
            "history","h",
            "quit", "q",
            "list","l",
            "next","n",
            "where","bt"
    };
    String[] historyStartWithExcludes = { 
            "nextnext","nn",
            "enable","en",
            "disable", "dis",
            "enableonly", "eo ",
            "h "
    };
    boolean isAddToHistory(String cmdLine) {  
        if (cmdLine == null || cmdLine.trim().length() <= 0)
            return false;
            
        for (int i = 0; i < historyExcludes.length; i++) {
            if (cmdLine.trim().toLowerCase().equals(historyExcludes[i]))
                return false;            
        }

        for (int i = 0; i < historyStartWithExcludes.length; i++) {
            if (cmdLine.trim().toLowerCase().startsWith(historyStartWithExcludes[i]))
                return false;
        }
        
        return true;
    }
    
    public TTY() throws Exception {
        initReadLine(new String[0]);
        ScriptManagerBridge.init(this);

        MessageOutput.println("Initializing progname", progname);
       
        if (Env.connection().isOpen() && Env.vm().canBeModified()) {
            /*
             * Connection opened on startup. Start event handler
             * immediately, telling it (through arg 2) to stop on the 
             * VM start event.
             */
            this.handler = new EventHandler(this, true);
        }
        try {
            BufferedReader in = 
                    new BufferedReader(new InputStreamReader(System.in));
    
            String lastLine = null;
    
            Thread.currentThread().setPriority(Thread.NORM_PRIORITY);
    
            /*
             * Try reading user's home startup file. Handle Unix and 
             * and Win32 conventions for the names of these files. 
             */
            if (!readCommandFile(System.getProperty("user.home") + 
                                 File.separator + "jdb.ini")) {
                readCommandFile(System.getProperty("user.home") + 
                                File.separator + ".jdbrc");
            }

            String scriptSuffix = ScriptManagerBridge.getScriptSuffix();
            readScriptFile(System.getProperty("user.home") + 
                                 File.separator + scriptSuffix);
    
            // Try startup file in local directory
            readCommandFile(System.getProperty("user.dir") + 
                                 File.separator + "jdb.ini"); 
            readCommandFile(System.getProperty("user.dir") + 
                                File.separator + ".jdbrc");

            
            readScriptFile(System.getProperty("user.dir") + 
                                 File.separator + scriptSuffix);                                           

            boolean init_bind_key = false;

            server = new Server(SERVER_PORT, this);           
            server.start();            
    
            // Process interactive commands.
            MessageOutput.printPrompt();
            while (true) {
                String ln;

                ln = Readline.readline(MessageOutput.promptString(), false);                

                if (isAddToHistory(ln))
                    Readline.addToHistory(ln);

                if (ln == null) {
                    if (lastLine == null)
                        continue;
                    ln = lastLine;
                    MessageOutput.printDirectln(ln);// Special case: use printDirectln()
                }
    
                if (ln.startsWith("!!") && lastLine != null) {
                    ln = lastLine + ln.substring(2);
                    MessageOutput.printDirectln(ln);// Special case: use printDirectln()
                }
    
                StringTokenizer t = new StringTokenizer(ln);
                if (t.hasMoreTokens()) {
                    lastLine = ln;
                    executeCommand(t);
                } else {
                    MessageOutput.printPrompt();
                }
            }
        } catch (VMDisconnectedException e) {
            handler.handleDisconnectedException();
        }
    }

    private static void usage() {
        MessageOutput.println("zz usage text", new Object [] {progname,
                                                     File.pathSeparator});
        System.exit(1);
    }

    static void usageError(String messageKey) {
        MessageOutput.println(messageKey);
        MessageOutput.println();
        usage();
    }

    static void usageError(String messageKey, String argument) {
        MessageOutput.println(messageKey, argument);
        MessageOutput.println();
        usage();
    }

    private static Connector findConnector(String transportName, List availableConnectors) {
        Iterator iter = availableConnectors.iterator();
        while (iter.hasNext()) {
            Connector connector = (Connector)iter.next();
            if (connector.transport().name().equals(transportName)) {
                return connector;
            }
        }

        // not found
        throw new IllegalArgumentException(MessageOutput.format("Invalid transport name:",
                                                                transportName));
    }

    private static boolean supportsSharedMemory() {
        List connectors = Bootstrap.virtualMachineManager().allConnectors();
        Iterator iter = connectors.iterator();
        while (iter.hasNext()) {
            Connector connector = (Connector)iter.next();
            if (connector.transport() == null) {
                continue;
            }
            if ("dt_shmem".equals(connector.transport().name())) {
                return true;
            }
        }
        return false;
    }

    private static String addressToSocketArgs(String address) {
        int index = address.indexOf(':');
        if (index != -1) {
            String hostString = address.substring(0, index);
            String portString = address.substring(index + 1);
            return "hostname=" + hostString + ",port=" + portString;
        } else {
            return "port=" + address;
        }
    }

    private static boolean hasWhitespace(String string) {
        int length = string.length();
        for (int i = 0; i < length; i++) {
            if (Character.isWhitespace(string.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static String addArgument(String string, String argument) {
        if (hasWhitespace(argument) || argument.indexOf(',') != -1) {
            // Quotes were stripped out for this argument, add 'em back. 
            StringBuffer buffer = new StringBuffer(string);
            buffer.append('"');
            for (int i = 0; i < argument.length(); i++) {
                char c = argument.charAt(i);
                if (c == '"') {
                    buffer.append('\\');
                }
                buffer.append(c);
            }
            buffer.append("\" ");
            return buffer.toString();
        } else {
            return string + argument + ' ';
        }
    }

    public static String xclasspath = null;

    public static void main(String argv[]) throws MissingResourceException {
        String cmdLine = "";
        String javaArgs = "";
        int traceFlags = VirtualMachine.TRACE_NONE;
        boolean launchImmediately = false;
        String connectSpec = null;

        String MainClass = "";

        MessageOutput.textResources = ResourceBundle.getBundle
            ("com.sun.tools.example.debug.tty.TTYResources",
             Locale.getDefault());

        for (int i = 0; i < argv.length; i++) {
            String token = argv[i];
            if (token.equals("-dbgtrace")) {
                if ((i == argv.length - 1) ||
                    ! Character.isDigit(argv[i+1].charAt(0))) {
                    traceFlags = VirtualMachine.TRACE_ALL;
                } else {
                    String flagStr = "";
                    try {
                        flagStr = argv[++i];
                        traceFlags = Integer.decode(flagStr).intValue();
                    } catch (NumberFormatException nfe) {
                        usageError("dbgtrace flag value must be an integer:",
                                   flagStr);
                        return;                
                    }
                }
            } else if (token.equals("-X")) {
                usageError("Use java minus X to see");
                return;
            } else if (
                   // Standard VM options passed on
                   token.equals("-v") || token.startsWith("-v:") ||  // -v[:...]
                   token.startsWith("-verbose") ||                  // -verbose[:...]
                   token.startsWith("-D") ||
                   // -classpath handled below
                   // NonStandard options passed on
                   token.startsWith("-X") ||
                   // Old-style options (These should remain in place as long as
                   //  the standard VM accepts them)
                   token.equals("-noasyncgc") || token.equals("-prof") ||
                   token.equals("-verify") || token.equals("-noverify") ||
                   token.equals("-verifyremote") ||
                   token.equals("-verbosegc") ||
                   token.startsWith("-ms") || token.startsWith("-mx") ||
                   token.startsWith("-ss") || token.startsWith("-oss") ) {

                javaArgs = addArgument(javaArgs, token);
            } else if (token.equals("-tclassic")) {
                usageError("Classic VM no longer supported.");
                return;
            } else if (token.equals("-tclient")) {
                // -client must be the first one
                javaArgs = "-client " + javaArgs;
            } else if (token.equals("-tserver")) {
                // -server must be the first one
                javaArgs = "-server " + javaArgs;
            } else if (token.equals("-sourcepath")) {
                if (i == (argv.length - 1)) {
                    usageError("No sourcepath specified.");
                    return;
                }
                Env.setSourcePath(argv[++i]);
            } else if (token.equals("-classpath")) {
                if (i == (argv.length - 1)) {
                    usageError("No classpath specified.");
                    return;
                }
                javaArgs = addArgument(javaArgs, token);
                javaArgs = addArgument(javaArgs, argv[++i]);
            }            
            else if (token.equals("-xclasspath")) { 
                xclasspath = argv[++i];            
            }
            else if (token.equals("-jar")) {
                if (i == (argv.length - 1)) {
                    usageError("No jar specified.");
                    return;
                }
                
                String filename = argv[++i];
                File file = new File(filename);
                
                if (file.exists()) {
                    try {
                        JarFile jarfile = new JarFile(filename);
                        Manifest manifest = jarfile.getManifest();
                        Attributes attrs = (Attributes)manifest.getMainAttributes();
                        MainClass = attrs.getValue("Main-Class");
                        if (MainClass != null && MainClass.length() > 0) {
                            javaArgs = addArgument(javaArgs, "-classpath");
                            javaArgs = addArgument(javaArgs, filename);

                            // Everything from here is part of the command line
                            cmdLine = "";
                            for (i++; i < argv.length; i++) {
                                cmdLine = addArgument(cmdLine, argv[i]);
                            }
                            break;
                        }
                    } catch(IOException e) {
                        // do nothing
                    }
                } else {
                    System.out.print("Jar File not found.");
                    usage();
                    return ;
                }                
            }
            else if (token.equals("-attach")) {
                if (connectSpec != null) {
                    usageError("cannot redefine existing connection", token);
                    return;
                }
                if (i == (argv.length - 1)) {
                    usageError("No attach address specified.");
                    return;
                }
                String address = argv[++i];

                /*
                 * -attach is shorthand for one of the reference implementation's
                 * attaching connectors. Use the shared memory attach if it's
                 * available; otherwise, use sockets. Build a connect 
                 * specification string based on this decision.
                 */
                if (supportsSharedMemory()) {
                    connectSpec = "com.sun.jdi.SharedMemoryAttach:name=" + 
                                   address;
                } else {
                    String suboptions = addressToSocketArgs(address);
                    connectSpec = "com.sun.jdi.SocketAttach:" + suboptions;
                }
            } else if (token.equals("-listen") || token.equals("-listenany")) {
                if (connectSpec != null) {
                    usageError("cannot redefine existing connection", token);
                    return;
                }
                String address = null;
                if (token.equals("-listen")) {
                    if (i == (argv.length - 1)) {
                        usageError("No attach address specified.");
                        return;
                    }
                    address = argv[++i];
                }

                /*
                 * -listen[any] is shorthand for one of the reference implementation's
                 * listening connectors. Use the shared memory listen if it's
                 * available; otherwise, use sockets. Build a connect 
                 * specification string based on this decision.
                 */
                if (supportsSharedMemory()) {
                    connectSpec = "com.sun.jdi.SharedMemoryListen:";
                    if (address != null) {
                        connectSpec += ("name=" + address);
                    }
                } else {
                    connectSpec = "com.sun.jdi.SocketListen:";
                    if (address != null) {
                        connectSpec += addressToSocketArgs(address);
                    }
                }
            } else if (token.equals("-launch")) {
                launchImmediately = true;
            } else if (token.equals("-listconnectors")) {
                Commands evaluator = new Commands();                
                evaluator.commandConnectors(Bootstrap.virtualMachineManager());
                return;
            } else if (token.equals("-connect")) {
                /*
                 * -connect allows the user to pick the connector
                 * used in bringing up the target VM. This allows 
                 * use of connectors other than those in the reference
                 * implementation.
                 */
                if (connectSpec != null) {
                    usageError("cannot redefine existing connection", token);
                    return;
                }
                if (i == (argv.length - 1)) {
                    usageError("No connect specification.");
                    return;
                }
                connectSpec = argv[++i];
            } else if (token.equals("-help")) {
                usage();
            } else if (token.equals("-version")) {
                Commands evaluator = new Commands();                
                evaluator.commandVersion(progname,
                                         Bootstrap.virtualMachineManager());
                System.exit(0);
            } else if (token.startsWith("-")) {
                if (token.equals("-server")) {
                    javaArgs = "-server " + javaArgs;
                    continue;
                } else 
                    usageError("invalid option", token);
                return;
            } else {
                // Everything from here is part of the command line
                cmdLine = addArgument("", token);
                for (i++; i < argv.length; i++) {
                    cmdLine = addArgument(cmdLine, argv[i]);
                }
                break;
            }
        }

        /*
         * Unless otherwise specified, set the default connect spec.
	 */

        /*
         * Here are examples of jdb command lines and how the options
	 * are interpreted as arguments to the program being debugged.
	 * arg1       arg2
	 * ----       ----
	 * jdb hello a b       a          b
	 * jdb hello "a b"     a b
	 * jdb hello a,b       a,b
	 * jdb hello a, b      a,         b
	 * jdb hello "a, b"    a, b
	 * jdb -connect "com.sun.jdi.CommandLineLaunch:main=hello  a,b"   illegal
	 * jdb -connect  com.sun.jdi.CommandLineLaunch:main=hello "a,b"   illegal
	 * jdb -connect 'com.sun.jdi.CommandLineLaunch:main=hello "a,b"'  arg1 = a,b
	 * jdb -connect 'com.sun.jdi.CommandLineLaunch:main=hello "a b"'  arg1 = a b
	 * jdb -connect 'com.sun.jdi.CommandLineLaunch:main=hello  a b'   arg1 = a  arg2 = b
	 * jdb -connect 'com.sun.jdi.CommandLineLaunch:main=hello "a," b' arg1 = a, arg2 = b
	 */
        if (connectSpec == null) {
            connectSpec = "com.sun.jdi.CommandLineLaunch:";
        } else if (!connectSpec.endsWith(",") && !connectSpec.endsWith(":")) {
            connectSpec += ","; // (Bug ID 4285874)
        } 

        if (MainClass!=null && MainClass.length() > 0)
            cmdLine =MainClass+" "+cmdLine;

        cmdLine = cmdLine.trim();
        javaArgs = javaArgs.trim();

        if (cmdLine.length() > 0) {
            if (!connectSpec.startsWith("com.sun.jdi.CommandLineLaunch:")) {
                usageError("Cannot specify command line with connector:",
                           connectSpec);
                return;
            }
            connectSpec += "main=" + cmdLine + ",";
        } 

        if (javaArgs.length() > 0) {
            if (!connectSpec.startsWith("com.sun.jdi.CommandLineLaunch:")) {
                usageError("Cannot specify target vm arguments with connector:",
                           connectSpec);
                return;
            }
            connectSpec += "options=" + javaArgs + ",";
        } 

        try {
            if (! connectSpec.endsWith(",")) {
                connectSpec += ","; // (Bug ID 4285874)
            }
            Env.init(connectSpec, launchImmediately, traceFlags);
            new TTY();
        } catch(Exception e) {                
            MessageOutput.printException("Internal exception:", e);
        }
    }

    File cmdHistoryFile;
    void initReadLine(String[] args) {
        if (args.length > 1)
            Readline.load(ReadlineLibrary.byName(args[1]));
        else
            Readline.load(ReadlineLibrary.GnuReadline);

        Readline.initReadline("jdb"); // init, set app name, read inputrc
        
        try {
            if (args.length > 0)
                Readline.readInitFile(args[0]);    // read private inputrc
        } catch (IOException e) {              // this deletes any initialization
            System.out.println(e.toString());    // from /etc/inputrc and ~/.inputrc
            System.exit(0);
        }

        cmdHistoryFile = new File(".jdbhistory");
        try {
            if (cmdHistoryFile.exists())
                Readline.readHistoryFile(cmdHistoryFile.getName());
        } catch (Exception e) {
            System.err.println("Error reading history file!");
        }

        try {
            Readline.setWordBreakCharacters(" \t;");
        } catch (UnsupportedEncodingException enc) {
            System.err.println("Could not set word break characters");
            System.exit(0);
        }


        Readline.setCompleter(new CommandCompleter(this));
        Readline.setBindKeyHandler(new TTYBindKeyHandler(this));

        Readline.BindKey(TTYBindKeyHandler.BINDKEY_PAGE_UP); // Page up
        Readline.BindKey(TTYBindKeyHandler.BINDKEY_PAGE_DOWN); // Page down
        Readline.BindKey(TTYBindKeyHandler.BINDKEY_F11); 
        Readline.BindKey(TTYBindKeyHandler.BINDKEY_F12); 
    }

    int currListLoc = -1;
    static  int LIST_SIZE = 10;
    static  int SERVER_PORT = 6666;    
    static  int LIST_SCROLL_SIZE = 2;
    static boolean pageMode = false;
    static int scrollSize = LIST_SCROLL_SIZE;
    boolean autosavebreak = true;

    String getCurrentSourceLocation() {
        StackFrame frame = null;
        String empty = "";
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null) {
            MessageOutput.println("No thread specified.");
            return empty;
        }

        try {
            frame = threadInfo.getCurrentFrame();
        } catch (IncompatibleThreadStateException e) {
            MessageOutput.println("Current thread isnt suspended.");
            return empty;
        }

        if (frame == null) {
            MessageOutput.println("No frames on the current call stack");
            return empty;
        }

        Location loc = frame.location();
        if (loc.method().isNative()) {
            MessageOutput.println("Current method is native");
            return empty;
        }
    
        File file = Env.sourceFile(loc);
        int lineno = loc.lineNumber();

        if (file.exists()) {
            return lineno + " " + file.getAbsolutePath();
        }

        return empty;

    }

    String convertWindowsPath(String path) {
        String drive="z:";
        String convertPath= path.replace("/","\\");
        return drive + convertPath;
    }

    void commandSoecketSend(StringTokenizer t) {
        if (t.countTokens() < 3) {
            System.out.println("socketsend ipAddress port cmd");
            return;
        }
        String ipAddress = t.nextToken();
        String port = t.nextToken();

        String cmd =t.nextToken();
        while (t.hasMoreTokens())
            cmd = cmd + " " + t.nextToken();

        clientSocketSend(ipAddress, port, cmd);
    }

    void clientSocketSend(String ipAddress, String port, String command) {

        try {
            String server = ipAddress;
            int i = 0;
            String sendCmd=command;

            byte[] byteBuffer = sendCmd.getBytes();

            int servPort = Integer.parseInt(port) ;

            Socket socket = new Socket(server, servPort);
            System.out.println("Connected to server...sending:");
            System.out.println("\""+sendCmd +"\"");

            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            out.write(byteBuffer);
            socket.shutdownOutput();

            socket.close();  
        } catch (Exception e) { 
            System.out.print("error send commad!!");
        }
    }

    
    void commandSysCmd(StringTokenizer t) {
        if (t.hasMoreTokens()) {
            String syscmd =t.nextToken();
            while (t.hasMoreTokens())
                syscmd = syscmd+" "+t.nextToken();

            startSystemCmdThread(syscmd);
        } else {
            System.out.println("usage: syscommand cmd");
        }

    }

    void startSystemCmdThread(final String syscmd) {
        Thread thd = new Thread(new Runnable() {
            @Override
            public void run() {
                executeSystemCmd(syscmd);
            }
        });
        thd.start();
    }

    private static final ThreadLocal<String> sysResult = new ThreadLocal<String>();
    private static final ThreadLocal<String> sysError = new ThreadLocal<String>();
     
    String getSystemCmdResult() {
        return sysResult.get();
    }

    String getSystemCmdError() {
        return sysError.get();
    }
    
    boolean executeSystemCmd(String syscmd) {
        String result="";
        String error="";

        try {
            System.out.println("");
            System.out.println("exec command: ");
            System.out.println(syscmd);
            Runtime rt = Runtime.getRuntime();
            Process p = null;

            String osName=System.getProperty("os.name");
            if (osName.startsWith("Windows")) {
                String[] cmd ={"cmd", "/c",  syscmd };
                p = rt.exec(cmd);
            } else {
                String[] cmd ={"/bin/sh", "-c",  syscmd};
                p = rt.exec(cmd);
            }

            p.waitFor();                
            BufferedReader stdInput = new BufferedReader(new 
                 InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new 
                 InputStreamReader(p.getErrorStream()));

            String s = null;
            while ((s = stdInput.readLine()) != null) {
                if (result.length() <= 0)
                    System.out.println("ouput:");
                    
                System.out.println(s);
                result=result+s+"\n";
            }            
    
            while ((s = stdError.readLine()) != null) {
                if (error.length() <= 0)
                    System.out.println("error ouput:");

                System.out.println(s);
                error=error+s+"\n";
            }
            
            sysResult.set(result);
            sysError.set(error);
            if ( stdInput != null )
                stdInput.close();
            if ( stdError != null)
                stdError.close();            
        }  catch (IOException e) {
            System.out.println("exception happened - here's what I know: ");
            e.printStackTrace();            
        } catch (InterruptedException e)
        {
            e.printStackTrace();            
        }

        MessageOutput.promptString();
        return true;
    }

    Server server; 
    public void doService(Socket client) throws IOException {
        BufferedReader reader = null;
        PrintStream writer = null;
        try {
            reader = new BufferedReader(
            new InputStreamReader(client.getInputStream()));
            writer = new PrintStream(client.getOutputStream());
            String input = reader.readLine();
            System.out.println();
            System.out.println("Server receive command:");
            System.out.println(input);
            executeCommand(new StringTokenizer(input));
            System.out.print(MessageOutput.promptString()); 
        } finally {
            if (client != null)
                client.close();            
            if (reader != null)
                reader.close();
        }
    }
}

class TTYBindKeyHandler implements BindKeyHandler {
    TTY tty;
    public static final int BINDKEY_PAGE_UP = 0;
    public static final int BINDKEY_PAGE_DOWN = 1;
    public static final int BINDKEY_F11 = 2;
    public static final int BINDKEY_F12 = 3;

    TTYBindKeyHandler(TTY tty) {
        this.tty =tty;
    }

    public void handleKey(int key) {        
        if (tty.currListLoc < 0)
            return ;

        StackFrame frame = null;
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null) {
            MessageOutput.println("No thread specified.");
            return;
        }

        try {
            frame = threadInfo.getCurrentFrame();
        } catch (IncompatibleThreadStateException e) {
            MessageOutput.println("Current thread isnt suspended.");
            return;
        }

        if (frame == null) {
            MessageOutput.println("No frames on the current call stack");
            return;
        }

        Location loc = frame.location();
        if (loc.method().isNative()) {
            MessageOutput.println("Current method is native");
            return;
        }

        ReferenceType refType = loc.declaringType();

        int totalLine = Env.getSourceLineCount(loc);              
        boolean bSmallcodes= false;

        if (totalLine <= tty.LIST_SIZE)
            bSmallcodes = true;

        if (key == BINDKEY_PAGE_UP) {// page up
            if (bSmallcodes)
                tty.currListLoc = totalLine / 2;
            else {
                if (tty.currListLoc - tty.scrollSize < tty.LIST_SIZE / 2 )
                    tty.currListLoc  = tty.LIST_SIZE / 2;
                else
                    tty.currListLoc = tty.currListLoc - tty.scrollSize;
            }

            String listcmdstr = "list " + tty.currListLoc;
            StringTokenizer t = new StringTokenizer(listcmdstr);        
            tty.executeCommand(t);
            System.out.print(MessageOutput.promptString()); 

        } else if (key == BINDKEY_PAGE_DOWN) {// page down            
            if (bSmallcodes)
                tty.currListLoc = totalLine / 2;
            else {
                if (tty.currListLoc + tty.scrollSize >=  totalLine - tty.LIST_SIZE / 2 )
                    tty.currListLoc = totalLine - tty.LIST_SIZE / 2 ;
                else
                    tty.currListLoc = tty.currListLoc + tty.scrollSize;
            }

            String listcmdstr = "list "+tty.currListLoc;
            StringTokenizer t = new StringTokenizer(listcmdstr);
            tty.executeCommand(t);
            System.out.print(MessageOutput.promptString()); 

        } else if (key == BINDKEY_F11) {
            if (!tty.pageMode) {
                tty.scrollSize = tty.LIST_SIZE;
                tty.pageMode = true;
                System.out.println("<offset is one Page>");
            } else {
                tty.scrollSize = tty.LIST_SCROLL_SIZE;
                tty.pageMode = false;
                System.out.println("<offset is several lines>");
            }
            System.out.print(MessageOutput.promptString()); 
        }             

    }
}

class CommandCompleter implements ReadlineCompleter {
    TTY tty;
    CommandCompleter(TTY tty) {
        this.tty = tty;
        initializeCompleterCmdTable();
    }
    String typedName(Method method) {
        StringBuffer buf = new StringBuffer();
        buf.append(method.name());
        buf.append("(");
  
        List args = method.argumentTypeNames();
        int lastParam = args.size() - 1;
        for (int ii = 0; ii < lastParam; ii++) {
            buf.append((String)args.get(ii));
            buf.append(", ");
        }
        if (lastParam >= 0) {
            String lastStr = (String)args.get(lastParam);
            if (method.isVarArgs()) {
                buf.append(lastStr.substring(0, lastStr.length() - 2));
                buf.append("...");
            } else {
                buf.append(lastStr);
            }
        }
        buf.append(")");
        return buf.toString();
  }   

    public static final int COMPLETER_FILES = 1;
    public static final int COMPLETER_CLASS_METHOD = 2;
    public static final int COMPLETER_CLASS = 3;
    public static final int COMPLETER_PRINT = 4;
    public static final int COMPLETER_THREADS = 5;
    public static final int COMPLETER_CLASS_FIELD = 6;

    static Map completerCmdMap = null;

    void initializeCompleterCmdTable() {
        completerCmdMap = new HashMap<String , Integer>();
        completerCmdMap.put("read", COMPLETER_FILES); 
        completerCmdMap.put("re", COMPLETER_FILES); 
        completerCmdMap.put("write", COMPLETER_FILES); 
        completerCmdMap.put("w", COMPLETER_FILES); 
        completerCmdMap.put("writea", COMPLETER_FILES); 
        completerCmdMap.put("wra", COMPLETER_FILES); 

        completerCmdMap.put("scriptread", COMPLETER_FILES); 
        completerCmdMap.put("sr", COMPLETER_FILES);

        completerCmdMap.put("sourcepath", COMPLETER_FILES); 
        completerCmdMap.put("use", COMPLETER_FILES); 

        completerCmdMap.put("sourcepaths", COMPLETER_FILES); 
        completerCmdMap.put("uses", COMPLETER_FILES); 
        
        completerCmdMap.put("stop", COMPLETER_CLASS_METHOD); 
        completerCmdMap.put("b", COMPLETER_CLASS_METHOD); 
        completerCmdMap.put("bi", COMPLETER_CLASS_METHOD); 
        completerCmdMap.put("ba", COMPLETER_CLASS_METHOD); 

        completerCmdMap.put("list", COMPLETER_CLASS_METHOD); 
        completerCmdMap.put("l", COMPLETER_CLASS_METHOD);
        completerCmdMap.put("lines", COMPLETER_CLASS_METHOD);
        completerCmdMap.put("li", COMPLETER_CLASS_METHOD);

        completerCmdMap.put("trace", COMPLETER_CLASS_METHOD);
        completerCmdMap.put("tr", COMPLETER_CLASS_METHOD);

        completerCmdMap.put("load", COMPLETER_CLASS);
        completerCmdMap.put("loa", COMPLETER_CLASS);
        completerCmdMap.put("methods", COMPLETER_CLASS);
        completerCmdMap.put("mt", COMPLETER_CLASS);  
        completerCmdMap.put("redefine", COMPLETER_CLASS);        

        completerCmdMap.put("watch", COMPLETER_CLASS_FIELD);
        completerCmdMap.put("wa", COMPLETER_CLASS_FIELD);   
        completerCmdMap.put("twatch", COMPLETER_CLASS_FIELD);
        completerCmdMap.put("unwatch", COMPLETER_CLASS_FIELD);
        completerCmdMap.put("uw", COMPLETER_CLASS_FIELD);  

        completerCmdMap.put("print", COMPLETER_PRINT); 
        completerCmdMap.put("p", COMPLETER_PRINT); 
        completerCmdMap.put("dump", COMPLETER_PRINT); 
        completerCmdMap.put("du", COMPLETER_PRINT); 
        completerCmdMap.put("edump", COMPLETER_PRINT); 
        completerCmdMap.put("edu", COMPLETER_PRINT); 
        completerCmdMap.put("dumps", COMPLETER_PRINT); 
        completerCmdMap.put("dus", COMPLETER_PRINT); 
        completerCmdMap.put("edumps", COMPLETER_PRINT); 
        completerCmdMap.put("edus", COMPLETER_PRINT);
        completerCmdMap.put("prints", COMPLETER_PRINT); 
        completerCmdMap.put("ps", COMPLETER_PRINT);
        completerCmdMap.put("eprints", COMPLETER_PRINT); 
        completerCmdMap.put("eps", COMPLETER_PRINT);
        completerCmdMap.put("eprint", COMPLETER_PRINT);
        completerCmdMap.put("ep", COMPLETER_PRINT);
   }

    /**
      Default constructor.
    */

    public CommandCompleter() {
    }
  

    /**
      Return possible completion. Implements org.gnu.readline.ReadlineCompleter.
    */

    private Iterator possibleValues;  
    private List extraKey= new ArrayList();

    public String completer (String text, int state) {    
        if (text == null)
            return null;

        String readline = Readline.getLineBuffer();
        StringTokenizer t = new StringTokenizer(readline);

        if (t.countTokens() <= 1) {
            if ((t.countTokens() == 1 && !readline.endsWith(" "))
                    || t.countTokens() == 0 ){
                return completerCmd(text, state);
            }
        }

        String cmd = t.nextToken().toLowerCase();

        if (text.startsWith("{")) {
            return completerBreakCondition(text,state);
        }

        if ((text.length() <= 0 && readline.endsWith("{display ")
            ||(text.length() > 0 && readline.endsWith("{display " + text) ))) {
            return completerPrint(text, state); 
        }

        if ((text.length() <= 0 && readline.endsWith("{p ")
            ||(text.length() >0 && readline.endsWith("{p " + text) ))) {
            return completerPrint(text, state); 
        }


        if ((text.length() <= 0 && readline.endsWith("{if ")
            ||(text.length() > 0 && readline.endsWith("{if "+text) ))) {
            return completerPrint(text, state); 
        }

        String trimline = readline.trim();
        int inx = readline.indexOf(trimline);
        readline=readline.substring(inx);


        if (cmd.equals("m") || cmd.equals("monitor")) {
            String firstCmd=cmd;
            if (t.hasMoreTokens()) {
                cmd= t.nextToken().toLowerCase();
                if (!readline.startsWith(firstCmd + " " + cmd + " "))
                    return null;
            }
        } else if (!readline.startsWith(cmd + " "))
            return null;

        Integer ci =  (Integer)completerCmdMap.get(cmd);
        if (ci == null)
            return null;

        int c = (Integer)ci;

        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();

        if ( c == COMPLETER_CLASS  && threadInfo!=null) {
            extraKey.add("this");
        }

        switch (c) {
            case COMPLETER_FILES:
                return completerFiles(text, state);
            case COMPLETER_CLASS_METHOD:
                return completerClassOrMethod(text, state, true);
            case COMPLETER_CLASS:
                return completerClassOrMethod(text, state, false);
            case COMPLETER_PRINT:
                return completerPrint(text, state);
            case COMPLETER_THREADS:
                return completerThreads(text, state);
            case COMPLETER_CLASS_FIELD:
                return completerClassField(text, state);
        }
        return null;
    }

    String completerThreads(String text, int state) {
        if (state == 0) {
            cmdIndex = 0;
        }

        for (int i = cmdIndex; i < tty.commandList.length ; i++) {
            String var =  tty.commandList[i][0];

            if (var.startsWith(text)) {
                return var;
            }
        }
        return null;
    }

    int cmdIndex = 0;
    
    String completerCmd(String text, int state) {
        if (state == 0) {
            SortedSet  commandSet = new TreeSet();
            for (int i = 0; i < tty.commandList.length ; i++) {
                commandSet.add(tty.commandList[i][0]);
            }

            Iterator scriptCmdIter = tty.scriptCommandFuncMap.keySet().iterator();
            while (scriptCmdIter.hasNext()) {
                String cmd = (String) scriptCmdIter.next();
                commandSet.add(cmd);
            }
            
            possibleValues = commandSet.iterator();
        }

        while (possibleValues.hasNext()) {
            String var = (String)possibleValues.next();

            if (var.startsWith(text)) {
                return var;
            }
        }         
        
        return null;
    }

    String breakConditionCmd[]={"{display", "{if", "{p"};

    String completerBreakCondition(String text, int state) {
        if (state == 0) {
            cmdIndex = 0;
        }

        for (int i = cmdIndex; i < breakConditionCmd.length ; i++) {
            String var = breakConditionCmd[i];

            if (var.startsWith(text)) {
                cmdIndex=i+1;
                return var;
            }
        }

        return null;
    }
    
    String completerFiles(String text, int state) {
        if (state == 0)  {
            if (text.length() == 0)
                text = "./";

            int offset = text.lastIndexOf(File.separatorChar);
            String basedir= null;
            File f = null;
            File[] flist = null; 

            if (offset != -1)
                basedir= text.substring(0,offset+1);
            else {
                text="./"+text;
                basedir=".";
            }

            if (basedir != null)
                f = new File(basedir);
            else
                f = new File(text); 

            if (f == null)
                return null;

            flist = f.listFiles();

            if (flist == null)
                return null;

            SortedSet  commandSet = new TreeSet();

            for (int i = 0; i < flist.length ; i++) {
                String a = null;
                if (flist[i].isDirectory())
                    a = flist[i].getPath()+File.separatorChar;
                else
                    a=  flist[i].getPath();

                commandSet.add(a);
            }

            possibleValues = commandSet.iterator();
        } 
 
        while (possibleValues.hasNext()) {
            String var = (String)possibleValues.next();

            if (var.startsWith(text)) {
                return var;
            }
        } 
        return null;
    }

    private Value evaluate(String expr) {
        Value result = null;
        ExpressionParser.GetFrame frameGetter = null;
        try {
            final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
            if ((threadInfo != null) && (threadInfo.getCurrentFrame() != null)) {           
                frameGetter = new ExpressionParser.GetFrame() {
                        public StackFrame get() throws IncompatibleThreadStateException {
                            return threadInfo.getCurrentFrame();
                        }
                    };
            }
            result = ExpressionParser.evaluate(expr, Env.vm(), frameGetter);
        } catch (Exception ex) {
            return null;
        }
        return result;
    }

    String completerPrint(String text, int state) {
        if (state == 0) {
            StackFrame frame;
            ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
            if (threadInfo == null) {
                return null;
            }

            // Local variables for current frame
            List vars = null;
            SortedSet  commandSet = new TreeSet();
            try {
                frame = threadInfo.getCurrentFrame();
                if (frame == null) 
                    throw new AbsentInformationException();

                vars = frame.visibleVariables();
            } catch (AbsentInformationException aie) {
                return null;
            } catch (IncompatibleThreadStateException exc) {
                return null;
            }

            for (Iterator it = vars.iterator(); it.hasNext(); ) {
                LocalVariable var = (LocalVariable)it.next();
                commandSet.add(var.name());  
            }
            
            Location loc = frame.location();
            if (loc.declaringType().name() != null)
            {
                ReferenceType cls = Env.getReferenceTypeFromToken( loc.declaringType().name());
                if (cls != null) {
                    List fields = cls.allFields();
                    for (int i = 0; i < fields.size(); i++) {
                        Field field = (Field)fields.get(i);
                        commandSet.add(field.name()); 
                    }
                }
            }

            String anonyClass = loc.declaringType().name();
            int offset = anonyClass.lastIndexOf("$");

            while (offset > 0) {
                String pclass = anonyClass.substring(0, offset);                
                if (pclass != null && pclass.length() > 0) {
                    ReferenceType cls = Env.getReferenceTypeFromToken(pclass);
                    if (cls != null) {
                        List fields = cls.allFields();
                        for (int i = 0; i < fields.size(); i++) {
                            Field field = (Field)fields.get(i);
                            commandSet.add(field.name()); 
                        }
                    }

                }else
                    break;

                anonyClass = pclass;
                offset = anonyClass.lastIndexOf("$");
            }            

            offset= text.lastIndexOf(".");
            String varClass  = null;
            if (offset != -1) {
                varClass = text.substring(0,offset);
                Value val = evaluate(varClass);
                if (val != null) {
                    if ((val instanceof ObjectReference) && !(val instanceof StringReference)) {
                        ObjectReference obj = (ObjectReference)val;
                        ReferenceType refType = obj.referenceType();
                        addClassField(obj, refType, refType, commandSet, varClass);
                    }
                }
            }
            possibleValues = commandSet.iterator();              
        }
        
        while (possibleValues.hasNext() ) {
            String var = (String)possibleValues.next();

            if (var.startsWith(text)) {
                return var;
            }
        }

        return null;
    }
    
    private void addClassField(ObjectReference obj, ReferenceType refType,
                                ReferenceType refTypeBase, SortedSet commandSet, String varClass) {
        for (Iterator it = refType.fields().iterator(); it.hasNext(); ) {
            StringBuffer o = new StringBuffer();
            Field field = (Field)it.next();
            commandSet.add(varClass+"."+field.name());
        }

        if (refType instanceof ClassType) {
            ClassType sup = ((ClassType)refType).superclass();
            if (sup != null) {
                addClassField(obj, sup, refTypeBase, commandSet,varClass);
            }
        } else if (refType instanceof InterfaceType) {
            List sups = ((InterfaceType)refType).superinterfaces();
            for (Iterator it = sups.iterator(); it.hasNext(); ) {
                addClassField(obj, (ReferenceType)it.next(), refTypeBase, commandSet, varClass);
            }
        } else {
            // do nothing
        }
    }

    String completerClassOrMethod(String text, int state, boolean isMethod) {
        try {
            int offset= text.lastIndexOf(".");
            SortedSet  commandSet = new TreeSet();

            if (state == 0) {
                String idClass  = null;
                ReferenceType methodClass = null;

                if (offset != -1)
                    idClass = text.substring(0,offset);

                for (Iterator it = Env.vm().allClasses().iterator(); it.hasNext(); ) {
                    ReferenceType refType = (ReferenceType)it.next();
                    if (idClass != null) {
                        if (refType.name().endsWith(idClass)) {
                            methodClass  = refType;
                            idClass = null;
                        }
                    }
                    commandSet.add(refType.name());
                }

                if (methodClass != null && isMethod) {
                    for (Iterator it = methodClass.allMethods().iterator(); it.hasNext();) {
                        Method method = (Method)it.next();
                        String fullmethodname = method.declaringType().name()+"."+typedName(method);
                        commandSet.add(fullmethodname);
                    }
                }


                for (int i = 0; i < extraKey.size();i++) {
                    String extra = (String)extraKey.get(i);
                    commandSet.add(extra);
                }

                possibleValues = commandSet.iterator();   
            }

            while (possibleValues.hasNext()) {
                String var = (String)possibleValues.next();
                if (var.startsWith(text)) {
                    return var;
                }
            }
        } catch(VMNotConnectedException e) {
            return null;
        }

        return null;
    }

    String completerClassField(String text, int state) {
        try {
            int offset= text.lastIndexOf(".");
            SortedSet  commandSet = new TreeSet();

            if (state == 0) {
                String idClass  = null;

                if (offset !=-1)
                    idClass = text.substring(0,offset);

                 for (Iterator it = Env.vm().allClasses().iterator(); it.hasNext();) {
                    ReferenceType refType = (ReferenceType)it.next();
                    
                    if (refType.name().startsWith(text)) {
                            commandSet.add(refType.name());                                                  
                    }                    
                }                

                if (idClass != null && idClass.length() > 0) {
                    ReferenceType cls = Env.getReferenceTypeFromToken(idClass);
                    if (cls != null) {
                        List fields = cls.allFields();
                        for (int i = 0; i < fields.size(); i++) {
                            Field field = (Field)fields.get(i);
                            commandSet.add(idClass+"."+field.name());
                        }
                    }                             
                }                

                ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
                if (threadInfo!=null) {
                    String classname = tty.getCurrentLocationClassName();
                    ReferenceType cls = Env.getReferenceTypeFromToken(classname);
                    if (cls != null) {
                        List fields = cls.allFields();
                        for (int i = 0; i < fields.size(); i++) {
                            Field field = (Field)fields.get(i);
                            commandSet.add("this."+field.name());
                        }
                    }
                }
                possibleValues = commandSet.iterator();   
            }

            while (possibleValues.hasNext()) {
                String var = (String)possibleValues.next();
                if (var.startsWith(text)) {
                    return var;
                }
            }
        } catch(VMNotConnectedException e) {
            return null;
        }
        return null;
    }
}
