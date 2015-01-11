import sys
import re

'''
e('expr')	: return the result of executing java expression
_('cmd')  	: execute jdb command and print result in console
__('cmd') 	: execute jdb command and return the string of result.
_c('cmd')	: execute jdb command.
		  If command is defined in script, then pass script command 
                  and execute the internal command.
__c('cmd')  : same as _c('cmd') but return the string of result.	
_p('str')	: print string with newline
__p('str') 	: print string without newline 
load('script_file_path') : load other script file.
syscmd('cmd')   : execute system command and return the string of result.
'''


class StackFrames:
	'''
	StackFrames:
	     This is valid when stopping at a breakpoint.
	     Use StackFrames to get current stack frames.

		 ex:	 
			 var stf = new StackFrames()  => get current stack frames
			 stf.frameslist
			 stf.frameslist.length

		 The property of frame: 	 
			 stf.frameslist[index].id 
			 stf.frameslist[index].location 
			 stf.frameslist[index].file 
			 
		The methods:		 
			 stf.getById(id)        => get frame by id
			 stf.get(index)	 	=> get frame by index 
			 stf.size() 		=> frameslist size
		 
		 
		 stf[index] is equal to stf.frameslist[index] 
		 Therefore, you can also use :
		 stf[index].id, stf[index].location and stf[index].file
	'''
	def __init__(self):
		self.frameslist=[];
		l =__c('bt')
		lines =l.split('\n')
		#i=0
		#j=0
		for line in lines:
			pat=r'  \[(\d+)\] (.*) \((.*)\).*'
			k=re.search(pat,line);
			if k:
				o = {
						"id":int(k.group(1)),
						"location":k.group(2),
						"file":k.group(3).replace(",","")
				}
				self.frameslist.append(o) 
	
	def getById(id):
		if not self.frameslist:
			return None
		for frame in self.frameslist:
			if frame["id"]==id:
				return frame
		
		return None
		
	def get(index):
		if not self.frameslist:
			return None
		return 	self.frameslist[index]
		
	def size():
		if not self.frameslist:
			return 0
		return len(self.frameslist)
		
	def __getitem__(self, index):
		if not self.frameslist:
			return None
		return self.frameslist[index]


class BreakPoints:
	'''
	BreakPoints:
		Use BreakPoints to get current breakpoints.

		ex:
			var bps = new BreakPoints()  => get current breakpoints
			bps.breakpointslist
			bps.breakpointslist.length

		The property of breakpoint: (id is not all same as index)
		
			bps.breakpointslist[index].temporary 
			bps.breakpointslist[index].enable 
			bps.breakpointslist[index].resolve 
			bps.breakpointslist[index].cmdstatus 
			bps.breakpointslist[index].breakpoint 
			bps.breakpointslist[index].condition 
			bps.breakpointslist[index].hits
			
		The methods:
			bps.getById(id)         => get breakpoints by id
			bps.get(index)	        => get breakpoints by index 
			bps.size() 	        => breakpoints size
		
		bps[index] is equal to bps.breakpointslist[index]
		Therefore, you can also use :
		bps[index].temporary, bps[index].enable, etc.
	'''
	def __init__(self):		
		self.breakpointslist=[]
		l =__c('b')
		lines =l.split('\n')
		#i=0
		#j=0		
		for line in lines:
			pat=''
			eventType=1
			k=re.search(pat,line);
			if re.search("watch accesses of",line):
				pat=r'^\t\[\s*(\d+)\] ([t ]) ([ed]) ([yn]) ([ asc]{2}) watch accesses of (.*)\(hits: (\d+)\)'
				eventType=3
			elif re.search("watch modification of",line):
				pat=r'^\t\[\s*(\d+)\] ([t ]) ([ed]) ([yn]) ([ asc]{2}) watch modification of (.*)\(hits: (\d+)\)'
				eventType=2
			elif re.search("breakpoint",line):
				pat=r'^\t\[\s*(\d+)\] ([t ]) ([ed]) ([yn]) ([ asc]{2}) breakpoint (.*)\(hits: (\d+)\)'
				eventType=1
			else:
				continue
				
			bpat=r'(.*) {(.*)}'
			#print 'line=',line
			#print 'pat=',pat
			k=re.search(pat,line)
			breakp=''
			cond=''
			hits=0;
			
			if k and k.group(6) and k.group(7):
				hits=int(k.group(7))
				precmd = re.search(bpat,k.group(6))
				if precmd:					
					if precmd.group(1) and precmd.group(2):
						breakp=precmd.group(1)
						cond=precmd.group(2)
					else:
						breakp=k.group(6)
				else:
					breakp=k.group(6).strip()
			
			if k and k.group(1) and k.group(2) and k.group(3):
				o = {
						"id" : int(k.group(1)),
						"temporary" : ( k.group(2)=="t"),
						"enable" : (k.group(3)=="e"),
						"resolve" : (k.group(3)=="y"),
						"cmdstatus" : k.group(5),
						"breakpoint" : breakp,
						"eventType" : eventType,
						"condition" : cond,
						"hits" : hits
				}
				self.breakpointslist.append(o)
				
		self.number=-1
		self.length=len(self.breakpointslist)

	def getById(id):
		if not self.breakpointslist:
			return None
		for breakpoint in self.breakpointslist:
			if breakpoint["id"]==id:
				return breakpoint
		
		return None 
				
	def get(index):
		if not self.breakpointslist:
			return None
		return self.breakpointslist[index]
		
	def size():
		if not self.breakpointslist:
			return 0
		return len(self.breakpointslist)
		
	def __getitem__(self, index):
		if not self.breakpointslist:
			return None
		return self.breakpointslist[index]	

