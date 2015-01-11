import sys
def breakfunc(*args):
	id=args[0]
	threadName=args[1]
	
	expr=e('testStr')
	print "jython break:testStr="+ expr
	print "jython break:id="+ str(id)
	print "jython break:threadName="+ threadName
	if expr=="abc":
		_('list')
	else:
		_('cont')
	
addBreakPoint("XJdbTest:15",breakfunc, True)

def breakfunc(*args):
	id=args[0]
	
	expr=e('testStr')
	print "jython break:testStr="+ expr
	if expr=="abc":
		_('list')
		e('this.callA()')
		e('this.callB("script")')
		_p("-------------------------")
		_('p this.callA()')
		_('p this.callB("script")')
	else:
		_('cont')
	
addBreakPoint("XJdbTest:42",breakfunc, True)

def stopEvent(threadName):
	_p("[stop event] threadName is "+threadName)


addStopEvent(stopEvent)

def exitedEvent(cause):
	_p("exited event cause="+str(cause))

addExitedEvent(exitedEvent)
