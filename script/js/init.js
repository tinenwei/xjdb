/*
$('expr')	: return the result of executing java expression
_('cmd')  	: execute jdb command and print result in console
__('cmd') 	: execute jdb command and return the string of result.
_c('cmd')	: execute jdb command.
		  If command is defined in script, then pass script command 
		  and execute the internal command.
__c('cmd')  : same as _c('cmd') but return the string of result.	
_p('str')	: print string with newline
__p('str') 	: print string without newline 
load('script_file_path') : load other script file.
syscmd('cmd') 	: execute system command and return the string of result.
*/

/*
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
		 stf.getById(id)    	=> get frame by id
		 stf.get(index)	 	=> get frame by index 
		 stf.size() 		=> frameslist size
	 
	 
	 stf[index] is equal to stf.frameslist[index] 
	 Therefore, you can also use :
	 stf[index].id, stf[index].location and stf[index].file
 */

function StackFrames(){
	this.frameslist=[];
	//var l=jdb.cmdret('bt');
	var l=__c('bt');
	var lines=l.split("\n");
	var i=0;
	var j=0;
	for(i=0; i<lines.length;i++)
	{	
		var re=/  \[(\d+)\] (.*) \((.*)\).*/;
		var k=lines[i].match(re);
		if(k && k[1] && k[2] && k[3] )
		{
			var raRegExp = new RegExp(",","g");
			k[3] = k[3].replace(raRegExp,""); // line num may contains ","

			var o = { id: parseInt(k[1]), 
					  location: k[2],
					  file: k[3], 
					  toString: function(){
						return "  ["+this.id+"] "+this.method+" "+this.file;
					  }
					  
					};
						  
			this.frameslist.push(o);
			this[j]=o;
			j++;
		}
	}
	
}


StackFrames.prototype.getById=function(id){
	if(!this.frameslist)
		return null;
	for(var i=0; i<this.frameslist.length; i++)
	{
		if(this.frameslist[i].id==id)
		{
			return this.frameslist[i];
		}
	}
	return null;
};

StackFrames.prototype.get=function(index){
	if(!this.frameslist)
		return null;
	return this.frameslist[index];
};

StackFrames.prototype.size=function(){
	if(!this.frameslist)
		return 0;
	return this.frameslist.length;
};


/*
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
		bps.getById(id)   	 => get breakpoints by id
		bps.get(index)	 	 => get breakpoints by index 
		bps.size() 		 => breakpoints size
	
	bps[index] is equal to bps.breakpointslist[index]
	Therefore, you can also use :
	bps[index].temporary, bps[index].enable, etc.
*/

function BreakPoints(){
		this.breakpointslist=[];		
		
		var l=__c('b');
		var lines=l.split("\n");
		var i=0;
		var j=0;

		for(i=0; i<lines.length;i++)
		{
			var re;
			var eventType;
			if( lines[i].match("watch accesses of"))
			{
				re=/^\t\[\s*(\d+)\] ([t ]) ([ed]) ([yn]) ([ asc]{2}) watch accesses of (.*)\(hits: (\d+)\)/;
				eventType = 3;
			}
			else if(lines[i].match("watch modification of"))
			{
				re=/^\t\[\s*(\d+)\] ([t ]) ([ed]) ([yn]) ([ asc]{2}) watch modification of (.*)\(hits: (\d+)\)/;
				eventType = 2;
			}
			else if(lines[i].match("breakpoint"))
			{
				re=/^\t\[\s*(\d+)\] ([t ]) ([ed]) ([yn]) ([ asc]{2}) breakpoint (.*)\(hits: (\d+)\)/;
				eventType = 1;
			}
			
			var bre=/(.*) {(.*)}/;			
			var k=lines[i].match(re);
			var breakp = null;
			var cond = null;
			var hits = null;					
			
			if(k && k[6] && k[7])
			{				
				hits=k[7];	
				if(k[6].match("(.*) {(.*)}"))
				{
					var bb=k[6].match(bre);
					
					if(bb && bb[1] && bb[2])
					{
						breakp=bb[1];						
						cond=bb[2];											
					}else
					{					
						breakp=k[6];						
					}				
				}else
				{		
						breakp = k[6].trim();					
				}				
			}
			
			if(k && k[1] && k[2] && k[3])
			{		
				var o = { id: parseInt(k[1]), 
						  temporary: (k[2]=="t"),
						  enable: (k[3]=="e"),
						  resolve: (k[3]=="y"),
						  cmdstatus: k[5],
						  breakpoint: breakp,
						  eventType: eventType,
						  condition: cond,
						  hits: hits,
						  toString: function(){
							return 	"["+this.id+"] "+this.temporary+" "+this.enable+" "+this.resolve+" "
									+this.cmdstatus+" "+this.eventType+" "+this.breakpoint+(this.condition ? " {" + this.condition + "}" : "");
						  },	
						  set: function()
						  {
							if(this.eventType==1)
								__('b '+this.breakpoint+(this.condition ? " {" + this.condition + "}" : ""));
							else
								__('wa '+this.breakpoint+(this.condition ? " {" + this.condition + "}" : ""));
								
							if(this.temporary)
								__('temporary !!');
								
							if(!this.enable)
								__('disable !!');
								
							
						  }
						};
						  
				this.breakpointslist.push(o);				
				this[j]=o;
				j++;
			}
		
		}
}