def Location():
	'''
	Location:
		This is valid when stopping at a breakpoint.
		Use Location to get the current location.
		ex:
			var loc = new Location()  => get the current Location
			loc.thread 
			loc.func   
			loc.line
			loc.bci	
	'''
	loc=jdb.getLocation()
	pat=r'"thread=(.*)", (.*) line=(\d+) bci=(\d+).*';
	k=re.search(pat,loc)
	o={}
	if k:	
		o.update({'thread':k.group(1)})
		o.update({'func':k.group(2)})
		o.update({'line':int(k.group(3))})
		o.update({'bci':int(k.group(4))})
	return o
	
def getBreakPointId(id):
	brkps=BreakPoints();
	for brkp in brkps.breakpointslist:
		if brkp['id']==id:
			return brkp
	return None

def getRegisterEventObject(func):
	class PyEventObject:
		pass

	pye = PyEventObject()
	pye.onEvent=func

	return pye
                            

def addStopEvent(func):
	'''
	addStopEvent(func):	
		func: While program is interrupted (breakpoint, step event etc), func will be executed,  
			  func(threadName)
			  	threadName => The name of the thread which are being interrupted.	

		return 0 if success. Otherwise, return -1. 	
	'''
	pye=getRegisterEventObject(func)
	result = jdb.addStopEvent(pye);    
	return result  

def removeStopEvent(func):
	'''
	removeStopEvent(func):
		func: This must be the same as the func of addStopEvent.
	'''
	result = jdb.removeStopEvent(func);    
	return result  

def addExitedEvent(func):
	'''
	addExitedEvent(func):	
		func: While program exits (quit, program normally or abnormally exited), func will be executed,  
			  func(cause)
			  	cause => cause=1 , for using 'quit' command to exit
			  			 cause=0 , Otherwise.
		return 0 if success. Otherwise, return -1. 	
	'''
	pye=getRegisterEventObject(func)
	result = jdb.addExitedEvent(pye);   
	return result 

def removeExitedEvent(func):
	'''
	removeExitedEvent(func);
		func: This must be the same as the func of addExitedEvent.	
	'''
	result = jdb.removeExitedEvent(func); 
	return result

# addContinueEvent is not implemented.
def addContinueEvent(func):
	pye=getRegisterEventObject(func)
	result = jdb.addContinueEvent(pye); 
	return result

def removeContinueEvent(func):
	result = jdb.removeContinueEvent(func); 
	return result
	
		
class COMPLETER:
	NONE = -1
	FILES = 1
	CLASS_METHOD = 2
	CLASS = 3
	PRINT = 4
	THREADS = 5 	


def addPycommand(cmdstr, cmdabbrevstr, func, _completer, _override=False, _disconnected=True, _readonly=True):
	'''
	addPycommand(cmdstr, cmdabbrevstr, func, _completer, 
				_override, _disconnected, _readonly):	
		Add a new script command. you can type cmdstr or cmdabbrevstr in command line of xjdb.

		parameters:				
			cmdstr : the complete name of the command.
			cmdabbrevstr : the abbreviation of the command.
			func :  when type cmdstr or cmdabbrevstr in read line of jdb.
					The arguments is collected behind the cmdstr or cmdabbrevstr.
			
			_completer: One of the following values. The default is -1.
					  COMPLETER.NONE = -1;
					  COMPLETER.FILES = 1;
					  COMPLETER.CLASS_METHOD = 2;
					  COMPLETER.CLASS = 3;
					  COMPLETER.PRINT = 4;
					  COMPLETER.THREADS = 5;
					  
			_override: to override any existed command. 
					   The default is False.
			_disconnected: The command can be used when VM is disconnected. 
					   The default is True.
			_readonly : The command is read-only command for a read-only VM connection. 
					   The default is True;

			return 0 if success. Otherwise, return -1. 		
	'''
	class PyCmd:
		pass	
	
	psy = PyCmd()
	psy.cmd = cmdstr or ""
	psy.cmdabbrev = cmdabbrevstr or ""
	psy.completer = _completer or -1
	psy.onCommand = func
	psy.disconnected = _disconnected
	psy.readonly = _readonly
	psy.override = _override	
	return jdb.addPycommand(psy);	

