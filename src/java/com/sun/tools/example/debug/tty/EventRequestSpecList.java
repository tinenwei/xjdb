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
import com.sun.jdi.request.EventRequest;
import com.sun.jdi.event.ClassPrepareEvent;

import java.util.ArrayList;
import java.util.Collections;

import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;

import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

class EventRequestSpecList {

    private static final int statusResolved = 1;
    private static final int statusUnresolved = 2;
    private static final int statusError = 3;
    
    // all specs
    private List eventRequestSpecs = Collections.synchronizedList(
                                                  new ArrayList());
    private List eventRequestSpecsID = Collections.synchronizedList(
                                                  new ArrayList());     

    private Map<Integer, Property> eventRequestSpecsMap = Collections.synchronizedMap(
                                                   new HashMap<Integer, Property>()); 

    int seqId= 0;
    public static class Property {
        public Property() {
            this.id = -1;
            this.enable = true;
            this.temporary = false;
            cmdList = null;
            breakcondition = null;            
            scriptObj = null;
            count = 0;
        }

        public Property(int id, boolean enable, boolean temporary) {
            this.id = id;
            this.enable = enable;
            this.temporary = temporary;
            cmdList = null;
            breakcondition = null;            
            scriptObj = null;
            count = 0;
        }

        int id;
        boolean enable;
        boolean temporary;
        List cmdList;
        String breakcondition;       
        ScriptObject scriptObj;
        int count;

        public boolean getEnable() {
            return enable;        
        }    

        public int getId() {
            return id;        
        }    
    }                                     

    EventRequestSpecList() {
    }

    /** 
     * Resolve all deferred eventRequests waiting for 'refType'.
     * @return true if it completes successfully, false on error.
     */
    boolean resolve(ClassPrepareEvent event) {
        boolean failure = false;
        ArrayList deleteIds = new ArrayList();
        synchronized(eventRequestSpecs) {
            int index  = 0;
            Iterator iter = eventRequestSpecs.iterator();
            while (iter.hasNext()) {
                Property p = (Property)eventRequestSpecsID.get(index);
                int id = p.id;
                index++;
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                if (!spec.isResolved()) {
                    try {
                        EventRequest eventRequest = spec.resolve(event);
                        if (eventRequest != null) {                            
                            eventRequest.putProperty("id",id);
                            if (p.enable)
                                eventRequest.enable();
                            else
                                eventRequest.disable();                                
                            MessageOutput.println("Set deferred", spec.toString());
                        }
                    } catch (Exception e) {
                        MessageOutput.println("Unable to set deferred",
                                              new Object [] {spec.toString(),
                                                             spec.errorMessageFor(e)});
                        deleteIds.add(index - 1); 
                        failure = true;
                    }
                }
            }
            for(int j = deleteIds.size() - 1; j >= 0; j--)
                delete((Integer)deleteIds.get(j));      

        }
        return !failure;
    }

    void resolveAll() {
        Iterator iter = eventRequestSpecs.iterator();
        int index  = 0;
        ArrayList deleteIds = new ArrayList();
        while (iter.hasNext()) {
            Property p = (Property)eventRequestSpecsID.get(index);
            int id = p.id;
            index++;
            EventRequestSpec spec = (EventRequestSpec)iter.next();
            try {
                EventRequest eventRequest = spec.resolveEagerly();
                if (eventRequest != null) {
                   eventRequest.putProperty("id",id);
                   if (p.enable)
                       eventRequest.enable();
                   else
                       eventRequest.disable();                                
                    MessageOutput.println("Set deferred", spec.toString());
                } 
            } catch (Exception e) {
	               deleteIds.add(index-1); 
            }
        }
        
        for(int j = deleteIds.size() - 1; j >= 0 ; j--)
            delete((Integer)deleteIds.get(j));      
        
    }

    boolean addEagerlyResolve(EventRequestSpec spec, boolean temporary) {
        try {
            EventRequest eventRequest = spec.resolveEagerly();
            if (eventRequest != null) {
                MessageOutput.println("Set", spec.toString());
                eventRequest.putProperty("id",seqId);
            } 
            Property p = new Property(seqId, true, temporary);
            eventRequestSpecsID.add(p);
            eventRequestSpecsMap.put(new Integer(seqId),p);
             
            eventRequestSpecs.add(spec);
            seqId++;
            return true;
        } catch (Exception exc) {
            MessageOutput.println("Unable to set",
                                  new Object [] {spec.toString(),
                                                 spec.errorMessageFor(exc)});
            return false;
        }
    }

    EventRequestSpec createBreakpoint(String classPattern, 
                                 int line) throws ClassNotFoundException {
        ReferenceTypeSpec refSpec = 
            new PatternReferenceTypeSpec(classPattern);
        return new BreakpointSpec(refSpec, line);
    }
        