BreakPoints.prototype.getById=function(id){
	if(!this.breakpointslist)
		return null;
	for(var i=0; i<this.breakpoints.length; i++)
	{
		if(this.breakpointslist[i].id==id)
		{
			return this.breakpointslist[i];
		}
	}
	return null;
};

BreakPoints.prototype.get=function(index){
	if(!this.breakpointslist)
		return null;
	return this.breakpointslist[index];
};

BreakPoints.prototype.size=function(){
	if(!this.breakpointslist)
		return 0;
	return this.breakpointslist.length;
};

BreakPoints.prototype.saveEnables=function(){
	this.enables=[];
	if(!this.breakpointslist)
		return null;		

	var brkThis=this;
	
	this.breakpointslist.forEach(function(brk){
			brkThis.enables.push(brk['enable']);	
	});
	
	
	return this.enables;
};

BreakPoints.prototype.restoreEnables=function(){
	
	if(!this.breakpointslist || !this.enables)
		return null;
		
	var i=0;
	for(i=0;i<this.breakpointslist.length;i++)
	{
		//this.enables.push(brk['enable']);
		this.breakpointslist[i]['enable'] = this.enables[i];
		if(this.enables[i])
			__('en '+ this.breakpointslist[i]['id']);
		else
			__('dis '+ this.breakpointslist[i]['id']);
	}
	
	return this.enables;
};

/*
Location:
	This is valid when stopping at a breakpoint.
	Use Location to get the current location.
	ex:
		var loc = new Location()  => get the current Location
		loc.thread 
		loc.func   
		loc.line
		loc.bci
*/

function Location()
{
	var loc=jdb.location;
	var re=/"thread=(.*)", (.*) line=(\d+) bci=(\d+).*/;
	var k=loc.match(re);
	
	if(k && k[1] && k[2] && k[3] && k[4])
	{
		this.thread = k[1];
		this.func = k[2];
		this.line = parseInt(k[3]);
		this.bci = parseInt(k[4]);
	}
}

/*
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
*/

function addBreakPoint(breakpstr, func, en, temp)
{

	var bp={
		temporary : temp || false,
		//enable: en || true,	
		enable: en ,	
		breakpoint: breakpstr,
		eventType: "breakpoint",
		onBreakPoint: function(id,threadName)
		{
			if(func)				
				func(bp,id,threadName);
		}
	}	
	
	if(typeof(en)=="undefined")
		bp.enable=true;
	else
		bp.enable=en;
	
	
	bp.id = jdb.addBreakpoint(bp);	
	return bp.id;	
}

function getBreakPointId(id)
{
	var brkps=new BreakPoints();
	var i=0;

	for(i=0;i<brkps.breakpointslist.length;i++)
	{		
		if(id==brkps.breakpointslist[i]['id'])
		{
			return brkps.breakpointslist[i];
		}
	}

	return null;
}

function addBreakPointId(id, func)
{
	var brk=getBreakPointId(id);
	
	if(brk==null)
	{
		_p('id is wrong !!');
		return -1;
	}	

	var brktypestr;
	if(brk['eventType']==1)
		brktypestr = "breakpoint";
	else
		brktypestr = "watchpoint";

	var bp={
		temporary : brk['temporary'] || false,		
		enable: brk['enable'] ,	
		breakpoint: brk['breakpoint'] ,
		eventType: brktypestr,
		onBreakPoint: function(id)
		{
			if(func)				
				func(bp,id);
		}
	}		
	
	bp.id = jdb.addBreakpointScriptFunc(id, bp);	
	return bp.id;	
}


/*
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
*/

function addWatchPoint(breakpstr, func, en, temp)
{
	var bp={
		temporary : temp || false,
		enable: en ,//|| true,	
		breakpoint: breakpstr,
		eventType: "watchpoint",
		onBreakPoint: function(id,field,curVal, tobeVal,threadName)
		{
			if(func)				
				func(bp,id,field,curVal,tobeVal,threadName)
		}
	}
	
	
	if(typeof(en)=="undefined")
		bp.enable=true;
	else
		bp.enable=en;
	
	
	bp.id = jdb.addBreakpoint(bp);	
	return bp.id;	
}



function getRegisterEventObject(func)
{
	var jse = {
		onEvent: function()
		{
			var args=[];
			for(var i = 0; i < arguments.length; i++) {				
				args.push(arguments[i])
			}
			if(func)			
				return func.apply(this,args);
		},
		funcObj:func
	}

	return jse;
	
}