def addWatchPoint(breakpstr, func, en=True, temp=False):
	'''
	addWatchPoint(breakpstr, func, en, temp):
		Add a watchpoint for executing script.

		parameters:
			breakpstr : string of watchpoint
			func : While stopping at a breakpoint, func will be executed, 			   
				   func(bp, id, field, curVal, tobeVal, threadName): 
					   bp =>
							bp.temporary  : the temp value when executing addWatchPoint.
							bp.enable	  : the en value when executing addWatchPoint.
							bp.breakpoint : breakpstr
							bp.eventType  : "watchpoint"
					   id => Assigned id of watchpoint
					   field => The watched filed.
					   curVal => current value.
					   tobeVal => The value that the field will be changed to.
					   threadName => The name of the thread which are stopping at this breakpoint
				   
				   bp.temporay and bp.enable may be incorrect.			   
				   Because they can be changed in the code of java code.
			en 	 : The default is true.
			temporay : The default is false.
		
		return id if success. Otherwise, return -1.	
	'''
	class PyWatchPoint:
		pass
		
	bp = PyWatchPoint()
	bp.temporary = temp or False
	bp.enable = en
	bp.breakpoint = breakpstr
	bp.eventType = "watchpoint"
	bp.onBreakPoint = func
	bp.id = jdb.addBreakpoint(bp);	
	return bp.id;	
	
	
def addBreakPoint(breakpstr, func, en=True, temp=False):
	'''
	addBreakPoint(breakpstr, func, en, temp):
	    Add a breakpoint for executing script.

	    parameters:
			breakpstr : string of breakpoint
			func : While stopping at a breakpoint, func will be executed, 			   
				   func(bp, id, threadName): 
					   bp =>
							bp.temporary  : the temp value when executing addBreakPoint.
							bp.enable	  : the en value when executing addBreakPoint.
							bp.breakpoint : breakpstr
							bp.eventType  : "breakpoint"
					   id => Assigned id of breakpoint
					   threadName => The name of the thread which are stopping at this breakpoint
				   
				   bp.temporay and bp.enable may be incorrect.			   
				   Because they can be changed in the code of java code.
			en 	 : The default is true.
			temporay : The default is false.
		
		return id if success. Otherwise, return -1.	
	'''
	class PyBreakPoint:
		pass
		
	bp = PyBreakPoint()
	bp.temporary = temp or False
	bp.enable = en
	bp.breakpoint = breakpstr
	bp.eventType = "breakpoint"
	bp.onBreakPoint = func
	bp.id = jdb.addBreakpoint(bp);	
	return bp.id;	
	
def getBreakPointId(id):
	brkps=BreakPoints();
	for brkp in brkps.breakpointslist:
		if brkp['id']==id:
			return brkp
	
	return None
	
	
def addBreakPointId(id, func):
	brk=getBreakPointId(id);
	
	if brk==None:	
		_p('id is wrong !!')
		return -1

	brktypestr=""
	if brk['eventType']==1:
		brktypestr = "breakpoint";
	else:
		brktypestr = "watchpoint";

	class PyBreakPoint:
		pass
	
	bp = PyBreakPoint()
	bp.temporary = brk['temporary'] or False
	bp.enable = brk['enable']
	bp.breakpoint = brk['breakpoint']
	bp.eventType = brktypestr
	bp.onBreakPoint = func
	bp.id = jdb.addBreakpointScriptFunc(bp);	
	return bp.id;	
	
def getCurrentThreadName():
	return jdb.getCurrentThreadName()

def getLastBreakPointId():
	return jdb.getLastBreakPointId()

def getCurrentSourceLocation():
	return jdb.getCurrentSourceLocation()

def getCurrentLocationClassName():
	return jdb.getCurrentLocationClassName()

	
jdb.initload("py/pycmd.py") #load from class path