    EventRequestSpec createBreakpoint(String classPattern, 
                                 String methodId, 
                                 List methodArgs) 
                                throws MalformedMemberNameException, 
                                       ClassNotFoundException {
        ReferenceTypeSpec refSpec = 
            new PatternReferenceTypeSpec(classPattern);
        return new BreakpointSpec(refSpec, methodId, methodArgs);
    }
        
    EventRequestSpec createExceptionCatch(String classPattern,
                                          boolean notifyCaught,
                                          boolean notifyUncaught)
                                            throws ClassNotFoundException {
        ReferenceTypeSpec refSpec = 
            new PatternReferenceTypeSpec(classPattern);
        return new ExceptionSpec(refSpec, notifyCaught, notifyUncaught);
    }
        
    EventRequestSpec createAccessWatchpoint(String classPattern, 
                                       String fieldId) 
                                      throws MalformedMemberNameException, 
                                             ClassNotFoundException {
        ReferenceTypeSpec refSpec = 
            new PatternReferenceTypeSpec(classPattern);
        return new AccessWatchpointSpec(refSpec, fieldId);
    }
        
    EventRequestSpec createModificationWatchpoint(String classPattern, 
                                       String fieldId) 
                                      throws MalformedMemberNameException, 
                                             ClassNotFoundException {
        ReferenceTypeSpec refSpec = 
            new PatternReferenceTypeSpec(classPattern);
        return new ModificationWatchpointSpec(refSpec, fieldId);
    }

    boolean delete(EventRequestSpec proto) {
        synchronized (eventRequestSpecs) {
            int inx = eventRequestSpecs.indexOf(proto);
            if (inx != -1) {
                EventRequestSpec spec = (EventRequestSpec)eventRequestSpecs.get(inx);
                spec.remove();
                eventRequestSpecs.remove(inx);
                Property p = (Property)eventRequestSpecsID.get(inx);
                eventRequestSpecsMap.remove(p.id);
                eventRequestSpecsID.remove(inx);             
                return true;
            } else {
                return false;
            }
        }
    }

    List eventRequestSpecs() {
       // We need to make a copy to avoid synchronization problems
        synchronized (eventRequestSpecs) {
            return new ArrayList(eventRequestSpecs);
        }
    }

    List eventRequestSpecsID() {
        // We need to make a copy to avoid synchronization problems
        synchronized (eventRequestSpecsID) {
            return new ArrayList(eventRequestSpecsID);
        }
    }

    Map eventRequestSpecsMap() {
        // We need to make a copy to avoid synchronization problems
        synchronized (eventRequestSpecsMap) {
            return new HashMap(eventRequestSpecsMap);
        }
    }

    boolean deleteAll() {
        synchronized (eventRequestSpecs) {            
            for(int i = 0; i < eventRequestSpecs.size(); i++) {    
                 EventRequestSpec spec = (EventRequestSpec)eventRequestSpecs.get(i);
                 spec.remove();
            }
            eventRequestSpecs.clear();
            eventRequestSpecsID.clear();            
            eventRequestSpecsMap.clear();
           
           return true;    
        }
    }