/*
addStopEvent(func):
	func: While program is interrupted (breakpoint, step event etc), func will be executed,  
		  func(threadName)
		  	threadName => The name of the thread which are being interrupted.	

	return 0 if success. Otherwise, return -1.
*/

function addStopEvent(func)
{
	var jse=getRegisterEventObject(func)
	var result = jdb.addStopEvent(jse);	
	return result;	
}

/*
removeStopEvent(func):
	func: This must be the same as the func of addStopEvent.
*/

function removeStopEvent(func)
{
	var result = jdb.removeStopEvent(func);	
	return result;	
}

/*
addExitedEvent(func):	
	func: While program exits (quit, program normally or abnormally exited), func will be executed,  
		  func(cause)
		  	cause => cause=1 , for using 'quit' command to exit
		  			 cause=0 , Otherwise.
	return 0 if success. Otherwise, return -1. 
*/

function addExitedEvent(func)
{
	var jse=getRegisterEventObject(func)
	var result = jdb.addExitedEvent(jse);	
	return result;	
}

/*
removeExitedEvent(func);
	func: This must be the same as the func of addExitedEvent.
*/

function removeExitedEvent(func)
{
	var result = jdb.removeExitedEvent(func);	
	return result;	
}

// addContinueEvent is not implemented.
function addContinueEvent(func)
{
	var jse=getRegisterEventObject(func)
	var result = jdb.addContinueEvent(jse);	
	return result;	
}

function removeContinueEvent(func)
{
	var result = jdb.removeContinueEvent(func);	
	return result;	
}
// ~addContinueEvent is not implemented.


var COMPLETER = {
  "NONE" : -1,
  "FILES" : 1,
  "CLASS_METHOD" : 2,
  "CLASS" : 3,
  "PRINT" : 4,
  "THREADS" : 5 
}


/*
addJscommand(cmdstr, cmdabbrevstr, func, _completer, 
				_override, _disconnected, _readonly):
	Add a new script command. you can type cmdstr or cmdabbrevstr in command line of xjdb.

	parameters:
		cmdstr : the complete name of the command.
		cmdabbrevstr : the abbreviation of the command.
		func :  when type cmdstr or cmdabbrevstr in command line of jdb.
				The arguments is collected behind the cmdstr or cmdabbrevstr.
		
		_completer: One of the following values. The default is -1.
				  COMPLETER.NONE = -1;
				  COMPLETER.FILES = 1;
				  COMPLETER.CLASS_METHOD = 2;
				  COMPLETER.CLASS = 3;
				  COMPLETER.PRINT = 4;
				  COMPLETER.THREADS = 5;
				  
		_override: to override any existed command. 
				   The default is false.
		_disconnected: The command can be used when VM is disconnected. 
				   The default is true.
		_readonly : The command is read-only command for a read-only VM connection. 
				   The default is true; 

	return 0 if success. Otherwise, return -1.  
*/				   

function addJscommand(cmdstr, cmdabbrevstr, func, _completer, _override, _disconnected, _readonly)
{
	var jsc ={
		cmd : cmdstr || "",
		cmdabbrev: cmdabbrevstr || "", 	
		//disconnected: _disconnected || true,
		//readonly: _readonly || false,
		completer: _completer || -1,		
		onCommand: function()
		{			
			var args=[];
			for(var i = 0; i < arguments.length; i++) {				
				args.push(arguments[i])
			}
			if(func)			
				return func.apply(this,args);
			
		}
	}
	
	if(typeof(_override)=="undefined")
		jsc.override=false;
	else
		jsc.override=_override;
	
	if(typeof(_disconnected)=="undefined")
		jsc.disconnected=true;
	else
		jsc.disconnected=_disconnected;

	if(typeof(_readonly)=="undefined")
		jsc.readonly=true;
	else
		jsc.readonly=_readonly;
		
	var result = jdb.addJscommand(jsc);	
	return result;	
}

function readStdIn()
{
	 var stdin = new java.io.BufferedReader( new java.io.InputStreamReader(java.lang.System['in']) )
	 return String(stdin.readLine());
}

function getCurrentThreadName()
{
	return jdb.currentThreadName;
}

function getLastBreakPointId()
{
	return jdb.lastBreakPointId;
}

function getCurrentSourceLocation()
{
	return jdb.currentSourceLocation;
}

function getCurrentLocationClassName()
{
	return jdb.currentLocationClassName;
}

function getClassLocations(classid,lineno)
{

	if(!classid)
		return "";
	if(!lineno)
		lineno = -1;

	return jdb.getClassLocations(classid,lineno);
}

function isInt(x) {
  var y=parseInt(x);
  if (isNaN(y)) return false;
  return x==y && x.toString()==y.toString();
}


jdb.initload("js/jscmd.js"); // load from class path
//jdb.load("some.js"); // load from file path

