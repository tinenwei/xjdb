/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
/*
 * Copyright (c) 1997-2001 by Sun Microsystems, Inc. All Rights Reserved.
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
import com.sun.jdi.connect.Connector;
import com.sun.jdi.request.*;
import com.sun.tools.example.debug.expr.ExpressionParser;
import com.sun.tools.example.debug.expr.ParseException;

import java.text.*;
import java.util.*;
import java.io.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.jar.JarInputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import java.util.Set;
import java.util.HashSet;
import sun.tools.javap.ClassParser;

class Commands {

    abstract class AsyncExecution {
	abstract void action();

	AsyncExecution() {
            execute();
	}

	void execute() {
            /*
             * Save current thread and stack frame. (BugId 4296031)
             */
            final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
            final int stackFrame = threadInfo == null? 0 : threadInfo.getCurrentFrameIndex();
            Thread thread = new Thread("asynchronous jdb command") {
                    public void run() {                    
                        try {
                            action();
                        } catch (UnsupportedOperationException uoe) {
                            //(BugId 4453329)
                            MessageOutput.println("Operation is not supported on the target VM");
                        } catch (Exception e) {
                            MessageOutput.println("Internal exception during operation:",
                                                  e.getMessage());
                        } finally {
                            /*
                             * This was an asynchronous command.  Events may have been
                             * processed while it was running.  Restore the thread and
                             * stack frame the user was looking at.  (BugId 4296031)
                             */
                            if (threadInfo != null) {
                                ThreadInfo.setCurrentThreadInfo(threadInfo);
                                try {
                                    threadInfo.setCurrentFrameIndex(stackFrame);
                                } catch (IncompatibleThreadStateException e) {
                                    MessageOutput.println("Current thread isnt suspended.");
                                } catch (ArrayIndexOutOfBoundsException e) {
                                    MessageOutput.println("Requested stack frame is no longer active:",
                                                          new Object []{new Integer(stackFrame)});
                                }
                            }
                            MessageOutput.printPrompt();
                        }
                    }
                };
            thread.start();
            try {
                thread.join();
            }catch(InterruptedException e) {
                // do nothing
            }
	}
    }

    Commands() {
    }

    TTY tty;
    Commands(TTY tty) {
        this.tty = tty;
    }
    
    private Value evaluateBreakCondition(String expr) throws Exception {
        Value result = null;
        ExpressionParser.GetFrame frameGetter = null;
       
        final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if ((threadInfo != null) && (threadInfo.getCurrentFrame() != null)) {
            frameGetter = new ExpressionParser.GetFrame() {
                        public StackFrame get() throws IncompatibleThreadStateException {
                            return threadInfo.getCurrentFrame();
                        }
                    };
        }
        result = ExpressionParser.evaluate(expr, Env.vm(), frameGetter);        
        return result;
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
        } catch (InvocationException ie) {
            MessageOutput.println("Exception in expression:",
                                  ie.exception().referenceType().name());
        } catch (Exception ex) {
            String exMessage = ex.getMessage();
            if (exMessage == null) {
                MessageOutput.printException(exMessage, ex);
            } else {
                String s;
                try {
                    s = MessageOutput.format(exMessage);
                } catch (MissingResourceException mex) {
                    s = ex.toString();
                }
            }
        }
        return result;
    }

    private String getStringValue() {
         Value val = null;
         String valStr = null;
         try {
              val = ExpressionParser.getMassagedValue();
              valStr = val.toString();
         } catch (ParseException e) { 
              String msg = e.getMessage();
              if (msg == null) {
                  MessageOutput.printException(msg, e);
              } else {
                  String s;
                  try {
                      s = MessageOutput.format(msg);
                  } catch (MissingResourceException mex) {
                      s = e.toString();
                  }
                  MessageOutput.printDirectln(s);    
              }
         }
         return valStr;
    }

    private Value getValue() {
        Value val = null;
        try {
            val = ExpressionParser.getMassagedValue();              
            return val;
        } catch (ParseException e) { 
            String msg = e.getMessage();
            if (msg == null) {
                MessageOutput.printException(msg, e);
            } else {
                String s;
                try {
                    s = MessageOutput.format(msg);
                } catch (MissingResourceException mex) {
                    s = e.toString();
                }
                MessageOutput.printDirectln(s);    
            }
        }
        return val;
    }

    private ThreadInfo doGetThread(String idToken) {
        ThreadInfo threadInfo = null;

        if (idToken.startsWith("#")) {
            idToken = idToken.substring(1);
            try {
                long id = threadIdMap.get(Integer.parseInt(idToken));
                threadInfo = ThreadInfo.getThreadInfo(id);
            } catch (NumberFormatException e) {
                threadInfo = null;
            }
        }else
            threadInfo = ThreadInfo.getThreadInfo(idToken);

        if (threadInfo == null) {
            MessageOutput.println("is not a valid thread id", idToken);
        }
        return threadInfo;
    }

    String typedName(Method method) {
        StringBuffer buf = new StringBuffer();
        buf.append(method.name());
        buf.append("(");

        List args = method.argumentTypeNames();
        int lastParam = args.size() - 1;
        // output param types except for the last
        for (int ii = 0; ii < lastParam; ii++) {
            buf.append((String)args.get(ii));
            buf.append(", ");
        }
        if (lastParam >= 0) {
            // output the last param
            String lastStr = (String)args.get(lastParam);
            if (method.isVarArgs()) {
                // lastParam is an array.  Replace the [] with ...
                buf.append(lastStr.substring(0, lastStr.length() - 2));
                buf.append("...");
            } else {
                buf.append(lastStr);
            }
        }
        buf.append(")");
        return buf.toString();
    }   
                            
    void commandConnectors(VirtualMachineManager vmm) {
        Iterator iter = vmm.allConnectors().iterator();
        if (iter.hasNext()) {
            MessageOutput.println("Connectors available");
        }
        while (iter.hasNext()) {
            Connector cc = (Connector)iter.next();
            String transportName =
                cc.transport() == null ? "null" : cc.transport().name();
            MessageOutput.println();
            MessageOutput.println("Connector and Transport name",
                                  new Object [] {cc.name(), transportName});
            MessageOutput.println("Connector description", cc.description());

            Iterator argIter = cc.defaultArguments().values().iterator();
            if (argIter.hasNext()) {
                while (argIter.hasNext()) {
                    Connector.Argument aa = (Connector.Argument)argIter.next();
                    MessageOutput.println();
                    
                    boolean requiredArgument = aa.mustSpecify();
                    if (aa.value() == null || aa.value() == "") {
                        //no current value and no default.
                        MessageOutput.println(requiredArgument ?
                                              "Connector required argument nodefault" :
                                              "Connector argument nodefault", aa.name());
                    } else {
                        MessageOutput.println(requiredArgument ?
                                              "Connector required argument default" :
                                              "Connector argument default",
                                              new Object [] {aa.name(), aa.value()});
                    } 
                    MessageOutput.println("Connector description", aa.description());
                    
                }
            }
        }
        
    }

    void commandClasses() {
        List list = Env.vm().allClasses();

        StringBuffer classList = new StringBuffer();
        for (int i = 0 ; i < list.size() ; i++) {
            ReferenceType refType = (ReferenceType)list.get(i);
            classList.append(refType.name());
            classList.append("\n");
        }
        MessageOutput.print("** classes list **", classList.toString());
    }

    void commandClass(StringTokenizer t) {
        List list = Env.vm().allClasses();

        if (!t.hasMoreTokens()) {
            MessageOutput.println("No class specified.");
            return;
        }

        String idClass = t.nextToken();
        boolean showAll = false;

        if (t.hasMoreTokens()) {
            if (t.nextToken().toLowerCase().equals("all")) {
                showAll = true;
            } else {
                MessageOutput.println("Invalid option on class command");
                return;
            }
        }
        ReferenceType type = Env.getReferenceTypeFromToken(idClass);
        if (type == null) {
            MessageOutput.println("is not a valid id or class name", idClass);
            return;
        }
        if (type instanceof ClassType) {
            ClassType clazz = (ClassType)type;
            MessageOutput.println("Class:", clazz.name());

            ClassType superclass = clazz.superclass();
            while (superclass != null) {
                MessageOutput.println("extends:", superclass.name());
                superclass = showAll ? superclass.superclass() : null;
            }

            List interfaces = showAll ? clazz.allInterfaces() 
                                      : clazz.interfaces();
            Iterator iter = interfaces.iterator();
            while (iter.hasNext()) {
                InterfaceType interfaze = (InterfaceType)iter.next();
                MessageOutput.println("implements:", interfaze.name());
            }

            List subs = clazz.subclasses();
            iter = subs.iterator();
            while (iter.hasNext()) {
                ClassType sub = (ClassType)iter.next();
                MessageOutput.println("subclass:", sub.name());
            }
            List nested = clazz.nestedTypes();
            iter = nested.iterator();
            while (iter.hasNext()) {
                ReferenceType nest = (ReferenceType)iter.next();
                MessageOutput.println("nested:", nest.name());
            }
        } else if (type instanceof InterfaceType) {
            InterfaceType interfaze = (InterfaceType)type;
            MessageOutput.println("Interface:", interfaze.name());
            List supers = interfaze.superinterfaces();
            Iterator iter = supers.iterator();
            while (iter.hasNext()) {
                InterfaceType superinterface = (InterfaceType)iter.next();
                MessageOutput.println("extends:", superinterface.name());
            }
            List subs = interfaze.subinterfaces();
            iter = subs.iterator();
            while (iter.hasNext()) {
                InterfaceType sub = (InterfaceType)iter.next();
                MessageOutput.println("subinterface:", sub.name());
            }
            List implementors = interfaze.implementors();
            iter = implementors.iterator();
            while (iter.hasNext()) {
                ClassType implementor = (ClassType)iter.next();
                MessageOutput.println("implementor:", implementor.name());
            }
            List nested = interfaze.nestedTypes();
            iter = nested.iterator();
            while (iter.hasNext()) {
                ReferenceType nest = (ReferenceType)iter.next();
                MessageOutput.println("nested:", nest.name());
            }
        } else {  // array type
            ArrayType array = (ArrayType)type;
            MessageOutput.println("Array:", array.name());
        }
    }

    void commandMethods(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No class specified.");
            return;
        }

        String idClass = t.nextToken();

        boolean bStop = false;
        boolean bTemporary = false;

        String breakConditionToken = null;
        if (t.hasMoreTokens()) {
            String token = t.nextToken();
            if (token.equals("b") || token.equals("tb") || 
                    token.equals("break") || token.equals("tbreak"))
                bStop = true;

            if ( token.equals("tb") || token.equals("tbreak"))
                bTemporary = true;

            if (t.hasMoreTokens()) {
                String rest =t.nextToken();
                while (t.hasMoreTokens())
                    rest = rest+" "+t.nextToken();

                Pattern p = Pattern.compile("\\{(.*)\\}") ; 
                Matcher m = p.matcher(rest) ;

                if (m.find() == true) {
                    if (BreakCondition.validate(m.group(1)))
                        breakConditionToken = rest;
                } else {
                    System.out.println("usage: methods classId b(tb) {condition}");
                    System.out.println("{condition} is wrong");
                }
            }
        }

        String bname = null;

        if (idClass.equals("this")) {
            String classname = tty.getCurrentLocationClassName();
            if (classname != null)
                idClass = classname; 
        }      
        
        ReferenceType cls = Env.getReferenceTypeFromToken(idClass);
        if (cls != null) {
            List methods = cls.allMethods();
            StringBuffer methodsList = new StringBuffer();
            for (int i = 0; i < methods.size(); i++) {
                Method method = (Method)methods.get(i);
                
                if (bname != null && ! bname.equals(method.declaringType().name()))
                    break;
          

                methodsList.append(method.declaringType().name());                
                methodsList.append(".");                
                methodsList.append(typedName(method));
                methodsList.append('\n');
                
                if (bStop) {
                    String bpoint = "in "+method.declaringType().name()+"."+typedName(method) ;

                    if (breakConditionToken != null)
                        bpoint = bpoint + " " + breakConditionToken;

                    StringTokenizer tt = new StringTokenizer(bpoint);
                    commandStop(tt, true, bTemporary);
                }
                bname= method.declaringType().name();
            }
            MessageOutput.print("** methods list **", methodsList.toString());
        } else {
            MessageOutput.println("is not a valid id or class name", idClass);
        }
    }

    void commandFields(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No class specified.");
            return;
        }

        String idClass = t.nextToken();
        ReferenceType cls = Env.getReferenceTypeFromToken(idClass);
        if (cls != null) {
            List fields = cls.allFields();
            List visible = cls.visibleFields();
            StringBuffer fieldsList = new StringBuffer();
            for (int i = 0; i < fields.size(); i++) {
                Field field = (Field)fields.get(i);
                String s;
                if (!visible.contains(field)) {
                    s = MessageOutput.format("list field typename and name hidden",
                                             new Object [] {field.typeName(),
                                                            field.name()});
                } else if (!field.declaringType().equals(cls)) {
                    s = MessageOutput.format("list field typename and name inherited",
                                             new Object [] {field.typeName(),
                                                            field.name(),
                                                            field.declaringType().name()});
                } else {
                    s = MessageOutput.format("list field typename and name",
                                             new Object [] {field.typeName(),
                                                            field.name()});
                }
                fieldsList.append(s);
            }
            MessageOutput.print("** fields list **", fieldsList.toString());
        } else {
            MessageOutput.println("is not a valid id or class name", idClass);
        }
    }

    static List<Long> threadIdMap= new ArrayList<Long>();

    private void printThreadGroup(ThreadGroupReference tg) {
        ThreadIterator threadIter = new ThreadIterator(tg);

        MessageOutput.println("Thread Group:", tg.name());
        int maxIdLength = 0;
        int maxNameLength = 0;
        while (threadIter.hasNext()) {
            ThreadReference thr = (ThreadReference)threadIter.next();
            maxIdLength = Math.max(maxIdLength,
                                   Env.description(thr).length());
            maxNameLength = Math.max(maxNameLength,
                                     thr.name().length());
        }

        threadIter = new ThreadIterator(tg);
        threadIdMap.clear();
        int id=0;
        while (threadIter.hasNext()) {
            ThreadReference thr = (ThreadReference)threadIter.next();
	    if (thr.threadGroup() == null) {
                continue; 
            }
            // Note any thread group changes
            if (!thr.threadGroup().equals(tg)) {
                tg = thr.threadGroup();
                MessageOutput.println("Thread Group:", tg.name());
            }

            /*
             * Do a bit of filling with whitespace to get thread ID
             * and thread names to line up in the listing, and also
             * allow for proper localization.  This also works for
             * very long thread names, at the possible cost of lines
             * being wrapped by the display device.
             */

            long uid = thr.uniqueID(); 
            StringBuffer idBuffer = new StringBuffer(Env.description(thr));
            for (int i = idBuffer.length(); i < maxIdLength; i++) {
                idBuffer.append(" ");
            }
            StringBuffer nameBuffer = new StringBuffer(thr.name());
            for (int i = nameBuffer.length(); i < maxNameLength; i++) {
                nameBuffer.append(" ");
            }
            
            /*
             * Select the output format to use based on thread status
             * and breakpoint.
             */
            String statusFormat;
            switch (thr.status()) {
            case ThreadReference.THREAD_STATUS_UNKNOWN:
                if (thr.isAtBreakpoint()) {
                    statusFormat = "Thread description name unknownStatus BP";
                } else {
                    statusFormat = "Thread description name unknownStatus";
                }
                break;
            case ThreadReference.THREAD_STATUS_ZOMBIE:
                if (thr.isAtBreakpoint()) {
                    statusFormat = "Thread description name zombieStatus BP";
                } else {
                    statusFormat = "Thread description name zombieStatus";
                }
                break;
            case ThreadReference.THREAD_STATUS_RUNNING:
                if (thr.isAtBreakpoint()) {
                    statusFormat = "Thread description name runningStatus BP";
                } else {
                    statusFormat = "Thread description name runningStatus";
                }
                break;
            case ThreadReference.THREAD_STATUS_SLEEPING:
                if (thr.isAtBreakpoint()) {
                    statusFormat = "Thread description name sleepingStatus BP";
                } else {
                    statusFormat = "Thread description name sleepingStatus";
                }
                break;
            case ThreadReference.THREAD_STATUS_MONITOR:
                if (thr.isAtBreakpoint()) {
                    statusFormat = "Thread description name waitingStatus BP";
                } else {
                    statusFormat = "Thread description name waitingStatus";
                }
                break;
            case ThreadReference.THREAD_STATUS_WAIT:
                if (thr.isAtBreakpoint()) {
                    statusFormat = "Thread description name condWaitstatus BP";
                } else {
                    statusFormat = "Thread description name condWaitstatus";
                }
                break;
            default:
                throw new InternalError(MessageOutput.format("Invalid thread status."));
            }
            threadIdMap.add(uid);            
            System.out.print(String.format("[%2d]",id));
            MessageOutput.println(statusFormat,
                                  new Object [] {idBuffer.toString(),
                                                 nameBuffer.toString()});
            id++;
        }
    }

    void commandThreads(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            printThreadGroup(ThreadInfo.group());
            return;
        }
        String name = t.nextToken();
        ThreadGroupReference tg = ThreadGroupIterator.find(name);
        if (tg == null) {
            MessageOutput.println("is not a valid threadgroup name", name);
        } else {
            printThreadGroup(tg);
        }
    }

    void commandThreadGroups() {
        ThreadGroupIterator it = new ThreadGroupIterator();
        int cnt = 0;
        while (it.hasNext()) {
            ThreadGroupReference tg = it.nextThreadGroup();
            ++cnt;
            MessageOutput.println("thread group number description name",
                                  new Object [] { new Integer (cnt),
                                                  Env.description(tg),
                                                  tg.name()});
        }
    }
    
    void commandThread(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("Thread number not specified.");
            return;
        }
        ThreadInfo threadInfo = doGetThread(t.nextToken());
        if (threadInfo != null) {
            ThreadInfo.setCurrentThreadInfo(threadInfo);
        }
    }
    
    void commandThreadGroup(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("Threadgroup name not specified.");
            return;
        }
        String name = t.nextToken();
        ThreadGroupReference tg = ThreadGroupIterator.find(name);
        if (tg == null) {
            MessageOutput.println("is not a valid threadgroup name", name);
        } else {
            ThreadInfo.setThreadGroup(tg);
        }
    }
    
    void commandRun(StringTokenizer t) {
        /*
         * The 'run' command makes little sense in a 
         * that doesn't support restarts or multiple VMs. However,
         * this is an attempt to emulate the behavior of the old
         * JDB as much as possible. For new users and implementations
         * it is much more straightforward to launch immedidately
         * with the -launch option.
         */
        VMConnection connection = Env.connection();
        if (!connection.isLaunch()) {
            if (!t.hasMoreTokens()) {
                commandCont();
            } else {
                MessageOutput.println("run <args> command is valid only with launched VMs");
            }
            return;
        } 
        if (connection.isOpen()) {
            MessageOutput.println("VM already running. use cont to continue after events.");
            return;
        }

        /*
         * Set the main class and any arguments. Note that this will work
         * only with the standard launcher, "com.sun.jdi.CommandLineLauncher"
         */
        String args;
        if (t.hasMoreTokens()) {
            args = t.nextToken("");
            boolean argsSet = connection.setConnectorArg("main", args);
            if (!argsSet) {
                MessageOutput.println("Unable to set main class and arguments");
                return;
            } 
        } else {
            args = connection.connectorArg("main");
            if (args.length() == 0) {
                MessageOutput.println("Main class and arguments must be specified");
                return;
            }
        }
        MessageOutput.println("run", args);

        /*
         * Launch the VM.
         */
        connection.open();
        
    }

    void commandLoad(StringTokenizer t) {
        if (!t.hasMoreTokens())         
            System.out.println("The class Id is lost.");
        else
            commandLoadclass(t.nextToken());
    }


    void commandLoadclass(String classId) {
        System.out.println("The class Id is " + classId);
        Value val = evaluate ("java.lang.Class.forName (\"" + classId + "\")");
        if (val == null) {
            System.out.println("The class Id is invalid.");
            return ;
        }

        ObjectReference obj = (ObjectReference)val;
        ReferenceType refType = obj.referenceType();

        dump (obj, refType, refType);
    }

    private List allThreads(ThreadGroupReference group) {
        List list = new ArrayList();
        list.addAll(group.threads());
        Iterator iter = group.threadGroups().iterator();
        while (iter.hasNext()) {
            ThreadGroupReference child = (ThreadGroupReference)iter.next();
            list.addAll(allThreads(child));
        }
        return list;
    }

    void commandSuspend(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            Env.vm().suspend();
            MessageOutput.println("All threads suspended.");
        } else {
            while (t.hasMoreTokens()) {
                ThreadInfo threadInfo = doGetThread(t.nextToken());
                if (threadInfo != null) {
                    threadInfo.getThread().suspend();
                }                
            }
        }
    }

    void commandResume(StringTokenizer t) {
         if (!t.hasMoreTokens()) {
             ThreadInfo.invalidateAll();
             Env.vm().resume();
             MessageOutput.println("All threads resumed.");
         } else {
             while (t.hasMoreTokens()) {
                ThreadInfo threadInfo = doGetThread(t.nextToken());
                if (threadInfo != null) {
                    threadInfo.invalidate();
                    threadInfo.getThread().resume();
                }
            }
        }
    }

    void commandCont() {
        if (ThreadInfo.getCurrentThreadInfo() == null) {
            MessageOutput.println("Nothing suspended.");
            return;
        }
        ThreadInfo.invalidateAll();
        Env.vm().resume();
    }

    void clearPreviousStep(ThreadReference thread) {
        /*
         * A previous step may not have completed on this thread; 
         * if so, it gets removed here. 
         */
         EventRequestManager mgr = Env.vm().eventRequestManager();
         List requests = mgr.stepRequests();
         Iterator iter = requests.iterator();
         while (iter.hasNext()) {
             StepRequest request = (StepRequest)iter.next();
             if (request.thread().equals(thread)) {
                 mgr.deleteEventRequest(request);
                 break;
             }
         }
    }
    /* step
     *
     */
    void commandStep(StringTokenizer t) {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null) {
            MessageOutput.println("Nothing suspended.");
            return;
        }
        int depth;
        if (t.hasMoreTokens() &&
                  t.nextToken().toLowerCase().equals("up")) {
            depth = StepRequest.STEP_OUT;
        } else {
            depth = StepRequest.STEP_INTO;
        }

        clearPreviousStep(threadInfo.getThread());
        EventRequestManager reqMgr = Env.vm().eventRequestManager();
        StepRequest request = reqMgr.createStepRequest(threadInfo.getThread(),
                                                       StepRequest.STEP_LINE, depth);
        if (depth == StepRequest.STEP_INTO) {
            Env.addExcludes(request);
        }
        // We want just the next step event and no others
        request.addCountFilter(1);
        request.enable();
        ThreadInfo.invalidateAll();
        Env.vm().resume();
    }

    /* stepi
     * step instruction.
     */
    void commandStepi() {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null) {
            MessageOutput.println("Nothing suspended.");
            return;
        }
        clearPreviousStep(threadInfo.getThread());
        EventRequestManager reqMgr = Env.vm().eventRequestManager();
        StepRequest request = reqMgr.createStepRequest(threadInfo.getThread(),
                                                       StepRequest.STEP_MIN,
                                                       StepRequest.STEP_INTO);
        Env.addExcludes(request);
        // We want just the next step event and no others
        request.addCountFilter(1);
        request.enable();
        ThreadInfo.invalidateAll();
        Env.vm().resume();
    }

    void commandNext() {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null) {
            MessageOutput.println("Nothing suspended.");
            return;
        }
        clearPreviousStep(threadInfo.getThread());
        EventRequestManager reqMgr = Env.vm().eventRequestManager();
        StepRequest request = reqMgr.createStepRequest(threadInfo.getThread(),
                                                       StepRequest.STEP_LINE,
                                                       StepRequest.STEP_OVER);
        Env.addExcludes(request);
        // We want just the next step event and no others
        request.addCountFilter(1);
        request.enable();
        ThreadInfo.invalidateAll();
        Env.vm().resume();
    }

    void doKill(ThreadReference thread, StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No exception object specified.");
            return;
        }
        String expr = t.nextToken("");
        Value val = evaluate(expr);
        if ((val != null) && (val instanceof ObjectReference)) {
            try {
                thread.stop((ObjectReference)val);
                MessageOutput.println("killed", thread.toString());
            } catch (InvalidTypeException e) {
                MessageOutput.println("Invalid exception object");
            }
        } else {
            MessageOutput.println("Expression must evaluate to an object");
        }
    }

    void doKillThread(final ThreadReference threadToKill,
                      final StringTokenizer tokenizer) {
        new AsyncExecution() {
                void action() {
                    doKill(threadToKill, tokenizer);
                }
            }; 
    }

    void commandKill(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("Usage: kill <thread id> <throwable>");
            return;
        }
        ThreadInfo threadInfo = doGetThread(t.nextToken());
        if (threadInfo != null) {
            MessageOutput.println("killing thread:", threadInfo.getThread().name());
            doKillThread(threadInfo.getThread(), t);
            return;
        }
    }

    void listCaughtExceptions() {
        boolean noExceptions = true;

        // Print a listing of the catch patterns currently in place
        Iterator iter = Env.specList.eventRequestSpecs().iterator();
        while (iter.hasNext()) {
            EventRequestSpec spec = (EventRequestSpec)iter.next();
            if (spec instanceof ExceptionSpec) {
                if (noExceptions) {
                    noExceptions = false;
                    MessageOutput.println("Exceptions caught:");
                }
                MessageOutput.println("tab", spec.toString());
            }
        }
        if (noExceptions) {
            MessageOutput.println("No exceptions caught.");
        }
    }

    private EventRequestSpec parseExceptionSpec(StringTokenizer t) {
        String notification = t.nextToken();
        boolean notifyCaught = false;
        boolean notifyUncaught = false;
        EventRequestSpec spec = null;
        String classPattern = null;
        
        if (notification.equals("uncaught")) {
            notifyCaught = false;
            notifyUncaught = true;
        } else if (notification.equals("caught")) {
            notifyCaught = true;
            notifyUncaught = false;
        } else if (notification.equals("all")) {
            notifyCaught = true;
            notifyUncaught = true;
        } else {
            /*
             * Handle the same as "all" for backward
             * compatibility with existing .jdbrc files.
             *
             * Insert an "all" and take the current token as the
             * intended classPattern
             *
             */
            notifyCaught = true;
            notifyUncaught = true;
            classPattern = notification;
        }
        if (classPattern == null && t.hasMoreTokens()) {
            classPattern = t.nextToken();
        }
        if ((classPattern != null) && (notifyCaught || notifyUncaught)) {
            try {
                spec = Env.specList.createExceptionCatch(classPattern,
                                                         notifyCaught,
                                                         notifyUncaught);
            } catch (ClassNotFoundException exc) {
                MessageOutput.println("is not a valid class name", classPattern);
            }
        }
        return spec;
    }

    void commandCatchException(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            listCaughtExceptions();
        } else { 
            EventRequestSpec spec = parseExceptionSpec(t);
            if (spec != null) {
                resolveNow(spec, false);
            } else {
                MessageOutput.println("Usage: catch exception");
            }
        }
    }
    
    void commandIgnoreException(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            listCaughtExceptions();
        } else { 
            EventRequestSpec spec = parseExceptionSpec(t);
            if (Env.specList.delete(spec)) {
                MessageOutput.println("Removed:", spec.toString());
            } else {
                if (spec != null) {
                    MessageOutput.println("Not found:", spec.toString());
                }
                MessageOutput.println("Usage: ignore exception");
            }
        }
    }
    
    void commandUp(StringTokenizer t) {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null) {
            MessageOutput.println("Current thread not set.");
            return;
        }

        int nLevels = 1;
        if (t.hasMoreTokens()) {
            String idToken = t.nextToken();
            int i;
            try {
                NumberFormat nf = NumberFormat.getNumberInstance();
                nf.setParseIntegerOnly(true);
                Number n = nf.parse(idToken);
                i = n.intValue();
            } catch (java.text.ParseException jtpe) {
                i = 0;
            }
            if (i <= 0) {
                MessageOutput.println("Usage: up [n frames]");
                return;
            }
            nLevels = i;
        }

        try {
            threadInfo.up(nLevels);
            tty.printCurrentLocation();
            tty.currListLoc = -1;
            commandList( new StringTokenizer(""));
        } catch (IncompatibleThreadStateException e) {
            MessageOutput.println("Current thread isnt suspended.");
        } catch (ArrayIndexOutOfBoundsException e) {
            MessageOutput.println("End of stack.");
        }
    }

    void commandDown(StringTokenizer t) {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null) {
            MessageOutput.println("Current thread not set.");
            return;
        }

        int nLevels = 1;
        if (t.hasMoreTokens()) {
            String idToken = t.nextToken();
            int i;
            try {
                NumberFormat nf = NumberFormat.getNumberInstance();
                nf.setParseIntegerOnly(true);
                Number n = nf.parse(idToken);
                i = n.intValue();
            } catch (java.text.ParseException jtpe) {
                i = 0;
            }
            if (i <= 0) {
                MessageOutput.println("Usage: down [n frames]");
                return;
            }
            nLevels = i;
        }

        try {
            threadInfo.down(nLevels);
            tty.printCurrentLocation();
            tty.currListLoc = -1;
            commandList( new StringTokenizer(""));
        } catch (IncompatibleThreadStateException e) {
            MessageOutput.println("Current thread isnt suspended.");
        } catch (ArrayIndexOutOfBoundsException e) {
            MessageOutput.println("End of stack.");
        }
    }

    void commandFrame(StringTokenizer t) {
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null) {
            MessageOutput.println("Current thread not set.");
            return;
        }

        int nLevels = 1;
        if (t.hasMoreTokens()) {
            String idToken = t.nextToken();
            int i;
            try {
                NumberFormat nf = NumberFormat.getNumberInstance();
                nf.setParseIntegerOnly(true);
                Number n = nf.parse(idToken);
                i = n.intValue();
            } catch (java.text.ParseException jtpe) {
                i = 0;
            }
            if (i < 0) {
                System.out.println("Usage: frame [n-th frames]");
                return;
            }
            nLevels = i;
        }
        /*
        if ((nLevels < 0) || (nLevels >= thread.frameCount())) {
            throw new ArrayIndexOutOfBoundsException();
        }
        */
        try {
            if (nLevels <= 0)
                nLevels = 1;

            threadInfo.setCurrentFrameIndex(nLevels-1);
            tty.printCurrentLocation(); 
            tty.currListLoc = -1;
            commandList( new StringTokenizer(""));
        } catch (IncompatibleThreadStateException e) {
            MessageOutput.println("Current thread isnt suspended.");
        } catch (ArrayIndexOutOfBoundsException e) {
            MessageOutput.println("End of stack.");
        }
    }
    private void dumpStack(ThreadInfo threadInfo, boolean showPC) {
        List stack = null;
        try {
            stack = threadInfo.getStack();
        } catch (IncompatibleThreadStateException e) {
            MessageOutput.println("Current thread isnt suspended.");
            return;
        }
        if (stack == null) {  
            MessageOutput.println("Thread is not running (no stack).");
        } else {
            int nFrames = stack.size();
            
            if (displayFrames!=-1)
                nFrames = Math.min(threadInfo.getCurrentFrameIndex()+displayFrames, nFrames);

            for (int i = threadInfo.getCurrentFrameIndex(); i < nFrames; i++) {
                StackFrame frame = (StackFrame)stack.get(i);
                dumpFrame (i, showPC, frame);
            }
        }
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

    int displayFrames = -1;    
    void commandWhere(StringTokenizer t, boolean showPC) {
        displayFrames = -1;

        String token = null; 
        if (t.hasMoreTokens())
            token= t.nextToken();   

        if (token!=null && token.length() >= 2 &&  token.startsWith("/")) {
            String digits= token.substring(1, token.length());
            
            try {
                displayFrames = Integer.parseInt(digits);
            } catch (NumberFormatException exc) {
                System.out.println("where /n (all/thread_id)");
                return ;
            }
            
            if (t.hasMoreTokens())
                token= t.nextToken();
            else 
                token = null;
        }
        if (token == null) {
            ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
            if (threadInfo == null) {
                MessageOutput.println("No thread specified.");
                return;
            }
            dumpStack(threadInfo, showPC);
        } else {        
            if (token.toLowerCase().equals("all")) {
                Iterator iter = ThreadInfo.threads().iterator();
                while (iter.hasNext()) {
                    ThreadInfo threadInfo = (ThreadInfo)iter.next();
                    MessageOutput.println("Thread:",
                                          threadInfo.getThread().name());
                    dumpStack(threadInfo, showPC);
                }
            } else {
                ThreadInfo threadInfo = doGetThread(token);
                if (threadInfo != null) {
                    ThreadInfo.setCurrentThreadInfo(threadInfo);
                    dumpStack(threadInfo, showPC);
                }
            }
        }
    }

    void commandInterrupt(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
            if (threadInfo == null) {
                MessageOutput.println("No thread specified.");
                return;
            }
            threadInfo.getThread().interrupt();
        } else {
            ThreadInfo threadInfo = doGetThread(t.nextToken());
            if (threadInfo != null) {
                threadInfo.getThread().interrupt();
            }
        }
    }

    void commandMemory() {
        MessageOutput.println("The memory command is no longer supported.");
    }

    void commandGC() {
        MessageOutput.println("The gc command is no longer necessary.");
    }

    /*
     * The next two methods are used by this class and by EventHandler
     * to print consistent locations and error messages.
     */
    static String locationString(Location loc) {
        return MessageOutput.format("locationString",
                                    new Object [] {loc.declaringType().name(),
                                                   loc.method().name(),
                                                   new Integer (loc.lineNumber()),
                                                   new Long (loc.codeIndex())});
    }

    void listBreakpoints() {
        boolean noBreakpoints = true;

        // Print set breakpoints
        Iterator iter = Env.specList.eventRequestSpecs().iterator();
        int i = 0;
        while (iter.hasNext()) {
            EventRequestSpec spec = (EventRequestSpec)iter.next();
            if (spec instanceof BreakpointSpec || spec instanceof WatchpointSpec) {
                if (noBreakpoints) {
                    noBreakpoints = false;
                    MessageOutput.println("Breakpoints set:");
                    MessageOutput.println("tab", "[id] temporary enable resolve command breakpoint condition hits");
                }

                EventRequestSpecList.Property p =  (EventRequestSpecList.Property) Env.specList.eventRequestSpecsID().get(i);
                String enable = "e";
                if (!p.enable)
                    enable = "d";

                String resolve = "y";
                if (spec.resolved() == null )
                    resolve = "n";

                String temporary = " ";
                if (p.temporary)
                    temporary = "t";

                String cmdstatus = "  ";
                if (p.cmdList != null) {
                    if (p.scriptObj != null) {
                        // ascriptBreakpoint
                        /*
                        boolean ascriptBreakpoint = false;
                        Iterator it = p.cmdList.iterator();
                        while (it.hasNext()) {
                            String c = (String)it.next();
                            if (c.startsWith("[ascript]")) {
                                ascriptBreakpoint = true;
                                break;
                            }
                        }
                        if (ascriptBreakpoint)
                            cmdstatus = "as";
                        else
                        */
                        // ~ascriptBreakpoint
                            cmdstatus = " s";

                    } else    
                        cmdstatus = " c";
                }

                String breakcondition = "";
                if (p.breakcondition!=null)
                    breakcondition = "{" + p.breakcondition + "}";


                MessageOutput.println("tab", String.format("[%3d] %s %s %s %s %s %s (hits: %d)", 
                            p.id, temporary, enable, resolve, cmdstatus, spec.toString(), breakcondition, p.count));
            }
            i++;
        }

        boolean noException = true;
        i = 0;
        iter = Env.specList.eventRequestSpecs().iterator();
        while (iter.hasNext()) {
            EventRequestSpec spec = (EventRequestSpec)iter.next();
            if (spec instanceof ExceptionSpec) {
                if (noException) {
                    noException = false;
                    System.out.println("Exceptions set:");
                }
                EventRequestSpecList.Property p =  (EventRequestSpecList.Property) Env.specList.eventRequestSpecsID().get(i);
                MessageOutput.println("tab", String.format("[%3d]  %s", p.id, spec.toString()));
            }           
            i++;
        }
        if (noBreakpoints) {
            MessageOutput.println("No breakpoints set.");
        }
    }


    private void printBreakpointCommandUsage(String atForm, String inForm) {
        MessageOutput.println("printbreakpointcommandusage",
                              new Object [] {atForm, inForm});
    }

    String parsedClassId = null;
    int  parsedLineNumber = -1;
    protected BreakpointSpec parseBreakpointSpec(StringTokenizer t, 
                                             String atForm, String inForm) {
        EventRequestSpec breakpoint = null;
        try {
            String token = t.nextToken(":( \t\n\r");

            // We can't use hasMoreTokens here because it will cause any leading
            // paren to be lost.
            String rest;
            try {
                rest = t.nextToken("").trim();
            } catch (NoSuchElementException e) {
                rest = null;
            }

            if ((rest != null) && rest.startsWith(":")) {
                t = new StringTokenizer(rest.substring(1));
                String classId = token;
                String lineToken = t.nextToken();
                parsedClassId  = classId;
                NumberFormat nf = NumberFormat.getNumberInstance();
                nf.setParseIntegerOnly(true);
                Number n = nf.parse(lineToken);
                int lineNumber = n.intValue();
                parsedLineNumber = lineNumber;

                if (t.hasMoreTokens()) {
                    printBreakpointCommandUsage(atForm, inForm);
                    return null;
                }
                try { 
                    breakpoint = Env.specList.createBreakpoint(classId, 
                                                               lineNumber);
                } catch (ClassNotFoundException exc) {
                    MessageOutput.println("is not a valid class name", classId);
                }
            } else {
                // Try stripping method from class.method token.
                int idot = token.lastIndexOf(".");
                if ( (idot <= 0) ||                     /* No dot or dot in first char */
                     (idot >= token.length() - 1) ) { /* dot in last char */
                    printBreakpointCommandUsage(atForm, inForm);
                    return null;
                }
                String methodName = token.substring(idot + 1);
                String classId = token.substring(0, idot);
                List argumentList = null;
                if (rest != null) {
                    if (!rest.startsWith("(") || !rest.endsWith(")")) {
                        MessageOutput.println("Invalid method specification:",
                                              methodName + rest);
                        printBreakpointCommandUsage(atForm, inForm);
                        return null;
                    }
                    // Trim the parens
                    rest = rest.substring(1, rest.length() - 1);

                    argumentList = new ArrayList();
                    t = new StringTokenizer(rest, ",");
                    while (t.hasMoreTokens()) {
                        argumentList.add(t.nextToken());
                    }
                }
                try {
                    breakpoint = Env.specList.createBreakpoint(classId, 
                                                               methodName, 
                                                               argumentList);
                } catch (MalformedMemberNameException exc) {
                    MessageOutput.println("is not a valid method name", methodName);
                } catch (ClassNotFoundException exc) {
                    MessageOutput.println("is not a valid class name", classId);
                }
            }
        } catch (Exception e) {
            printBreakpointCommandUsage(atForm, inForm);
            return null;
        }
        return (BreakpointSpec)breakpoint;
    }


    private boolean resolveNow(EventRequestSpec spec, boolean temporary) {     
        System.out.println("resolve ["+spec.toString()+"] .....");
        boolean success = Env.specList.addEagerlyResolve(spec, temporary);
        if (success && !spec.isResolved()) {
            MessageOutput.println("Deferring.", spec.toString());
        }
        return success;
    }

    boolean commandStop(StringTokenizer t, boolean checkInnerClass , boolean temporary) {
        Location bploc;
        String atIn;
        byte suspendPolicy = EventRequest.SUSPEND_ALL;

        if (t.hasMoreTokens()) {
            atIn = t.nextToken();
            if (atIn.equals("go") && t.hasMoreTokens()) {
                suspendPolicy = EventRequest.SUSPEND_NONE;
                atIn = t.nextToken();
            } else if (atIn.equals("thread") && t.hasMoreTokens()) {
                suspendPolicy = EventRequest.SUSPEND_EVENT_THREAD;
                atIn = t.nextToken();
            }            

            if (Character.isDigit(atIn.charAt(0))) {
                try {
                    int breakline = Integer.parseInt(atIn);
                    String declaringType =tty.getCurrentLocationClassName();
                    if (declaringType != null) {
                        atIn = "at";
                        String s = declaringType + ":" + breakline;
                        
                        while(t.hasMoreTokens())
                            s = s + " " + t.nextToken();

                        t = new StringTokenizer(s);
                    } else {
                        System.out.println("declaringType is null.");
                        return false;
                    }
                } catch (NumberFormatException exc) {
                    System.out.println("b number (expr)");
                    return false;
                }
            }
        } else {
            listBreakpoints();
            return false;
        }

        String breakcondition = null;
        if (t.hasMoreTokens()) {
            String rest =t.nextToken();
            while (t.hasMoreTokens())
                rest = rest + " " + t.nextToken();

            String s = null ;
            Pattern p = Pattern.compile("(.*)\\{(.*)\\}.*"); 
            Matcher m = p.matcher(rest);
            if (m.find() == true) {
                s=m.group (1);
                breakcondition = m.group(2).trim();
                if (!BreakCondition.validate(breakcondition)) {
                    System.out.println("{condition} is not correct!");
                    return false;
                }
            } else {
                breakcondition=null;
                s=rest;
            }

            t = new StringTokenizer(s);
        }        

        parsedClassId = null;
        parsedLineNumber = -1;
        BreakpointSpec spec = parseBreakpointSpec(t, "stop at", "stop in");
        if (spec != null) {
            // Enforcement of "at" vs. "in". The distinction is really 
            // unnecessary and we should consider not checking for this 
            // (and making "at" and "in" optional).
            if (atIn.equals("at") && spec.isMethodBreakpoint()) {
                MessageOutput.println("Use stop at to set a breakpoint at a line number");
                printBreakpointCommandUsage("stop at", "stop in");
                return false;
            }
            spec.suspendPolicy = suspendPolicy;
            if (parsedClassId == null) {
                resolveNow(spec, temporary);
            } else 
            if (parsedClassId != null && !resolveNow(spec, temporary) && checkInnerClass) {
                SortedSet classSet = getListClassMethod(parsedClassId);
                Iterator it= classSet.iterator(); 
                String classId = null;
                Set<String> handledClasses = new HashSet<String>();

                System.out.println("Check in loaded inner or anonymous classes .....");
                System.out.println("");
                while (it.hasNext())  {
                    String id = (String) it.next();
                    handledClasses.add(id);
                    //bMatchAnonymousClass = false;
                    if (matchClassMethodLines(id, parsedLineNumber)) {// handle loaded inner class
                        boolean result = false;
                        classId = id;
                        //bMatchAnonymousClass = true;                        
                        String breakString = null;
                        //if (breakexpr != null && breakcmd != null)
                        if (breakcondition != null)
                            breakString = "at " + classId + ":" + parsedLineNumber + " {" + breakcondition + "}";
                        else
                            breakString = "at " + classId + ":" + parsedLineNumber;

                        result = commandStop(new StringTokenizer(breakString), false, temporary);
                        return result;
                    }
                }
                
                // handle not loaded inner class, now it is valid for classpath, not valid for customed classloader.
                {
                    System.out.println("Load inner or anonymous classes that have never been loaded to check .....");
                    //System.out.println("");
                    String tParsedClassId=parsedClassId;
                    List<String> mls = matchClassFromClasspath(tParsedClassId);
                    boolean result = false;
                    boolean found = false;
                    for (String ml : mls) {
                        if (ml.startsWith(tParsedClassId + "$")) {
                            String breakString = null;
                            classId = ml.substring(0, ml.length()-6); // remove ".class"

                            if (handledClasses.contains(classId))
                                continue;

                            Value val = evaluate ("java.lang.Class.forName (\"" + classId + "\")");
                            if(val==null || !matchClassMethodLines(classId, parsedLineNumber))
                                continue;                            

                            if (breakcondition!=null)
                                breakString = "at " + classId + ":" + parsedLineNumber + " {" + breakcondition + "}";
                            else
                                breakString = "at " + classId + ":" + parsedLineNumber;

                            boolean res= commandStop(new StringTokenizer(breakString), false, temporary);
                            result = (result || res);
                            found = true;
                        }
                    }

                    if(!found) {
                        System.out.println("Search in classpath to parse and check inner or anonymous classes ..... ");
                        //System.out.println("");
                        classId = matchClassFromClasspathForLine(parsedClassId, parsedLineNumber);//  classid doesn't end with ".class"
                        if (classId != null) {
                            String breakString = null;
                            
                            if(breakcondition!=null)
                                breakString = "at "+classId+":"+parsedLineNumber +" {" + breakcondition +"}" ;
                            else
                                breakString = "at "+classId+":"+parsedLineNumber;

                            boolean res= commandStop(new StringTokenizer(breakString), false, temporary);
                            result = (result || res);
                            found = true;
                        }
                    }
                    
                    if (found)
                        return result;
                }
                // ~handle not loaded inner class, now it is valid for classpath, not valid for customed classloader.
            }            
            
            if (breakcondition != null) {
                EventRequestSpecList.Property p = null;
                p = getLastBreakPoint();

                if (p!=null) {
                    p.breakcondition = breakcondition;
                    BreakCondition.println(breakcondition);
                } else {
                    System.out.println("condition is invalid!!");
                }
            }
            return true;
        }
        return false;

    }

    void commandClear(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            listBreakpoints();
            return;
        }
        
        BreakpointSpec spec = parseBreakpointSpec(t, "clear", "clear");
        if (spec != null) {         
            if (Env.specList.delete(spec)) {
                MessageOutput.println("Removed:", spec.toString());
            } else {
                MessageOutput.println("Not found:", spec.toString());
            }
        }
    }
    
    void commandWrite(StringTokenizer t, boolean appendMode) {
        String fileName = null;
        boolean noBreakpoints = true;

        if (Env.specList.eventRequestSpecs().size() <= 0) {
            System.out.println("Nothing to save!!");
            return;
        }

        if (!t.hasMoreTokens()) {      
            fileName = ".jdbrc";
        } else {
            fileName = t.nextToken();
        }
        
        try {
        
            FileWriter fileout = new FileWriter(fileName, appendMode);

            if (appendMode) {
                fileout.write(String.format("\n"));
                fileout.write(String.format("dis\n"));
                fileout.write(String.format("\n"));
            }

            Iterator iter = Env.specList.eventRequestSpecs().iterator();

            int i=0;
            int k = 0;
            while (iter.hasNext()) {
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                if (spec instanceof BreakpointSpec ||  spec instanceof WatchpointSpec) {
                    if (noBreakpoints) {
                        noBreakpoints = false;
                    }

                    EventRequestSpecList.Property p = (EventRequestSpecList.Property)Env.specList.eventRequestSpecsID().get(i);

                    boolean scriptBreakpoint = false;

                    if (p.cmdList != null) {
                        Iterator it = p.cmdList.iterator();
                        while (it.hasNext()) {
                            String c = (String)it.next();
                            if (c.startsWith("[script]")) {
                                scriptBreakpoint = true;
                                break;
                            }
                        }
                    }
                    
                    if (scriptBreakpoint) {
                        i++;k++;
                        continue;
                    }

                    if (spec instanceof WatchpointSpec) {
                        fileout.write(String.format("watch %s", ((WatchpointSpec)spec).classField()));
                    } else {
                        String [] s = spec.toString().split(" ");
                        if (s == null)  continue;

                        String ss = null;

                        for (int j = 1; j < s.length ; j++) {
                            if (j == 1)
                                ss = s[j];
                            else
                                ss += s[j];
                        }

                        if (ss.indexOf(":") != -1)
                            fileout.write(String.format("stop at %s", ss));
                        else
                            fileout.write(String.format("stop in %s", ss));
                    }

                    if (p.breakcondition!=null)
                        fileout.write(String.format(" {%s}\n", p.breakcondition));
                    else
                        fileout.write("\n");

                    if (p.cmdList != null) {
                        Iterator it = p.cmdList.iterator();
                        while (it.hasNext()) {
                            String c = (String)it.next();
                            fileout.write("command !! " + c + "\n");
                        }
                    }

                    if (!p.enable)
                        fileout.write("disable !!\n");

                    if (p.temporary)
                        fileout.write("temporary !!\n");

                    k++;
                }
                i++;
            }

            if (appendMode)
                System.out.println("Append to file ./" + fileName + ".");
            else
                System.out.println("Overwrite to file ./" + fileName + ".");

            if (noBreakpoints) {
                MessageOutput.println("No breakpoints set.");
            }

            fileout.close();        
        } catch( Exception ex) {
            ex.printStackTrace();
        }
    }
    
    void commandDelete(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            String ans = "n";
            boolean deleteAll = true;
            BufferedReader br =
                 new BufferedReader(new InputStreamReader(System.in));

            System.out.print("Are you sure to delete all breakpoints (y/n)?  ");
            String strLine = null;

            try {
                ans = br.readLine();
            } catch(Exception e) {
                System.out.println("Error while reading line from console : " + e);              
                deleteAll = false;
            }

            if (ans.toLowerCase().equals("n")) {
                deleteAll = false;
            }

            if (deleteAll) {                
                Env.specList.deleteAll();
                listBreakpoints();
            }
            
            if (br != null) {
                try {
                    br.close();
                } catch (Exception exc) {
                    // do nothing
                }
            }
            return;
        }
        String breakpointNum = t.nextToken();

        if (Env.specList.eventRequestSpecsID().size() <= 0) {
            System.out.println("Breakpoints is empty. ");
            return;
        }

        if (breakpointNum.contains("-")) {
            String [] s = breakpointNum.split("-");

            if (s == null) {
                System.out.println("delete number [num-num]");
                return;
            }

            try {
                EventRequestSpecList.Property p = getLastBreakPoint();
                int max = p.id;
                int start = 0 ;
                int end = max;

                if (breakpointNum.startsWith("-")) {
                    end = Integer.parseInt(s[0]);
                }
 
                if (breakpointNum.endsWith("-")) {
                    start = Integer.parseInt(s[0]); 
                }

                if (!breakpointNum.startsWith("-") && !breakpointNum.endsWith("-")) {
                    if (s[0] != null && s[0].length() > 0)
                        start = Integer.parseInt(s[0]);

                    if (s[1] != null && s[1].length() > 0)
                        end = Integer.parseInt(s[1]);
                }

                if (start > end) {
                    System.out.println("delete number [num1-num2]:  num1 > num2");
                    return;
                }

                end = Math.min(end, max);
                if (start < 0) start = 0;
                if (Env.specList.delete(start, end)) {
                    listBreakpoints();
                    return;
                }

            } catch (NumberFormatException exc) {
                System.out.println("delete number [num-num]");
                return;
            }
        } else if (t.countTokens() >= 1) {
            try {
                int restToken = t.countTokens();
                int[] ids = new int[restToken + 1];
                ids[0] = Integer.parseInt(breakpointNum);

                for (int i = 1; i < restToken + 1; i++) {
                    String strNum = t.nextToken();
                    int bnum = Integer.parseInt(strNum);
                    ids[i] = bnum;
                }

                if (Env.specList.delete(ids)) {
                    listBreakpoints();
                    return;
                }
            } catch( NumberFormatException exc) {
                System.out.println("delete number number ..");
                return;
            }
        }
        
        if (breakpointNum.equals("!!")) {
            if (Env.specList.eventRequestSpecsID().size() == 0) {
                System.out.println("The breakpoint list  is empty.");
                return ;
            }

            EventRequestSpecList.Property p = getLastBreakPoint();
            if (p == null) {
                System.out.println("The breakpoint list  is empty.");
                return ;
            }

            breakpointNum = Integer.toString(p.id);
        }

        if (Character.isDigit(breakpointNum.charAt(0))) {
            try {
                int bnum = Integer.parseInt(breakpointNum);

                Iterator iter = Env.specList.eventRequestSpecsID().iterator();
                boolean bDelete = false;
                int index = 0;
                while (iter.hasNext()) {
                    EventRequestSpecList.Property p = (EventRequestSpecList.Property)iter.next();
                    if (p.id == bnum) {
                        bDelete = true;
                        break;
                    }
                    index++;
                }

                if (bDelete) {
                    System.out.println("Removed: "+Env.specList.eventRequestSpecs().get(index).toString());
                    Env.specList.delete(index);
                } else
                    System.out.println("Not found: breakpoint " + bnum);

            } catch (NumberFormatException exc) {
                System.out.println("delete number");
                return;
            }
        } else {
            System.out.println("delete number");
        }
    }

    void commandCondition(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            System.out.println("condition number {condition}");
            return;
        }

        if (Env.specList.eventRequestSpecsID().size() <= 0) {
            System.out.println("Breakpoints is empty. ");
            return;
        }
        
        String breakpointNum = t.nextToken();
        String breakcondition = null;

        String rest = "";
        if (t.hasMoreTokens()) {
            String token = t.nextToken();
            boolean condPart = false;
            String cond = "";

            if (Character.isDigit(token.charAt(0)) && !condPart)
                rest=token;
            else {
                condPart = true;
                cond=token;
            }

            while (t.hasMoreTokens()) {
                token =t.nextToken();
                if (Character.isDigit(token.charAt(0))&& !condPart) {
                    if(rest.length()>0)
                        rest = rest + " " + token;
                    else
                        rest = token;
                } else {
                    condPart = true;
                    if (cond.length() > 0)
                        cond=cond+ " "+token;
                    else
                        cond=token;
                }
            }

            if (condPart) {
                Pattern p = Pattern.compile( "\\{(.*)\\}" ) ; 
                Matcher m = p.matcher( cond ) ;

                if (m.find() == true) {
                    if (BreakCondition.validate(m.group(1))) {
                        breakcondition = m.group(1).trim();
                    }
                }
            } else {
                breakcondition="";
            }
        } else {
            breakcondition="";
        }

        t = new StringTokenizer(rest);

        if (breakcondition == null) {
            System.out.println("usage: condition num {condition}");
            System.out.println("{condition} is wrong");
            return;
        }

        if (breakpointNum.contains("-")) {
            String[] s = breakpointNum.split("-");

            if (s == null) {
                System.out.println("condition number [num-num] {condition}");
                return;
            }

            try {
                EventRequestSpecList.Property p = getLastBreakPoint();
                int max = p.id;
                int start = 0 ;
                int end = max;
                
                if (breakpointNum.startsWith("-")) {
                    end = Integer.parseInt(s[0]);
                }

                if (breakpointNum.endsWith("-")) {
                    start = Integer.parseInt(s[0]); 
                }

                if (!breakpointNum.startsWith("-") && !breakpointNum.endsWith("-")) {
                    if (s[0] != null && s[0].length() > 0)
                        start = Integer.parseInt(s[0]);

                    if (s[1] != null && s[1].length() > 0)
                        end = Integer.parseInt(s[1]);
                }

                if (start > end) {
                    System.out.println("condition number [num1-num2]:  num1 > num2  {condition}");
                    return;
                }

                end = Math.min(end, max);
                if (start < 0) start = 0;
                if (Env.specList.breakCondition(start, end, breakcondition)) {
                    listBreakpoints();
                    return;
                }
            } catch (NumberFormatException exc) {
                System.out.println("condition number [num-num] {condition}");
                return;
            }

        } else if (t.countTokens() >= 1) {
            try {
                int restToken = t.countTokens();
                int[] ids = new int[restToken + 1];
                ids[0] = Integer.parseInt(breakpointNum);

                for (int i = 1;i < restToken + 1; i++) {
                    String strNum = t.nextToken();
                    int bnum = Integer.parseInt(strNum);
                    ids[i] = bnum;
                }

                if (Env.specList.breakCondition(ids, breakcondition)) {
                    listBreakpoints();
                    return;
                }
            } catch( NumberFormatException exc) {
                System.out.println("condition number number .. {condition}");
                return;
            }
        }
        
        if (breakpointNum.equals("!!")) {
            if (Env.specList.eventRequestSpecsID().size() == 0) {
                System.out.println("The breakpoint list  is empty.");
                return ;
            }

            EventRequestSpecList.Property p = getLastBreakPoint();
            if (p == null) {
                System.out.println("The breakpoint list  is empty.");
                return ;
            }

            breakpointNum = Integer.toString(p.id);
        }

        if (Character.isDigit(breakpointNum.charAt(0))) {
            try {
                int bnum = Integer.parseInt(breakpointNum);
                Iterator iter = Env.specList.eventRequestSpecsID().iterator();
                boolean bCondition = false;
                int index = 0;
                while (iter.hasNext()) {
                    EventRequestSpecList.Property p = (EventRequestSpecList.Property)iter.next();
                    if (p.id == bnum) {
                        bCondition = true;
                        if (breakcondition.length() > 0) {
                            p.breakcondition = breakcondition;
                        } else {
                            p.breakcondition = null;
                        }
                        break;
                    }
                    index++;
                }

                if (bCondition) {
                    EventRequestSpec spec = (EventRequestSpec)Env.specList.eventRequestSpecs().get(index);
                    if (breakcondition.length() > 0)
                        System.out.println("condition : [" + bnum + "] " + Env.specList.eventRequestSpecs().get(index).toString() + " {"+breakcondition+"}");
                    else
                        System.out.println("undo condition : [" + bnum + "] " + Env.specList.eventRequestSpecs().get(index).toString());
                } else
                    System.out.println("Not found: breakpoint " + bnum);

            } catch (NumberFormatException exc) {
                System.out.println("condition number {condition}");
                return;
            }
        } else {
            System.out.println("condition number .. {condition}");
        }                
    } 

    void commandTemporary(StringTokenizer t, boolean value) {

        if (!t.hasMoreTokens()) {
            Env.specList.temporaryAll(value);
            listBreakpoints();
            return;
        }
        
        String breakpointNum = t.nextToken();

        if (Env.specList.eventRequestSpecsID().size() <=0 ) {
            System.out.println("Breakpoints is empty. ");
            return;
        }

        if (breakpointNum.contains("-")) {
            String[] s = breakpointNum.split("-");

            if (s == null) {
                System.out.println("temporary number [num-num]");
                return;
            }

            try {
                EventRequestSpecList.Property p = getLastBreakPoint();
                int max = p.id;
                int start = 0 ;
                int end = max;

                if (breakpointNum.startsWith("-")) {
                    end = Integer.parseInt(s[0]);
                }

                if (breakpointNum.endsWith("-")) {
                    start = Integer.parseInt(s[0]); 
                }

                if (!breakpointNum.startsWith("-") && !breakpointNum.endsWith("-")) {
                    if (s[0] != null && s[0].length() > 0)
                        start = Integer.parseInt(s[0]);

                    if (s[1] != null && s[1].length() > 0)
                        end = Integer.parseInt(s[1]);
                }

                if (start > end) {
                    System.out.println("temporary number [num1-num2]:  num1 > num2");
                    return;
                }

                end = Math.min(end, max);
                if (start < 0) start = 0;
                if (Env.specList.temporary(start, end, value)) {
                    listBreakpoints();
                    return;
                }        
            } catch (NumberFormatException exc) {
                System.out.println("temporary number [num-num]");
                return;
            }
        } else if (t.countTokens() >= 1) {
            try {
                int restToken = t.countTokens();
                int[] ids = new int[restToken + 1];
                ids[0] = Integer.parseInt(breakpointNum);

                for (int i = 1;i < restToken + 1; i++) {
                    String strNum = t.nextToken();
                    int bnum = Integer.parseInt(strNum);
                    ids[i] = bnum;
                }

                if (Env.specList.temporary(ids, value)) {
                    listBreakpoints();
                    return;
                }

            } catch( NumberFormatException exc) {
                System.out.println("temporary number number ..");
                return;
            }
        } 
        
        if (breakpointNum.equals("!!")) {
            if (Env.specList.eventRequestSpecsID().size() == 0) {
                System.out.println("The breakpoint list  is empty.");
                return ;
            }
            
            EventRequestSpecList.Property p = getLastBreakPoint();
            if (p == null) {
                System.out.println("The breakpoint list  is empty.");
                return;
            }             
            breakpointNum = Integer.toString(p.id); 
        }

        if (Character.isDigit(breakpointNum.charAt(0))) {
            try {
                int bnum = Integer.parseInt(breakpointNum);
                Iterator iter = Env.specList.eventRequestSpecsID().iterator();
                boolean bTemporary = false;
                int index = 0;
                while (iter.hasNext()) {
                    EventRequestSpecList.Property p = (EventRequestSpecList.Property)iter.next(); 
                    if (p.id == bnum) {
                        bTemporary = true;
                        p.temporary = value;
                        break;
                    }
                    index++;
                }

                if (bTemporary) {
                    EventRequestSpec spec = (EventRequestSpec)Env.specList.eventRequestSpecs().get(index);
                    System.out.println("temporary (" + value + "): ["+bnum+"] " + Env.specList.eventRequestSpecs().get(index).toString());
                } else
                    System.out.println("Not found: breakpoint "+bnum);

            } catch (NumberFormatException exc) {
                System.out.println("temporary number");
                return;
            }
        } else {
            System.out.println("temporary number");
        }                
    }   
 
    void commandDisable(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            Env.specList.disableAll();
            listBreakpoints();
            return;
        }
        String breakpointNum = t.nextToken();

        if (Env.specList.eventRequestSpecsID().size() <= 0 ) {
            System.out.println("Breakpoints is empty. ");
            return;
        }
 
        if (breakpointNum.contains("-")) {
            String[] s = breakpointNum.split("-");

            if (s == null) {
                System.out.println("disable number [num-num]");
                return;
            }

            try {  
                EventRequestSpecList.Property p = getLastBreakPoint();
                int max = p.id;
                int start = 0 ;
                int end = max;

                if (breakpointNum.startsWith("-")) {
                    end = Integer.parseInt(s[0]);
                }

                if (breakpointNum.endsWith("-")) {
                    start = Integer.parseInt(s[0]); 
                }

                if (!breakpointNum.startsWith("-") && !breakpointNum.endsWith("-")) {
                    if (s[0] != null && s[0].length() > 0)
                        start = Integer.parseInt(s[0]);

                    if (s[1] != null && s[1].length() > 0)
                        end = Integer.parseInt(s[1]);
                }


                if (start > end) {
                    System.out.println("disable number [num1-num2]:  num1 > num2");
                    return;
                }

                end = Math.min(end, max);
                if (start < 0) start = 0;
                if (Env.specList.disable(start, end)) {
                    listBreakpoints();
                    return;
                }

            } catch (NumberFormatException exc) {
                System.out.println("disable number [num-num]");
                return;
            }

        } else if (t.countTokens() >= 1) {
            try {
                int restToken = t.countTokens();
                int[] ids = new int[restToken + 1];
                ids[0] = Integer.parseInt(breakpointNum);

                for (int i = 1;i < restToken + 1;i++) {
                    String strNum = t.nextToken();
                    int bnum = Integer.parseInt(strNum);
                    ids[i] = bnum;
                }

                if (Env.specList.disable(ids)) {
                    listBreakpoints();
                    return;
                }
            } catch( NumberFormatException exc) {
                System.out.println("disable number number ..");
                return;
            }
        } 

        if (breakpointNum.equals("!!")) {
            if (Env.specList.eventRequestSpecsID().size() == 0) {
                System.out.println("The breakpoint list  is empty.");
                return ;
            }


            EventRequestSpecList.Property p = getLastBreakPoint();
            if (p == null) {
                System.out.println("The breakpoint list  is empty.");
                return ;
            } 
            breakpointNum = Integer.toString(p.id); 
        }

        if (Character.isDigit(breakpointNum.charAt(0))) {
            try {
                int bnum = Integer.parseInt(breakpointNum);
                Iterator iter = Env.specList.eventRequestSpecsID().iterator();
                boolean bDisable = false;
                int index = 0;
                while (iter.hasNext()) {
                    EventRequestSpecList.Property p = (EventRequestSpecList.Property)iter.next(); 
                    if (p.id == bnum) {
                        bDisable = true;
                        p.enable = false;
                        break;
                    }
                    index++;
                }

                if (bDisable) {
                    EventRequestSpec spec = (EventRequestSpec)Env.specList.eventRequestSpecs().get(index);
                    if (spec.resolved() != null) {
                        System.out.println("Disabled: [" + bnum + "] " + Env.specList.eventRequestSpecs().get(index).toString());
                        spec.resolved().disable();
                    } else {
                        System.out.println("Disabled: [" + bnum + "] " + Env.specList.eventRequestSpecs().get(index).toString());
                    } 
                } else
                    System.out.println("Not found: breakpoint " + bnum);

            } catch (NumberFormatException exc) {
                System.out.println("disable number");
                return;
            }
        } else {
            System.out.println("disable number");
        }                
    }

    void commandEnable(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            Env.specList.enableAll();
            listBreakpoints();
            return;
        }
        
        String breakpointNum = t.nextToken();

        if (Env.specList.eventRequestSpecsID().size() <= 0) {
            System.out.println("Breakpoints is empty. ");
            return;
        }
 
        if (breakpointNum.contains("-")) {
            String[] s = breakpointNum.split("-");

            if (s == null) {
                System.out.println("enable number [num-num]");
                return;
            }

            try { 
                EventRequestSpecList.Property p = getLastBreakPoint();
                int max = p.id;
                int start = 0 ;
                int end = max;

                if (breakpointNum.startsWith("-")) {
                    end = Integer.parseInt(s[0]);
                }

                if (breakpointNum.endsWith("-")) {
                    start = Integer.parseInt(s[0]);
                }

                if (!breakpointNum.startsWith("-") && !breakpointNum.endsWith("-")) {
                    if (s[0] != null && s[0].length() > 0)
                        start = Integer.parseInt(s[0]);

                    if (s[1] != null && s[1].length() > 0)
                        end = Integer.parseInt(s[1]);
                }

                if (start > end) {
                    System.out.println("enable number [num1-num2]:  num1 > num2");
                    return;
                }

                end = Math.min(end, max);
                if (start < 0) start = 0;
                if (Env.specList.enable(start, end)) {
                    listBreakpoints();
                    return;
                }

            } catch (NumberFormatException exc) {
                System.out.println("enable number [num-num]");
                return;
            }

        }else if (t.countTokens() >= 1) {
            try {
                int restToken = t.countTokens();
                int[] ids = new int[restToken + 1];
                ids[0] = Integer.parseInt(breakpointNum);

                for( int i = 1;i < restToken + 1; i++) {
                    String strNum = t.nextToken();
                    int bnum = Integer.parseInt(strNum);
                    ids[i] = bnum;
                }

                if (Env.specList.enable(ids)) {
                    listBreakpoints();
                    return;
                }

            } catch( NumberFormatException exc) {
                System.out.println("enable number number ..");
                return;
            }
        }         
        
        if (breakpointNum.equals("!!")) {
            if (Env.specList.eventRequestSpecsID().size() == 0) {
                System.out.println("The breakpoint list  is empty.");
                return ;
            }

            EventRequestSpecList.Property p = getLastBreakPoint();
            if (p == null) {
                System.out.println("The breakpoint list  is empty.");
                return ;
            }
 
            breakpointNum = Integer.toString(p.id); 
        }

        if (Character.isDigit(breakpointNum.charAt(0))) {
            try {
                int bnum = Integer.parseInt(breakpointNum);
                Iterator iter = Env.specList.eventRequestSpecsID().iterator();
                boolean bEnable= false;
                int index = 0;
                
                while (iter.hasNext()) {
                    //int no = (Integer)iter.next();
                    EventRequestSpecList.Property p = (EventRequestSpecList.Property)iter.next();
                    //int no = p.id;
                    if (p.id == bnum) {
                        bEnable = true;
                        p.enable = true;
                        break;
                    }
                    index++;
                }

                if (bEnable) {
                    EventRequestSpec spec = (EventRequestSpec)Env.specList.eventRequestSpecs().get(index);
                    if (spec.resolved() != null) {
                        System.out.println("Enabled: [" + bnum + "] " + Env.specList.eventRequestSpecs().get(index).toString());
                        spec.resolved().enable();
                    } else {
                        System.out.println("Not resolved: " + Env.specList.eventRequestSpecs().get(index).toString());
                    } 
                } else
                    System.out.println("Not found: breakpoint "+bnum);

            } catch (NumberFormatException exc) {
                System.out.println("enable number");
                return;
            }
        } else {
            System.out.println("enable number");  
        }                
    }
    
    void commandCmd(StringTokenizer t) {
        if (!t.hasMoreTokens())
            return;

        String cmdLine = "";
        String token = t.nextToken();
        EventRequestSpecList.Property p = null;
        if (Character.isDigit(token.charAt(0))) {
            try {
                int id = Integer.parseInt(token);
                p = (EventRequestSpecList.Property)Env.specList.eventRequestSpecsMap().get(id);
                if (p == null){
                    System.out.println("id number is invalid. ");
                    return;
                }	                
            } catch (NumberFormatException exc) {
                System.out.println("command (number) cmd line ");
                return;
            }

            if (t.hasMoreTokens())
                cmdLine = t.nextToken();

        }else if (token.equals("!!")) {
            p = getLastBreakPoint();
            if (p == null) {
                System.out.println("EventRequestSpecList.Property is null ");
                return;
            }

            if (t.hasMoreTokens())
                cmdLine = t.nextToken();
        } else {// for previous version : command cmd = > last breakpoint
            p = getLastBreakPoint();
            if (p == null) {
                System.out.println("EventRequestSpecList.Property is null ");
                return;
            }

            cmdLine = token;
        }

        while(t.hasMoreTokens())
           cmdLine = cmdLine + " " + t.nextToken();

        if (cmdLine != null && cmdLine.length() > 0) {
            if (p.cmdList == null)
                p.cmdList = new ArrayList();

            p.cmdList.add(cmdLine);   
            System.out.println("commands: "+cmdLine);
        } else {
            String ans = "n";
            boolean clearAll = true;
            BufferedReader br =
                 new BufferedReader(new InputStreamReader(System.in));

            System.out.print("Are you sure to delete all commands list (y/n)?  ");
            String strLine = null;

            try {
                ans = br.readLine();
            } catch (Exception e) {
                System.out.println("Error while reading line from console : " + e);               
                clearAll = false;
            }

            if (ans.toLowerCase().equals("n")) {
                clearAll = false;                
            }

            if (clearAll) {
                p.cmdList.clear();
                p.cmdList = null;
            }
            
            if (br != null) {
                try {
                    br.close();
                } catch (Exception exc) {
                    // do nothing
                }
            }            
            System.out.println("clear command list sucessfully!!");

        }   
    }


    static EventRequestSpecList.Property getLastBreakPoint() {
        EventRequestSpecList.Property p = null;

        for (int i = Env.specList.eventRequestSpecs().size() - 1; i >= 0; i--) {
            EventRequestSpec spec = (EventRequestSpec)Env.specList.eventRequestSpecs().get(i);
            if (spec instanceof BreakpointSpec || spec instanceof WatchpointSpec) {
                p = (EventRequestSpecList.Property)Env.specList.eventRequestSpecsID().get(i);
                break;
            }
        }

        return p;
    }
    
    void commandListCmd(StringTokenizer t) {
        String s = null;
    
        if (t.hasMoreTokens())
            s = t.nextToken(); 
        else {
            System.out.println("listcommand num");
            return;
        }            

        EventRequestSpecList.Property p = null;
        int index = 0;
        if (s != null && Character.isDigit(s.charAt(0))) {
            try {
                int id = Integer.parseInt(s);
                p = (EventRequestSpecList.Property)Env.specList.eventRequestSpecsMap().get(id);
                if (p == null) {
                    System.out.println("id number is invalid. ");
                    return;
                }
                for (int i = 0 ; i < Env.specList.eventRequestSpecsID().size(); i++) {
                    EventRequestSpecList.Property k = (EventRequestSpecList.Property)Env.specList.eventRequestSpecsID().get(i);
                    if (id == k.id) {
                        index = i;
                        break;
                    }
                }
                
            } catch (NumberFormatException exc) {
                System.out.println("listcommand (number) ");
                return;
            }
 
        } else if (s!=null && s.equals("!!")) {


            p = getLastBreakPoint();
            if (p == null) {
                System.out.println("EventRequestSpecList.Property is null ");
                return;
            } 
        }

        EventRequestSpec spec = (EventRequestSpec)Env.specList.eventRequestSpecs().get(index);
        System.out.println(" <" + spec.toString() + ">");
        if (p.cmdList == null) {
            System.out.println("command is empty.");
        } else {    
            Iterator it = p.cmdList.iterator();
            while (it.hasNext()) {
                String c = (String)it.next();

                if ((c.startsWith("[script]") || c.startsWith("[ascript]")) && p.scriptObj != null) {

                    String bstring = p.scriptObj.toString("onBreakPoint");
                    if (bstring != null) {
                        System.out.println("[script]");
                        System.out.println(bstring);
                    } 	
				
                } else
                    System.out.println(c);
            }
        }	 
    }
 

    private List parseWatchpointSpec(StringTokenizer t) {
        List list = new ArrayList();
        boolean access = false;
        boolean modification = false;
        int suspendPolicy = EventRequest.SUSPEND_ALL;

        String fieldName = t.nextToken();
        if (fieldName.equals("go")) {
            suspendPolicy = EventRequest.SUSPEND_NONE;
            fieldName = t.nextToken();
        } else if (fieldName.equals("thread")) {
            suspendPolicy = EventRequest.SUSPEND_EVENT_THREAD;
            fieldName = t.nextToken();
        }
        if (fieldName.equals("access")) {
            access = true;
            fieldName = t.nextToken();
        } else if (fieldName.equals("all")) {
            access = true;
            modification = true;
            fieldName = t.nextToken();
        } else {
            modification = true;
        }
        int dot = fieldName.lastIndexOf('.');
        if (dot < 0) {
            MessageOutput.println("Class containing field must be specified.");
            return list;
        }
        String className = fieldName.substring(0, dot);
        fieldName = fieldName.substring(dot+1);

        try {
            EventRequestSpec spec;
            if (access) {
                spec = Env.specList.createAccessWatchpoint(className, 
                                                           fieldName);
                spec.suspendPolicy = suspendPolicy;
                list.add(spec);
            }
            if (modification) {
                spec = Env.specList.createModificationWatchpoint(className, 
                                                                 fieldName);
                spec.suspendPolicy = suspendPolicy;
                list.add(spec);
            }
        } catch (MalformedMemberNameException exc) {
            MessageOutput.println("is not a valid field name", fieldName);
        } catch (ClassNotFoundException exc) {
            MessageOutput.println("is not a valid class name", className);
        }
        return list;
    }

     boolean commandWatch(StringTokenizer t, boolean temporary) { 
        if (!t.hasMoreTokens()) {
            MessageOutput.println("Field to watch not specified");
             return false; 
        }
  
        String fieldName = t.nextToken();
        String prefixString = "";

        if (fieldName.equals("go")) {
            prefixString = "go";
            fieldName = t.nextToken();
        } else if (fieldName.equals("thread")) {
            prefixString = "thread";
            fieldName = t.nextToken();
        }
        
        if (fieldName.equals("access")) {
            if (prefixString.length() > 0)
                prefixString = prefixString + " " + "access";
            else
                prefixString = "access";

            fieldName = t.nextToken();
        } else if (fieldName.equals("all")) {
            if (prefixString.length() > 0)
                prefixString = prefixString + " " + "access";	
            else
                prefixString = "all";
                
            fieldName = t.nextToken();
        } 
 
        String rest="";
        if (t.hasMoreTokens()) {
            rest = t.nextToken();
            while (t.hasMoreTokens())
                rest = rest + " " + t.nextToken();
       }
        
        if (fieldName.startsWith("this")) {
            String classname = tty.getCurrentLocationClassName();
            if (classname!=null) {
                String field = fieldName.substring(4);
                fieldName = classname+field;
            }
        }

        String breakcondition = null;
        
        if (rest.length() > 0) {
            String s = null ;
            Pattern p = Pattern.compile("(.*)\\{(.*)\\}.*"); 
            Matcher m = p.matcher(rest);
            if ( m.find() == true ) {
                s = m.group(1);
                breakcondition = m.group(2).trim();

                if (!BreakCondition.validate(breakcondition)) {
                    System.out.println("{condition}  is not correct!");
                    return false;
                }

            } else {
                breakcondition=null;
                s=rest;
            }
        }
 
        String newstring = fieldName + " " + rest;

        if (prefixString.length() > 0)
            newstring= prefixString + " " + newstring;
 
        t = new StringTokenizer(newstring);
        boolean res = false; 

        Iterator iter = parseWatchpointSpec(t).iterator();
        while (iter.hasNext()) { 
            boolean r = resolveNow((WatchpointSpec)iter.next(), temporary); 
            if (r && breakcondition != null) {
                EventRequestSpecList.Property p = null;
                p = getLastBreakPoint();

                if (p != null) {
                    p.breakcondition = breakcondition;
                    BreakCondition.println(breakcondition);
                } else {
                    System.out.println("condition is invalid!!");
                }
            }            

            if (r)
                res = true; 
        }        
        return res;         
    }

    void commandUnwatch(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("Field to unwatch not specified");
            return;
        }

        Iterator iter = parseWatchpointSpec(t).iterator();
        while (iter.hasNext()) {
            WatchpointSpec spec = (WatchpointSpec)iter.next();
            if (Env.specList.delete(spec)) {
                MessageOutput.println("Removed:", spec.toString());
            } else {
                MessageOutput.println("Not found:", spec.toString());
            }
        }
    }

    void turnOnExitTrace(ThreadInfo threadInfo, int suspendPolicy) {
        EventRequestManager erm = Env.vm().eventRequestManager();
        MethodExitRequest exit = erm.createMethodExitRequest();
        if (threadInfo != null) {
            exit.addThreadFilter(threadInfo.getThread());
        }
        Env.addExcludes(exit);
        exit.setSuspendPolicy(suspendPolicy);
        exit.enable();

    }

    static String methodTraceCommand = null;

    void commandTrace(StringTokenizer t) {
        String modif;
        int suspendPolicy = EventRequest.SUSPEND_ALL;
        ThreadInfo threadInfo = null;
        String goStr = " ";

        /*
         * trace [go] methods [thread]
         * trace [go] method exit | exits [thread]
         */
        if (t.hasMoreTokens()) {
            modif = t.nextToken();
            if (modif.equals("go")) {
                suspendPolicy = EventRequest.SUSPEND_NONE;
                goStr = " go ";
                if (t.hasMoreTokens()) {
                    modif = t.nextToken();
                }
            } else if (modif.equals("thread")) {
                // this is undocumented as it doesn't work right.
                suspendPolicy = EventRequest.SUSPEND_EVENT_THREAD;
                if (t.hasMoreTokens()) {
                    modif = t.nextToken();
                }
            }
                
            if  (modif.equals("method")) {
                String traceCmd = null;
        
                if (t.hasMoreTokens()) {
                    String modif1 = t.nextToken();
                    if (modif1.equals("exits") || modif1.equals("exit")) {
                        if (t.hasMoreTokens()) {
                            threadInfo = doGetThread(t.nextToken());
                        }
                        if (modif1.equals("exit")) {
                            StackFrame frame;
                            try {
                                frame = ThreadInfo.getCurrentThreadInfo().getCurrentFrame();
                            } catch (IncompatibleThreadStateException ee) {
                                MessageOutput.println("Current thread isnt suspended.");
                                return;
                            }
                            Env.setAtExitMethod(frame.location().method());
                            traceCmd = MessageOutput.format("trace" + 
                                                    goStr + "method exit " +
                                                    "in effect for",
                                                    Env.atExitMethod().toString());
                        } else {
                            traceCmd = MessageOutput.format("trace" + 
                                                   goStr + "method exits " +
                                                   "in effect");
                        }
                        commandUntrace(new StringTokenizer("methods"));
                        turnOnExitTrace(threadInfo, suspendPolicy);
                        methodTraceCommand = traceCmd;
                        return;
                    }
                } else {
                   MessageOutput.println("Can only trace");
                   return;
                }
            }
            if (modif.equals("methods")) {
                // Turn on method entry trace
                MethodEntryRequest entry;
                EventRequestManager erm = Env.vm().eventRequestManager();
                if (t.hasMoreTokens()) {
                    threadInfo = doGetThread(t.nextToken());
                }
                if (threadInfo != null) {
                    /*
                     * To keep things simple we want each 'trace' to cancel
                     * previous traces.  However in this case, we don't do that
                     * to preserve backward compatibility with pre JDK 6.0.
                     * IE, you can currently do 
                     *   trace   methods 0x21
                     *   trace   methods 0x22
                     * and you will get xxx traced just on those two threads
                     * But this feature is kind of broken because if you then do
                     *   untrace  0x21
                     * it turns off both traces instead of just the one.
                     * Another bogosity is that if you do
                     *   trace methods
                     *   trace methods
                     * and you will get two traces.
                     */

                    entry = erm.createMethodEntryRequest();
                    entry.addThreadFilter(threadInfo.getThread());
                } else {
                    commandUntrace(new StringTokenizer("methods"));
                    entry = erm.createMethodEntryRequest();
                }                        
                Env.addExcludes(entry);
                entry.setSuspendPolicy(suspendPolicy);
                entry.enable();
                turnOnExitTrace(threadInfo, suspendPolicy);
                methodTraceCommand = MessageOutput.format("trace" + goStr + 
                                                          "methods in effect");

                return;
            }

            MessageOutput.println("Can only trace");
            return;
        } 

        // trace all by itself.
        if (methodTraceCommand != null) {
            MessageOutput.printDirectln(methodTraceCommand);
        }
        
        // More trace lines can be added here.
    }

    void commandUntrace(StringTokenizer t) {
        // untrace
        // untrace methods

        String modif = null;
        EventRequestManager erm = Env.vm().eventRequestManager();
        if (t.hasMoreTokens()) {
            modif = t.nextToken();
        }
        if (modif == null || modif.equals("methods")) {
            erm.deleteEventRequests(erm.methodEntryRequests());
            erm.deleteEventRequests(erm.methodExitRequests());
            Env.setAtExitMethod(null);
            methodTraceCommand = null;
        }
    }
    
    void commandList(StringTokenizer t) {
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

        String sourceFileName = null;
        try {
            sourceFileName = loc.sourceName();

            ReferenceType refType = loc.declaringType();
            int lineno = loc.lineNumber();
            int currentlineno = lineno; 
    
            if (t.hasMoreTokens()) {
                String id = t.nextToken();    
 
                try {
                    NumberFormat nf = NumberFormat.getNumberInstance();
                    nf.setParseIntegerOnly(true);
                    Number n = nf.parse(id);
                    lineno = n.intValue();
                } catch (java.text.ParseException jtpe) { 
                        List meths = refType.methodsByName(id);
                        if (meths == null || meths.size() == 0) {
                            MessageOutput.println("is not a valid line number or method name for",
                                                  new Object [] {id, refType.name()});
                            return;
                        } else if (meths.size() > 1) {
                            MessageOutput.println("is an ambiguous method name in",
                                                  new Object [] {id, refType.name()});
                            return;
                        }
                        loc = ((Method)meths.get(0)).location();
                        lineno = loc.lineNumber();
                }
            } 
            int startLine = Math.max(lineno - tty.LIST_SIZE/2, 1);
            int endLine = startLine + tty.LIST_SIZE; 
            System.out.println("------------------------------------------------------------------------------");
            tty.printCurrentLocation();
            if (tty.currListLoc < 0) {
                tty.currListLoc = lineno;
                int totalLine = Env.getSourceLineCount(loc); 
                if (totalLine <= tty.LIST_SIZE)
                    tty.currListLoc = totalLine / 2;
                else if (tty.currListLoc < tty.LIST_SIZE / 2)
                    tty.currListLoc  = tty.LIST_SIZE / 2;
                else if (tty.currListLoc  >=  totalLine - tty.LIST_SIZE / 2 )
                    tty.currListLoc = totalLine - tty.LIST_SIZE/2;
            }
            
            if (lineno < 0) {
                MessageOutput.println("Line number information not available for");
            } else if (Env.sourceLine(loc, lineno) == null) {
                MessageOutput.println("is an invalid line number for",
                                      new Object [] {new Integer (lineno),
                                                     refType.name()});
            } else {
                for (int i = startLine; i <= endLine; i++) {
                    String sourceLine = Env.sourceLine(loc, i);
                    if (sourceLine == null) {
                        break;
                    }
 
                    if (i == currentlineno){ 
                        MessageOutput.println("source line number current line and line",
                                              new Object [] {new Integer (i),
                                                             sourceLine});
                    } else {
                        MessageOutput.println("source line number and line",
                                              new Object [] {new Integer (i),
                                                             sourceLine});
                    }
                } 
                System.out.println("------------------------------------------------------------------------------");  
            }
        } catch (AbsentInformationException e) {
            MessageOutput.println("No source information available for:", loc.toString());
        } catch(FileNotFoundException exc) {
            MessageOutput.println("Source file not found:", sourceFileName);
        } catch(IOException exc) {
            MessageOutput.println("I/O exception occurred:", exc.toString());
        } 
    }

    void commandLines(StringTokenizer t) { // Undocumented command: useful for testing
        if (!t.hasMoreTokens()) {
            MessageOutput.println("Specify class and method");
        } else {
            String idClass = t.nextToken();
            String idMethod = t.hasMoreTokens() ? t.nextToken() : null;
            try {
                ReferenceType refType = Env.getReferenceTypeFromToken(idClass);
                if (refType != null) {
                    List lines = null;
                    if (idMethod == null) {
                        lines = refType.allLineLocations();
                    } else {
                        List methods = refType.allMethods();
                        Iterator iter = methods.iterator();
                        while (iter.hasNext()) {
                            Method method = (Method)iter.next();
                            if (method.name().equals(idMethod)) {
                                lines = method.allLineLocations();
                            }
                        }
                        if (lines == null) {
                            MessageOutput.println("is not a valid method name", idMethod);
                        }
                    }
                    Iterator iter = lines.iterator();
                    while (iter.hasNext()) {
                        Location line = (Location)iter.next();
                        MessageOutput.printDirectln(line.toString());// Special case: use printDirectln() 
                        int offset= line.toString().lastIndexOf(":");	
                        String classid= line.toString().substring(0,offset);
                        System.out.println("classid ="+ classid +", num = "+line.lineNumber());  
                    }
                } else {
                    MessageOutput.println("is not a valid id or class name", idClass);
                }
            } catch (AbsentInformationException e) {
                MessageOutput.println("Line number information not available for", idClass);
            }
        }
    }

    void commandClasspath(StringTokenizer t) {
        if (Env.vm() instanceof PathSearchingVirtualMachine) {
            PathSearchingVirtualMachine vm = (PathSearchingVirtualMachine)Env.vm();
            MessageOutput.println("base directory:", vm.baseDirectory());
            MessageOutput.println("classpath:", vm.classPath().toString());
            MessageOutput.println("bootclasspath:", vm.bootClassPath().toString());
        } else {
            MessageOutput.println("The VM does not use paths");
        }
    }

    /* Get or set the source file path list. */
     void commandUse(StringTokenizer t, boolean append) { 
        if (!t.hasMoreTokens()) {
            MessageOutput.printDirectln(Env.getSourcePath());// Special case: use printDirectln()
        } else {
            /*
             * Take the remainder of the command line, minus
             * leading or trailing whitespace.  Embedded
             * whitespace is fine.
             */ 
            String token = t.nextToken("");
            File f = new File(token.trim());
            BufferedReader inFile = null;
            String paths = ""; 
            if (f.exists() && !f.isDirectory()) {
                if (f.canRead()) {
                    try {
                        inFile = new BufferedReader(new FileReader(f));
                        String ln;
                        while ((ln = inFile.readLine()) != null) {
                            paths += ln;
                        }     

                        if (append) {
                            if (Env.getSourcePath().length() > 0 && paths.length() > 0)
                                Env.setSourcePath(Env.getSourcePath()+File.pathSeparator+paths);
                            else
                                Env.setSourcePath(paths);
                        } else
                            Env.setSourcePath(paths);
                    } catch (Exception e) {
                        // do nothing
                    } finally {
                        if (inFile != null) {
                            try {
                                inFile.close();
                            } catch (Exception exc) {
                                // do nothing
                            }
                        }                          
                    }
                }
            } else {             
                token= token.trim();
                if (append) {
                    if (Env.getSourcePath().length() > 0 && token.length() > 0)
                        Env.setSourcePath(Env.getSourcePath()+File.pathSeparator+token);
                    else
                        Env.setSourcePath(token);
                } else
                    Env.setSourcePath(token);
            }              
        }
    }

    /* Print a stack variable */
    private void printVar(LocalVariable var, Value value) {
        MessageOutput.println("expr is value",
                              new Object [] {var.name(), 
                                             value == null ? "null" : value.toString()});
    }

    /* Print all local variables in current stack frame. */
    void commandLocals() {
        StackFrame frame;
        ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
        if (threadInfo == null) {
            MessageOutput.println("No default thread specified:");
            return;
        }
        try {
            frame = threadInfo.getCurrentFrame();
            if (frame == null) {
                throw new AbsentInformationException();
            }
            List vars = frame.visibleVariables();
    
            if (vars.size() == 0) {
                MessageOutput.println("No local variables");
                return;
            }
            Map values = frame.getValues(vars);

            MessageOutput.println("Method arguments:");
            for (Iterator it = vars.iterator(); it.hasNext(); ) {
                LocalVariable var = (LocalVariable)it.next();
                if (var.isArgument()) {
                    Value val = (Value)values.get(var);
                    printVar(var, val);
                }
            }
            MessageOutput.println("Local variables:");
            for (Iterator it = vars.iterator(); it.hasNext(); ) {
                LocalVariable var = (LocalVariable)it.next();
                if (!var.isArgument()) {
                    Value val = (Value)values.get(var);
                    printVar(var, val);
                }
            }
        } catch (AbsentInformationException aie) {
            MessageOutput.println("Local variable information not available.");
        } catch (IncompatibleThreadStateException exc) {
            MessageOutput.println("Current thread isnt suspended.");
        }
    }

    int dumpMod = 0;  
    private void dump(ObjectReference obj, ReferenceType refType,
                      ReferenceType refTypeBase) {
        for (Iterator it = refType.fields().iterator(); it.hasNext(); ) {
            StringBuffer o = new StringBuffer();
            Field field = (Field)it.next();
            o.append("    ");
            if (!refType.equals(refTypeBase)) {
                o.append(refType.name());
                o.append(".");
            }
            o.append(field.name());
            o.append(MessageOutput.format("colon space"));
            o.append(obj.getValue(field));
            MessageOutput.printDirectln(o.toString()); // Special case: use printDirectln()
        }
        if (refType instanceof ClassType) {
            ClassType sup = ((ClassType)refType).superclass();
            if (sup != null) {
                dump(obj, sup, refTypeBase);
            }
        } else if (refType instanceof InterfaceType) {
            List sups = ((InterfaceType)refType).superinterfaces();
            for (Iterator it = sups.iterator(); it.hasNext(); ) {
                dump(obj, (ReferenceType)it.next(), refTypeBase);
            }
        } else {
            /* else refType is an instanceof ArrayType */
            if (obj instanceof ArrayReference) {
                for (Iterator it = ((ArrayReference)obj).getValues().iterator();
                     it.hasNext(); ) { 
                    String s = it.next().toString();

                    if (dumpMod == 0)
                        MessageOutput.printDirect(s);
                    else if (dumpMod == 3) {
                        int value = Integer.parseInt(s);
                        char c = (char)((value&0x00FF));
                        MessageOutput.printDirect(Character.toString(c));
                    } else if (dumpMod == 2) {
                        int value = Integer.parseInt(s);
                        MessageOutput.printDirect(Integer.toHexString(value));
                    } else  if (dumpMod == 1) {
                        if (it.hasNext()) {
                            int b1 = Integer.parseInt(s); 
                            int b2 = Integer.parseInt(it.next().toString());
                            char c = (char)(((b1 & 0x00FF) << 8) + (b2 & 0x00FF)); 
                            MessageOutput.printDirect(Character.toString(c));
                        }
                    }
                    
                    if (it.hasNext() && (dumpMod == 0 ||dumpMod == 2)) {                    
                        MessageOutput.printDirect(", ");// Special case: use printDirect()
                    } 
                }
                MessageOutput.println();
            }
        }
    } 
    private void dump(ObjectReference obj, ReferenceType refType,
                      ReferenceType refTypeBase, String m) {
        for (Iterator it = refType.fields().iterator(); it.hasNext(); ) {
            StringBuffer o = new StringBuffer();
            Field field = (Field)it.next();
            o.append("    ");
            if (!refType.equals(refTypeBase)) {
                o.append(refType.name());
                o.append(".");
            }
            o.append(field.name());
            o.append(MessageOutput.format("colon space"));
            o.append(obj.getValue(field));
            if (field.name().contains(m))
                MessageOutput.printDirectln(o.toString()); // Special case: use printDirectln()
        }
        if (refType instanceof ClassType) {
            ClassType sup = ((ClassType)refType).superclass();
            if (sup != null) {
                dump(obj, sup, refTypeBase, m);
            }
        } else if (refType instanceof InterfaceType) {
            List sups = ((InterfaceType)refType).superinterfaces();
            for (Iterator it = sups.iterator(); it.hasNext(); ) {
                dump(obj, (ReferenceType)it.next(), refTypeBase, m);
            }
        } else {
            /* else refType is an instanceof ArrayType */
            if (obj instanceof ArrayReference) {
                for (Iterator it = ((ArrayReference)obj).getValues().iterator();
                                    it.hasNext(); ) { 
                    String s = it.next().toString();

                    if (dumpMod == 0)
                        MessageOutput.printDirect(s);
                    else if (dumpMod == 3) {
                        int value = Integer.parseInt(s);
                        char c = (char)((value & 0x00FF));
                        MessageOutput.printDirect(Character.toString(c));
                    } else if (dumpMod == 2) {
                        int value = Integer.parseInt(s);
                        MessageOutput.printDirect(Integer.toHexString(value));
                    } else if (dumpMod == 1) {
                        if (it.hasNext()) {
                            int b1 = Integer.parseInt(s); 
                            int b2 = Integer.parseInt(it.next().toString());
                            char c = (char)(((b1 & 0x00FF) << 8) + (b2 & 0x00FF)); 
                            MessageOutput.printDirect(Character.toString(c));
                        }
                    }
                    
                    if (it.hasNext() &&  (dumpMod == 0 ||dumpMod == 2)) {
                        MessageOutput.printDirect(", ");// Special case: use printDirect()
                    }
                }
                MessageOutput.println();
            }
        }
    }    

   void doPrint(StringTokenizer t, boolean dumpObject) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No objects specified.");
            return;
        }
        
        String token = t.nextToken();   
        dumpMod = 0;
        if (token.length() >= 2 &&  token.startsWith("/")) {
            if (token.substring(1,2).equals("s"))
                dumpMod = 1;
            else if (token.substring(1,2).equals("x"))
                dumpMod = 2;
            else if (token.substring(1,2).equals("a"))
                dumpMod = 3;

            if (!t.hasMoreTokens()) {
                MessageOutput.println("No objects specified.");
                return;
            }
            token =  t.nextToken();
        }          
        
        String expr = token;
        Value val = evaluate(expr);
        
        if (dumpObject == false && val == null) {
            int offset= expr.lastIndexOf(".");
            String classId = null;
            String fieldname = null;
            if (offset != -1) {
                classId= expr.substring(0,offset);
                fieldname = expr.substring(offset + 1, expr.length());
                Value val2 = null;
                if (classId != null)
                    val2 = evaluate(classId);

                if ((val2 instanceof ObjectReference) && !(val2 instanceof StringReference) && fieldname != null) {
                    ObjectReference obj = (ObjectReference)val2;
                    ReferenceType refType = obj.referenceType();
                    String out = null;

                    for (Iterator it = refType.fields().iterator(); it.hasNext();) {
                        Field field = (Field)it.next();
                        if (fieldname.equals(field.name())) {
                            out = expr + " = " + obj.getValue(field);
                            break;
                        }
                    }       

                    if (out != null) {
                        System.out.println(out);
                        return;
                    }
                }
            } else {
                try {
                    final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
                    StackFrame frame = threadInfo.getCurrentFrame();

                    if (frame != null) {
                        Location loc = frame.location();
                        Method meth = loc.method();
                        classId = meth.declaringType().name();
 
                        int ofst = classId.indexOf("$");
                        if (ofst > 0)
                            classId = classId.substring(0,ofst);
 
                        String newExpr = classId + "." + expr;
                        String oldExpr = expr;
                        expr = newExpr;
                        val = evaluate(expr);
                        if (val == null)
                            expr = oldExpr;
                    }
                } catch (Exception ex) {
                    // do nothing
                }
            }

        }                
  
        if (val == null) {
            try {
                final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
                StackFrame frame = threadInfo.getCurrentFrame();
                if (frame != null) {
                    Location loc = frame.location();
                    if (loc.declaringType().name() != null) {
                        String anonyClass = loc.declaringType().name();
                        int count = anonyClass.length() - anonyClass.replaceAll("\\$", "").length();  
                        int offset = anonyClass.lastIndexOf("$");
                        count--;                            
                        String aap = "this$"+count;

                        while (offset > 0) {
                            String pclass = anonyClass.substring(0,offset);

                            if (pclass != null && pclass.length() > 0) {
                                ReferenceType cls = Env.getReferenceTypeFromToken(pclass);
                                if (cls != null) {          
                                    val = evaluate(aap + "." + expr);
                                    if (val != null)
                                        break;
                                    count--;
                                    aap = aap + "." + "this$" + count;
                                }

                            }else
                                break;

                            anonyClass = pclass;
                            offset = anonyClass.lastIndexOf("$");
                        }
                    }
                }
            } catch (Exception ex) {
                // do nothing
            }  
        }            
             
        if (val == null) {
            MessageOutput.println("expr is null", expr.toString());
        } else if (dumpObject && (val instanceof ObjectReference) &&
                        !(val instanceof StringReference)) {
            ObjectReference obj = (ObjectReference)val;
            ReferenceType refType = obj.referenceType();
            MessageOutput.println("expr is value",
                                    new Object [] {expr.toString(),
                                            MessageOutput.format("grouping begin character")}); 
            if (t.hasMoreTokens()) {            
                dump(obj, refType, refType, t.nextToken());
            } else
                dump(obj, refType, refType);                

            MessageOutput.println("grouping end character");
        } else {
            String strVal = getStringValue();
            if (strVal != null) {
                MessageOutput.println("expr is value", new Object [] {expr.toString(),
                                                                      strVal});
            } 
        }        
    }
 
    boolean exprValue(String expr) throws Exception {
        Value val = evaluateBreakCondition(expr);       
      
        if (val == null) {
            int offset= expr.lastIndexOf(".");
            String classId = null;
            String fieldname = null;
            if (offset != -1) {
                classId= expr.substring(0,offset);
                fieldname = expr.substring(offset+1, expr.length());

                Value val2 = null;
                if (classId != null)
                    val2 = evaluateBreakCondition(expr);

                if ((val2 instanceof ObjectReference) && !(val2 instanceof StringReference) && fieldname != null) {
                    ObjectReference obj = (ObjectReference)val2;
                    ReferenceType refType = obj.referenceType();
                    String out = null;

                    for (Iterator it = refType.fields().iterator(); it.hasNext();) {
                        Field field = (Field)it.next();
                        if (fieldname.equals(field.name())) { 
                            out = obj.getValue(field).toString();
                            break;
                        }
                    }       

                    if (out != null) { 
                        return Boolean.parseBoolean(out);
                    }
                }
            } else {
                try {
                    final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
                    StackFrame frame = threadInfo.getCurrentFrame();
                    if (frame != null) {
                        Location loc = frame.location();
                        Method meth = loc.method();
                        classId = meth.declaringType().name();
                        String newExpr = classId + "." + expr;                        
                        expr = newExpr;
                        val = evaluate(expr);
                    }
                } catch (Exception ex) {
                    // do nothing
                }
            }
        }             

        if (val == null) { 
            System.out.println("expr is invalid.");
            return true;
        } else {
            String strVal = getStringValue();
            if (strVal != null) {
                return Boolean.parseBoolean(strVal);
            } 
        }
        return true;
    }
 
    void doEPrint(StringTokenizer t, boolean dumpObject) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No objects specified.");
            return;
        }

        while (t.hasMoreTokens()) {
            String expr = t.nextToken(""); 
            Value val = evaluate(expr);
 
            if (dumpObject == false && val == null) {
                int offset= expr.lastIndexOf(".");
                String classId = null;
                String fieldname = null;
                if (offset != -1) {
                    classId = expr.substring(0,offset);
                    fieldname = expr.substring(offset+1, expr.length());

                    Value val2 = null;
                    if (classId != null)
                        val2 = evaluate(classId);

                    if ((val2 instanceof ObjectReference) &&!(val2 instanceof StringReference) && fieldname != null) {
                        ObjectReference obj = (ObjectReference)val2;
                        ReferenceType refType = obj.referenceType();
                        String out = null;

                        for (Iterator it = refType.fields().iterator(); it.hasNext();) {
                            Field field = (Field)it.next();
                            if (fieldname.equals(field.name())) {
                                out = expr + " = " + obj.getValue(field);
                                break;
                            }
                        }       

                        if (out != null) {
                            System.out.println(out);
                            return;
                        }
                    }
                } else {
                    try {
                        final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
                        StackFrame frame = threadInfo.getCurrentFrame();
                        if (frame != null) {
                            Location loc = frame.location();
                            Method meth = loc.method();
                            classId = meth.declaringType().name();                            
 
                            int ofst = classId.indexOf("$");
                            if (ofst > 0)
                                classId = classId.substring(0,ofst); 
                            
                            String newExpr = classId + "." + expr;
                            String oldExpr = expr;
                            expr = newExpr;
                            val = evaluate(expr);
                            if (val == null)
                                expr = oldExpr;
                        }
                    } catch (Exception ex) {
                        // do nothing
                    }
                }
            }             
   
            if (val == null) {
                try {
                    final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
                    StackFrame frame = threadInfo.getCurrentFrame();
                    if (frame != null) {
                        Location loc = frame.location();
                        if (loc.declaringType().name() != null) {
                            String anonyClass = loc.declaringType().name();
                            int count = anonyClass.length() - anonyClass.replaceAll("\\$", "").length();   
                            int offset = anonyClass.lastIndexOf("$");
                            count--;                            
                            String aap = "this$"+count;

                            while (offset > 0) {
                                String pclass = anonyClass.substring(0,offset);

                                if (pclass != null && pclass.length() > 0) {
                                    ReferenceType cls = Env.getReferenceTypeFromToken(pclass);
                                    if (cls != null) {   
                                        val = evaluate(aap + "." + expr);
                                        if (val != null)
                                            break;
                                        count--;
                                        aap = aap + "." + "this$" + count;
                                    }

                                }else
                                    break;

                                anonyClass = pclass;
                                offset = anonyClass.lastIndexOf("$");
                            }
                        }
                    }
                } catch (Exception ex) {
                    // do nothing
                }  
            }          

            if (val == null) {
                MessageOutput.println("expr is null", expr.toString());
            } else if (dumpObject && (val instanceof ObjectReference) &&
                        !(val instanceof StringReference)) {
                ObjectReference obj = (ObjectReference)val;
                ReferenceType refType = obj.referenceType();
                MessageOutput.println("expr is value",
                                        new Object [] {expr.toString(),
                                                    MessageOutput.format("grouping begin character")});
                dump(obj, refType, refType);
                    MessageOutput.println("grouping end character");
            } else {
                String strVal = getStringValue();
                if (strVal != null) { 
                    System.out.println(strVal);
                } 
            }
        }
    }

    String expr(StringTokenizer t, boolean dumpObject) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No objects specified.");
            return "";
        }

        while (t.hasMoreTokens()) {
            String expr = t.nextToken("");
            Value val = evaluate(expr);          
            if (val == null) {
                int offset= expr.lastIndexOf(".");
                String classId = null;
                String fieldname = null;
                if (offset != -1) {
                    classId= expr.substring(0,offset);
                    fieldname = expr.substring(offset+1, expr.length());

                    Value val2 = null;
                    if (classId != null)
                        val2 = evaluate(classId);

                    if ((val2 instanceof ObjectReference) &&!(val2 instanceof StringReference) && fieldname != null) {
                        ObjectReference obj = (ObjectReference)val2;
                        ReferenceType refType = obj.referenceType();
                        String out = null;

                        for (Iterator it = refType.fields().iterator(); it.hasNext();) {
                            Field field = (Field)it.next();
                            if (fieldname.equals(field.name())) { 
                                val = obj.getValue(field);
                                break;
                            }
                        }        
                    }

                } else {
                    try {
                        final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
                        StackFrame frame = threadInfo.getCurrentFrame();
                        if (frame != null) {
                            Location loc = frame.location();
                            Method meth = loc.method();
                            classId = meth.declaringType().name();
                            
                            int ofst = classId.indexOf("$");
                            if (ofst > 0)
                                classId = classId.substring(0,ofst); 
                            
                            String newExpr = classId + "." + expr;
                            String oldExpr = expr;
                            expr = newExpr;
                            val = evaluate(expr);
                            if (val == null)
                                expr = oldExpr;                            
                        }
                    } catch (Exception ex) {
                        // do nothing
                    }
                }
            }  
 
            if (val == null) {
                try {
                    final ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
                    StackFrame frame = threadInfo.getCurrentFrame();
                    if (frame != null) {
                        Location loc = frame.location();
                        if (loc.declaringType().name() != null) {
                            String anonyClass = loc.declaringType().name();
                            int count = anonyClass.length() - anonyClass.replaceAll("\\$", "").length();  
                            int offset = anonyClass.lastIndexOf("$");
                            count--;                            
                            String aap = "this$"+count;

                            while (offset > 0) {
                                String pclass = anonyClass.substring(0,offset);

                                if (pclass != null && pclass.length() > 0) {
                                    ReferenceType cls = Env.getReferenceTypeFromToken(pclass);
                                    if (cls != null) {     
                                        val = evaluate(aap + "." + expr);
                                        if (val != null)
                                            break;
                                        count--;
                                        aap = aap + "." + "this$" + count;
                                    }

                                }else
                                    break;

                                anonyClass = pclass;
                                offset = anonyClass.lastIndexOf("$");
                            }
                        }
                    }
                } catch (Exception ex) {
                    // do nothing
                }  
            }          

            if (val == null) { 
                return null; 
            } else if (dumpObject && (val instanceof ObjectReference) &&
                        !(val instanceof StringReference)) {
                ObjectReference obj = (ObjectReference)val;
                ReferenceType refType = obj.referenceType();
                MessageOutput.println("expr is value",
                                            new Object [] {expr.toString(),
                                            MessageOutput.format("grouping begin character")});
                dump(obj, refType, refType);
                MessageOutput.println("grouping end character");
            } else {
                Value v = getValue(); 
                if (v != null) {
                    String strVal = v.toString();
                    if (val.type().name().equals("java.lang.String"))
                        return strVal.substring(1,strVal.length() - 1);  
                    else
                        return strVal;

                } 
            }
        }
        return "";
    } 

    void commandPrint(final StringTokenizer t, final boolean dumpObject) {
        new AsyncExecution() {
                void action() {
                    doPrint(t, dumpObject);
                }
            }; 
    }
 
    void commandEPrint(final StringTokenizer t, final boolean dumpObject) {
        new AsyncExecution() {
            void action() {
                doEPrint(t, dumpObject);  
            }
        }; 
    }
    
    void commandPrintSync(final StringTokenizer t, final boolean dumpObject) {
        doPrint(t, dumpObject);
    }
    
    void commandEPrintSync(final StringTokenizer t, final boolean dumpObject) {
        doEPrint(t, dumpObject);
    }    

    String commandExpr(final StringTokenizer t) {
        return expr(t, false);
    }

    void commandSetp(final StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            System.out.println("");
            System.out.println("preferences : ");
            System.out.println("\tlistsize = " + tty.LIST_SIZE);
            System.out.println("\tscrollsize = " + tty.LIST_SCROLL_SIZE);
            System.out.println("\tautosavebreak = " + tty.autosavebreak);
            System.out.println("\tport = " + tty.SERVER_PORT);
            System.out.println("\tpagemode = " + (tty.pageMode ? "on" : "off"));            
            return;
        }
        String p = t.nextToken();
        if (p.equals("port")) {
            String _port = t.nextToken();
            int port = Integer.parseInt(_port);            
            tty.SERVER_PORT = port;
            System.out.println("setp port = " + tty.SERVER_PORT);
        } if (p.equals("listsize")) {
            String size = t.nextToken();
            int listsize = Integer.parseInt(size);
            if (listsize > 0) {
                tty.LIST_SIZE = listsize;
                if (tty.pageMode)
                    tty.scrollSize = tty.LIST_SIZE;
            }
            System.out.println("setp listsize = " + tty.LIST_SIZE);
        } else if (p.equals("scrollsize")) {
            String size = t.nextToken();
            int offsetsize = Integer.parseInt(size);
            if (offsetsize > 0) {
                tty.LIST_SCROLL_SIZE = offsetsize;
                if (!tty.pageMode)
                    tty.scrollSize = tty.LIST_SCROLL_SIZE;
            }
            System.out.println("setp scrollsize = " + tty.LIST_SCROLL_SIZE);
        } else if (p.equals("autosavebreak")) {
            String size = t.nextToken();
            boolean bsave = Boolean.parseBoolean(size);
            tty.autosavebreak = bsave;
            System.out.println("setp autosavebreak = " + tty.autosavebreak);
        } else if (p.equals("pagemode")) {
            String onoff = t.nextToken();
            if (onoff.equals("on"))
                tty.pageMode= true;
            else
                tty.pageMode= false;

            if (onoff.equals("on")) {
                tty.scrollSize = tty.LIST_SIZE;
                tty.pageMode = true;
                System.out.println("<offset is one Page>");
            } else if (onoff.equals("off")) {

                tty.scrollSize = tty.LIST_SCROLL_SIZE;
                tty.pageMode = false;
                System.out.println("<offset is several lines>");
            } else {
            System.out.println("Preference value is invalid.");
            }
        } else {
            System.out.println("Invalid preference");
        }
    }     

    void commandSet(final StringTokenizer t) {
        String all = t.nextToken("");

        /*
         * Bare bones error checking. 
         */
        if (all.indexOf('=') == -1) {
            MessageOutput.println("Invalid assignment syntax");
            MessageOutput.printPrompt();
            return;
        }

        /*
         * The set command is really just syntactic sugar. Pass it on to the 
         * print command.
         */
        commandPrint(new StringTokenizer(all), false);
    }

    void doLock(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No object specified.");
            return;
        }

        String expr = t.nextToken("");
        Value val = evaluate(expr);

        try {
            if ((val != null) && (val instanceof ObjectReference)) {
                ObjectReference object = (ObjectReference)val;
                String strVal = getStringValue();
                if (strVal != null) {
                    MessageOutput.println("Monitor information for expr",
                                      new Object [] {expr.trim(),
                                                     strVal});
                } 
                ThreadReference owner = object.owningThread();
                if (owner == null) {
                    MessageOutput.println("Not owned");
                } else {
                    MessageOutput.println("Owned by:",
                                          new Object [] {owner.name(),
                                                         new Integer (object.entryCount())});
                }
                List waiters = object.waitingThreads();
                if (waiters.size() == 0) {
                    MessageOutput.println("No waiters");
                } else {
                    Iterator iter = waiters.iterator();
                    while (iter.hasNext()) {
                        ThreadReference waiter = (ThreadReference)iter.next();
                        MessageOutput.println("Waiting thread:", waiter.name());
                    }
                }
            } else {
                MessageOutput.println("Expression must evaluate to an object");
            }
        } catch (IncompatibleThreadStateException e) {
            MessageOutput.println("Threads must be suspended");
        }
    }

    void commandLock(final StringTokenizer t) {
        new AsyncExecution() {
                void action() {
                    doLock(t);
                }
            }; 
    }

    private void printThreadLockInfo(ThreadInfo threadInfo) {
        ThreadReference thread = threadInfo.getThread();
        try {
            MessageOutput.println("Monitor information for thread", thread.name());
            List owned = thread.ownedMonitors();
            if (owned.size() == 0) {
                MessageOutput.println("No monitors owned");
            } else {
                Iterator iter = owned.iterator();
                while (iter.hasNext()) {
                    ObjectReference monitor = (ObjectReference)iter.next();
                    MessageOutput.println("Owned monitor:", monitor.toString()); 
                }
            }
            ObjectReference waiting = thread.currentContendedMonitor();
            if (waiting == null) {
                MessageOutput.println("Not waiting for a monitor");
            } else {
                MessageOutput.println("Waiting for monitor:", waiting.toString());
            }
        } catch (IncompatibleThreadStateException e) {
            MessageOutput.println("Threads must be suspended");
        }
    }

    void commandThreadlocks(final StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            ThreadInfo threadInfo = ThreadInfo.getCurrentThreadInfo();
            if (threadInfo == null) {
                MessageOutput.println("Current thread not set.");
            } else {
                printThreadLockInfo(threadInfo);
            }
            return;
        }
        String token = t.nextToken();
        if (token.toLowerCase().equals("all")) {
            Iterator iter = ThreadInfo.threads().iterator();
            while (iter.hasNext()) {
                ThreadInfo threadInfo = (ThreadInfo)iter.next();
                printThreadLockInfo(threadInfo);
            }
        } else {
            ThreadInfo threadInfo = doGetThread(token);
            if (threadInfo != null) {
                ThreadInfo.setCurrentThreadInfo(threadInfo);
                printThreadLockInfo(threadInfo);
            }
        }
    }

    void doDisableGC(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No object specified.");
            return;
        }

        String expr = t.nextToken("");
        Value val = evaluate(expr);
        if ((val != null) && (val instanceof ObjectReference)) {
            ObjectReference object = (ObjectReference)val;
            object.disableCollection();
            String strVal = getStringValue();
            if (strVal != null) {
                 MessageOutput.println("GC Disabled for", strVal);
            } 
        } else {
            MessageOutput.println("Expression must evaluate to an object");
        }
    }

    void commandDisableGC(final StringTokenizer t) {
        new AsyncExecution() {
                void action() {
                    doDisableGC(t);
                }
            }; 
    }

    void doEnableGC(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No object specified.");
            return;
        }

        String expr = t.nextToken("");
        Value val = evaluate(expr);
        if ((val != null) && (val instanceof ObjectReference)) {
            ObjectReference object = (ObjectReference)val;
            object.enableCollection();
            String strVal = getStringValue();
            if (strVal != null) {
                 MessageOutput.println("GC Enabled for", strVal);
            } 
        } else {
            MessageOutput.println("Expression must evaluate to an object");
        }
    }

    void commandEnableGC(final StringTokenizer t) {
        new AsyncExecution() {
                void action() {
                    doEnableGC(t);
                }
            }; 
    }

    void doSave(StringTokenizer t) {// Undocumented command: useful for testing.
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No save index specified.");
            return;
        }

        String key = t.nextToken();

        if (!t.hasMoreTokens()) {
            MessageOutput.println("No expression specified.");
            return;
        }
        String expr = t.nextToken("");
        Value val = evaluate(expr);
        if (val != null) {
            Env.setSavedValue(key, val);
            String strVal = getStringValue();
            if (strVal != null) {
                 MessageOutput.println("saved", strVal);
            } 
        } else {
            MessageOutput.println("Expression cannot be void");
        }
    }

    void commandSave(final StringTokenizer t) { // Undocumented command: useful for testing.
        if (!t.hasMoreTokens()) {
            Set keys = Env.getSaveKeys();
            Iterator iter = keys.iterator();
            if (!iter.hasNext()) {
                MessageOutput.println("No saved values");
                return;
            }
            while (iter.hasNext()) {
                String key = (String)iter.next();
                Value value = Env.getSavedValue(key);
                if ((value instanceof ObjectReference) &&
                    ((ObjectReference)value).isCollected()) {
                    MessageOutput.println("expr is value <collected>",
                                          new Object [] {key, value.toString()});
                } else {
                    if (value == null){
                        MessageOutput.println("expr is null", key);
                    } else {
                        MessageOutput.println("expr is value",
                                              new Object [] {key, value.toString()});
                    }
                }
            }
        } else {
            new AsyncExecution() {
                    void action() {
                        doSave(t);
                    }
                }; 
        }

    }

   void commandBytecodes(final StringTokenizer t) { // Undocumented command: useful for testing.
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No class specified.");
            return;
        }
        String className = t.nextToken();

        if (!t.hasMoreTokens()) {
            MessageOutput.println("No method specified.");
            return;
        }
        // Overloading is not handled here.
        String methodName = t.nextToken();

        List classes = Env.vm().classesByName(className);
        // TO DO: handle multiple classes found
        if (classes.size() == 0) {
            if (className.indexOf('.') < 0) {
                MessageOutput.println("not found (try the full name)", className);
            } else {
                MessageOutput.println("not found", className);
            }
            return;
        } 
        
        ReferenceType rt = (ReferenceType)classes.get(0);
        if (!(rt instanceof ClassType)) {
            MessageOutput.println("not a class", className);
            return;
        }

        byte[] bytecodes = null;                                               
        List list = rt.methodsByName(methodName);
        Iterator iter = list.iterator();
        while (iter.hasNext()) {
            Method method = (Method)iter.next();
            if (!method.isAbstract()) {
                bytecodes = method.bytecodes();
                break;
            }
        }

        StringBuffer line = new StringBuffer(80);
        line.append("0000: ");
        for (int i = 0; i < bytecodes.length; i++) {
            if ((i > 0) && (i % 16 == 0)) {
                MessageOutput.printDirectln(line.toString());// Special case: use printDirectln()
                line.setLength(0);
                line.append(String.valueOf(i));
                line.append(": ");
                int len = line.length();
                for (int j = 0; j < 6 - len; j++) {
                    line.insert(0, '0');
                }
            }
            int val = 0xff & bytecodes[i];
            String str = Integer.toHexString(val);
            if (str.length() == 1) {
                line.append('0');
            }
            line.append(str);
            line.append(' ');
        }
        if (line.length() > 6) {
            MessageOutput.printDirectln(line.toString());// Special case: use printDirectln()
        }
    }

    void commandExclude(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.printDirectln(Env.excludesString());// Special case: use printDirectln()
        } else {
            String rest = t.nextToken("");
            if (rest.equals("none")) {
                rest = "";
            }
            Env.setExcludes(rest);
        }
    }
    
    void commandRedefine(StringTokenizer t) { 
        if (!Env.vm().canRedefineClasses()) {
            System.out.println("vm can't redefine classes.");
            return;
        } 
            
        if (!t.hasMoreTokens()) {
            MessageOutput.println("Specify classes to redefine");
        } else {
            String className = t.nextToken(); 
            List classes = Env.vm().classesByName(className);
            if (classes.size() == 0) {
                MessageOutput.println("No class named", className);
                return;
            }
            if (classes.size() > 1) {
                MessageOutput.println("More than one class named", className);
                return;
            }

            Env.setSourcePath(Env.getSourcePath());
            ReferenceType refType = (ReferenceType)classes.get(0);            
  
            Map map = new HashMap();            
            String fileName = "";
            List<String> fileNameAnonymousClasses = new ArrayList<String>();
            List<String> AnonymousClassesId = new ArrayList<String>();
            if (t.hasMoreTokens())
                fileName = t.nextToken();
            else {
                if (Env.connection().isOpen() && Env.vm() instanceof PathSearchingVirtualMachine && className != null) {
                    PathSearchingVirtualMachine vm = (PathSearchingVirtualMachine)Env.vm();
                    List<String> cps = vm.classPath();

                    String classId = className;                    
                    String classDir = File.separator;
                    String classNameWithoutPackage=classId;
                    String classPackage="";
                    int index = classId.lastIndexOf(".");
                    if (index != -1) {
                        classDir = File.separatorChar+classId.replace('.',File.separatorChar).substring(0,index);
                        classNameWithoutPackage=classId.substring(index + 1,classId.length());
                        classPackage = classId.substring(0,index);
                    }                    

                    List<String> xcps = new ArrayList<String>();
                    for (String cp : cps)
                        xcps.add(cp);

                    if (TTY.xclasspath != null && TTY.xclasspath.length() > 0) {
                        try {
                            String [] xcls =  TTY.xclasspath.split(":");
                            for(int j=0; j<xcls.length;j++)
                                xcps.add(xcls[j]);
                        } catch(Exception ex) {
                            ex.printStackTrace();
                        }
                    }

                    for (String cp : xcps) {
                        if (!cp.toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                            File f= new File(cp);
                            if (f != null && f.exists() && f.isDirectory()) {
                                String searchDir = f.getAbsoluteFile()+classDir;
                                File s = new File(searchDir);
                                if (s != null && s.exists() && s.isDirectory()) {
                                    File[] flist = s.listFiles(); 
                                    if (flist != null) {
                                        for(File fl : flist) {                                        
                                            if (fl.getName().equals(classNameWithoutPackage + ".class")) {
                                                fileName = fl.getAbsolutePath();
                                            }                                            
                                            if (fl.getName().startsWith(classNameWithoutPackage + "$")) {                                                
                                                fileNameAnonymousClasses.add(fl.getAbsolutePath());
                                                String anClassNameWithoutPackage=fl.getName().substring(0, fl.getName().length() - 6);
                                                String anClassNameClassId = "";
                                                if (classPackage.length() <= 0)
                                                    anClassNameClassId = anClassNameWithoutPackage;
                                                else
                                                    anClassNameClassId = classPackage + "." + anClassNameWithoutPackage;
                                                
                                                AnonymousClassesId.add(anClassNameClassId);
                                            }
                                            
                                        }
                                    }
                                }
                            }
                                         
                        } 
                    }

                }
            }
            
            if (fileName.length() <= 0) {
                MessageOutput.println("Specify file name for class", className);
                return;            
            }

            File phyl = new File(fileName);
            byte[] bytes = new byte[(int)phyl.length()];
            try {
                InputStream in = new FileInputStream(phyl);
                in.read(bytes);
                in.close();
            } catch (Exception exc) {
                MessageOutput.println("Error reading file",
                             new Object [] {fileName, exc.toString()});
                return;
            }
            map.put(refType, bytes);
            
            List<String> successAnonymousFilepaths = new ArrayList<String>();
            for (int i = 0; i < fileNameAnonymousClasses.size(); i++) {
                File f = new File(fileNameAnonymousClasses.get(i));
                byte[] abytes = new byte[(int)f.length()];
                
                try {
                    InputStream in = new FileInputStream(f);
                    in.read(abytes);
                    in.close();
                } catch (Exception exc) {        
                    continue;
                }                

                String aClassName = AnonymousClassesId.get(i);

                List aClasses = Env.vm().classesByName(aClassName);
                if (aClasses.size() == 0) {
                    continue;
                }
                if (aClasses.size() > 1) {
                    continue;
                }
                
                ReferenceType aRefType = (ReferenceType)aClasses.get(0);                

                successAnonymousFilepaths.add(fileNameAnonymousClasses.get(i));
                map.put(aRefType, abytes);                
                
            }       
            
            boolean loadSuccess = true;
            try {
                Env.vm().redefineClasses(map);
            } catch (Throwable exc) {
                MessageOutput.println("Error redefining class to file",
                             new Object [] {className,
                                            fileName,
                                            exc});
                loadSuccess = false; 
            }
            if (loadSuccess) {
                System.out.println("Classes reloaded successfully :");
                System.out.println(fileName);            
                for (String p : successAnonymousFilepaths) {
                    System.out.println(p);
                }
            }
        }
    }

    void commandPopFrames(StringTokenizer t, boolean reenter) {
        ThreadInfo threadInfo;

        if (t.hasMoreTokens()) {
            String token = t.nextToken();
            threadInfo = doGetThread(token);
            if (threadInfo == null) {
                return;
            }
        } else {
            threadInfo = ThreadInfo.getCurrentThreadInfo();
            if (threadInfo == null) {
                MessageOutput.println("No thread specified.");
                return;
            }
        }
       
        try {
            StackFrame frame = threadInfo.getCurrentFrame();
            threadInfo.getThread().popFrames(frame);
            threadInfo = ThreadInfo.getCurrentThreadInfo();
            ThreadInfo.setCurrentThreadInfo(threadInfo);
            if (reenter) {
                commandStepi();
            }
        } catch (Throwable exc) {
            MessageOutput.println("Error popping frame", exc.toString());
        }
    }

    void commandExtension(StringTokenizer t) {
        if (!t.hasMoreTokens()) {
            MessageOutput.println("No class specified.");            
            return;
        }

        String idClass = t.nextToken();
        ReferenceType cls = Env.getReferenceTypeFromToken(idClass);
        String extension = null;
        if (cls != null) {
            try {
                extension = cls.sourceDebugExtension();
                MessageOutput.println("sourcedebugextension", extension);
            } catch (AbsentInformationException e) {
                MessageOutput.println("No sourcedebugextension specified");
            }
        } else {
            MessageOutput.println("is not a valid id or class name", idClass);
        }
    }

    void commandVersion(String debuggerName,
                        VirtualMachineManager vmm) {
        MessageOutput.println("minus version",
                              new Object [] { debuggerName,
                                              new Integer(vmm.majorInterfaceVersion()),
                                              new Integer(vmm.minorInterfaceVersion()),
                                                  System.getProperty("java.version")});
        if (Env.connection() != null) {
            try {
                MessageOutput.printDirectln(Env.vm().description());// Special case: use printDirectln()
            } catch (VMNotConnectedException e) {
                MessageOutput.println("No VM connected");
            }
        }
    }

    SortedSet getListClassMethod(String text) {        
        try {
            SortedSet  commandSet = new TreeSet();
            if (true) {
                String idClass  = null;
                ReferenceType methodClass = null;                
                idClass = text;

                for (Iterator it = Env.vm().allClasses().iterator(); it.hasNext();) {
                    ReferenceType refType = (ReferenceType)it.next();

                    if (idClass != null) {  
                        if (refType.name().startsWith(idClass)) {
                            commandSet.add(refType.name());
                        }
                    }
                }
            }

            return commandSet;
        } catch(VMNotConnectedException e) {
            return null;
        }
    }

    String matchClassFromClasspathForLine(String classId,  int lineno) {        
        boolean found = false;

        if (Env.connection().isOpen() && Env.vm() instanceof PathSearchingVirtualMachine && classId != null) {
            PathSearchingVirtualMachine vm = (PathSearchingVirtualMachine)Env.vm();
            List<String> cps = vm.classPath();

            String classDir = File.separator;
            String className=classId;
            String classPackage="";
            int index = classId.lastIndexOf(".");
            if (index != -1) {
                classDir = File.separatorChar+classId.replace('.',File.separatorChar).substring(0,index);
                className=classId.substring(index + 1,classId.length());
                classPackage = classId.substring(0,index);
            }            

            List<String> xcps = new ArrayList<String>();
            for (String cp : cps)
                xcps.add(cp);

            if (TTY.xclasspath != null && TTY.xclasspath.length() > 0) {
                try {
                    String [] xcls =  TTY.xclasspath.split(":");
                    for(int j=0; j<xcls.length;j++)
                        xcps.add(xcls[j]);
                } catch(Exception ex) {
                    ex.printStackTrace();
                }
            }     
            
            for (String cp : xcps) {
                if (cp.toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                    try {
                        JarInputStream jarIn = new JarInputStream( new FileInputStream(cp));
                        JarFile jfile = new JarFile(new File(cp));
                        JarEntry entry = jarIn.getNextJarEntry();
                        while (entry != null) {
                            if (entry.getName().endsWith(".class") && entry.getName().replace(File.separatorChar, '.').startsWith(classId+"$")) {
                                String class_id = entry.getName().replace(File.separatorChar, '.');
                                found = true;
                                InputStream filein =  jfile.getInputStream(entry);
                                if(ClassParser.matchClassMethodLines(filein, lineno))
                                    return class_id.substring(0,class_id.length()-6);                                
                            }

                            entry = jarIn.getNextJarEntry();
                        }
                    } catch(Exception e) {
                        // do nothing.
                    }
                } else {
                    File f= new File(cp);
                    if (f != null && f.exists() && f.isDirectory()) {
                        String searchDir = f.getAbsoluteFile()+classDir;
                        File s = new File(searchDir);
                        if (s != null && s.exists() && s.isDirectory()) {
                            File[] flist = s.listFiles(); 
                            if (flist != null) {
                                for(File fl : flist) {
                                    if (fl.getName().startsWith(className+"$")) {
                                        String class_id = classPackage+"."+fl.getName();
                                        found = true;
                                        if(ClassParser.matchClassMethodLines(fl.getAbsolutePath(), lineno))
                                            return class_id.substring(0,class_id.length()-6);
                                            
                                    }
                                }
                            }
                        }
                    }
                }

                if (found)
                    break;
            }
        }
        return null;
    }
    
    List<String> matchClassFromClasspath(String classId) {
        ArrayList matchList = new ArrayList();

        if (Env.connection().isOpen() && Env.vm() instanceof PathSearchingVirtualMachine && classId != null) {
            PathSearchingVirtualMachine vm = (PathSearchingVirtualMachine)Env.vm();
            List<String> cps = vm.classPath();

            String classDir = File.separator;
            String className=classId;
            String classPackage="";
            int index = classId.lastIndexOf(".");
            if (index != -1) {
                classDir = File.separatorChar+classId.replace('.',File.separatorChar).substring(0,index);
                className=classId.substring(index + 1,classId.length());
                classPackage = classId.substring(0,index);
            }            

            List<String> xcps = new ArrayList<String>();
            for (String cp : cps)
                xcps.add(cp);

            if (TTY.xclasspath != null && TTY.xclasspath.length() > 0) {
                try {
                    String [] xcls =  TTY.xclasspath.split(":");
                    for(int j=0; j<xcls.length;j++)
                        xcps.add(xcls[j]);
                } catch(Exception ex) {
                    ex.printStackTrace();
                }
            }       
            
            for (String cp : xcps) {
                if (cp.toLowerCase(Locale.ENGLISH).endsWith(".jar")) {
                    try {
                        JarInputStream jarIn = new JarInputStream( new FileInputStream(cp));
                        JarEntry entry = jarIn.getNextJarEntry();
                        while (entry != null) {
                            if (entry.getName().endsWith(".class") && entry.getName().replace(File.separatorChar, '.').startsWith(classId)) {
                                matchList.add(entry.getName().replace(File.separatorChar, '.'));
                            }

                            entry = jarIn.getNextJarEntry();
                        }
                    } catch(Exception e) {
                        // do nothing.
                    }
                } else {
                    File f= new File(cp);
                    if (f != null && f.exists() && f.isDirectory()) {
                        String searchDir = f.getAbsoluteFile()+classDir;
                        File s = new File(searchDir);
                        if (s != null && s.exists() && s.isDirectory()) {
                            File[] flist = s.listFiles(); 
                            if (flist != null) {
                                for(File fl : flist) {
                                    if (fl.getName().startsWith(className)) {
                                        matchList.add(classPackage+"."+fl.getName());
                                    }
                                }
                            }
                        }
                    }
                }

                if (matchList.size() > 0)
                    break;
            }
        }
        return matchList;
    }
    
    boolean matchClassMethodLines(String idClass, int lineno) {
        if (false) {
            ;
        } else {  
            String idMethod = null;
            try {
                ReferenceType refType = Env.getReferenceTypeFromToken(idClass);
                if (refType != null) {
                    List lines = null;
                    if (idMethod == null) {
                        lines = refType.allLineLocations();
                    } else {
                        List methods = refType.allMethods();
                        Iterator iter = methods.iterator();
                        while (iter.hasNext()) {
                            Method method = (Method)iter.next();
                            if (method.name().equals(idMethod)) {
                                lines = method.allLineLocations();
                            }
                        }
                        if (lines == null) {
                            return false;
                        }
                    }
                    Iterator iter = lines.iterator();
                    while (iter.hasNext()) {
                        Location line = (Location)iter.next();
                        if (line.lineNumber() == lineno)
                            return true;
                    }
                } else {
                    return false;
                }
            } catch (AbsentInformationException e) {
                return false;
            }
        }

        return false;
    }
}