    boolean delete(int start, int end) { 
        synchronized (eventRequestSpecs) {       
            ArrayList<Integer> ids = new ArrayList<Integer>();
            Iterator iter = eventRequestSpecs.iterator();    
            int index = 0;
            while (iter.hasNext()) {
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property) eventRequestSpecsID.get(index);

                if (p.id > end)
                    break;
            
                if (p.id >= start)
                    ids.add(index);

                index++;
            }

            for (int i = ids.size() - 1; i >= 0; i--) {
                int id = (int)ids.get(i);            
                delete(id);
            }        
            return true;        
        }
    }
    
    boolean delete(int[] _ids) {
        Arrays.sort(_ids);
        synchronized (eventRequestSpecs) {
            ArrayList<Integer> ids = new ArrayList<Integer>();
            Iterator iter = eventRequestSpecs.iterator();    
            int index = 0;
            while (iter.hasNext()) {
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property)eventRequestSpecsID.get(index);

                for (int j = 0; j < _ids.length; j++) {            
                    if (p.id == _ids[j]) {
                        ids.add(index);
                        break;                    
                    }                
                }                
                index++;
            }

            for (int i = ids.size() - 1; i >= 0; i--) {
                int id = (int)ids.get(i);            
                delete(id);
            }
        
            return true;        
        }
    }
  
    boolean disableAll() {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();    
            int i = 0;
            while (iter.hasNext()) {
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                if (spec.resolved() != null)
                    spec.resolved().disable();

                Property p = (Property)eventRequestSpecsID.get(i);
                p.enable = false;
                i++;
            }               
            return true;     
        }
    }

    boolean disable(int start, int end) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();    
            int i = 0;
            while (iter.hasNext()) {            
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property) eventRequestSpecsID.get(i);

                if (p.id > end)
                    break;
            
                if (p.id >= start) {
                    if (spec.resolved() != null)
                        spec.resolved().disable();
                
                    p.enable = false;
                }
                i++;
            }               
            return true;     
        }
    }

    boolean disable(int[] ids) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();    
            int i = 0;
            while (iter.hasNext()) {            
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property)eventRequestSpecsID.get(i);

                for (int j = 0; j < ids.length; j++) {            
                    if (p.id == ids[j]) {
                        if (spec.resolved() != null)
                            spec.resolved().disable();

                    p.enable = false;
                    break;
                    }
                }
                i++;
            }               
            return true;     
        }
    }

 
    boolean temporaryAll(boolean value) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();   
            int i = 0;
            while (iter.hasNext()) {
                EventRequestSpec spec = (EventRequestSpec)iter.next();          
                Property p = (Property) eventRequestSpecsID.get(i);
                p.temporary = value;
                i++;
            }               
            return true;
        }
    }
 
    boolean temporary(int start, int end, boolean value) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();   
            int i = 0;
            while (iter.hasNext()) {              
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property) eventRequestSpecsID.get(i);
 
                if (p.id > end)
                    break;
          
                if (p.id >= start) {
                    if (spec.resolved() != null)
                        spec.resolved().disable();
              
                    p.temporary = true;
                }
                i++;
            }               
            return true;   
        }
    }
 
    boolean temporary(int[] ids, boolean value) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();   
            int i = 0;
            while (iter.hasNext()) {              
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property)eventRequestSpecsID.get(i);
 
                for (int j = 0; j < ids.length; j++) {           
                    if (p.id == ids[j]) {
                        p.enable = value;
                        break;
                    }
                }
                i++;
            }               
            return true;   
        }
    }

    boolean breakConditionAll(String cond) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();     
            int i = 0;
            while (iter.hasNext()) {
                EventRequestSpec spec = (EventRequestSpec)iter.next();          
                Property p = (Property)eventRequestSpecsID.get(i);
                if (cond.length() > 0) {
                     p.breakcondition = cond;                    
                } else {
                     p.breakcondition = null;
                }
                i++;
            }                 
            return true;      
        }
    }    

 

    boolean breakCondition(int start, int end, String cond) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();    
            int i = 0;
            while (iter.hasNext()) {            
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property)eventRequestSpecsID.get(i);
   
                if (p.id > end)
                    break;
            
                if (p.id >= start) {
                    /*
                    if (spec.resolved()!=null)
                        spec.resolved().disable();
                    */
                    if (cond.length() > 0) {
                        p.breakcondition = cond;                    
                    } else {
                        p.breakcondition = null;
                    }
                }
                i++;
            }                
            return true;     
        }
    }
   
    boolean breakCondition(int[] ids, String cond) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();    
            int i = 0;
            while (iter.hasNext()) {            
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property) eventRequestSpecsID.get(i);
   
                for(int j = 0; j < ids.length; j++) {            
                    if (p.id == ids[j]) {
                        if (cond.length() > 0) {
                            p.breakcondition = cond;
                        } else {
                            p.breakcondition = null;
                        }
                        break;
                    }
                }
                i++;
            }                
           return true;     
        }
    }
    
    boolean enableAll() {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();    
            int i = 0;
            while (iter.hasNext()) {
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                
                if (spec.resolved() != null)
                    spec.resolved().enable();

                Property p = (Property) eventRequestSpecsID.get(i);
                p.enable = true;

                i++;
            }               
            return true;         
        }
    }
    
    boolean enable(int start, int end) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();    
            int i = 0;
            while (iter.hasNext()) {            
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property) eventRequestSpecsID.get(i);

                if (p.id > end)
                    break;
                
                if (p.id >= start) {
                    if(spec.resolved()!=null)
                        spec.resolved().enable();
                    
                    p.enable = true;
                }
                i++;
            }               
            return true;         
        }
    }
    
    boolean enable(int[] ids) {
        synchronized (eventRequestSpecs) {
            Iterator iter = eventRequestSpecs.iterator();   
            int i = 0;
            while (iter.hasNext()) {              
                EventRequestSpec spec = (EventRequestSpec)iter.next();
                Property p = (Property) eventRequestSpecsID.get(i);

                for(int j = 0; j < ids.length; j++) {           
                    if(p.id == ids[j]) {
                        if(spec.resolved()!=null)
                            spec.resolved().enable();
              
                        p.enable = true;
                        break;
                    }
                }             
                i++;
            }               
            return true;       
        }
    }
    
    boolean delete(int inx) {
        synchronized (eventRequestSpecs) {
            if (inx != -1) {
                EventRequestSpec spec = (EventRequestSpec)eventRequestSpecs.get(inx);
                spec.remove();               
                eventRequestSpecs.remove(inx);
               Property p = (Property)eventRequestSpecsID.get(inx);
               eventRequestSpecsMap.remove(p.id);
               eventRequestSpecsID.remove(inx);             
               return true;
            } else {
                return false;
            }
        }
    }
}
