def nextnext(*args):
	'''
	Jump to lineno. 
	usage: 
		nextnext/nn lineno
	'''
	if len(args) <= 0:
		_p("usage: nn lineno");
		return
		
	line=args[0]	
	res=__('tb '+line)
	if res.find("No code")==-1:
		_('c');
	else:
		_p("No code in line "+line);	

addPycommand("nextnext", "nn", nextnext, COMPLETER.NONE)


def breakbreak(*args):
	'''
	Add breakpoint to current line.  
	usage: 
		breakbreak/bb 	
	'''
	location = getCurrentSourceLocation();
	if location:
		pat=r'(\d+) (.*)'
		k=re.search(pat,location)
		if k and k.group(1) and k.group(2):
			lineno=k.group(1)
			_('b '+lineno)
	
addPycommand("breakbreak", "bb", breakbreak, COMPLETER.NONE)

def breakonly(*args):
	'''
	Add breakpoint and disable the other breakpoints.
	usage:
		breakonly/bo breakpstr	
	'''
	breakline=""
	__('dis')
	for arg in args:
		breakline=breakline+" "+arg	
	
	result = __('b '+breakline)	
	
	if result.find("No code")!=-1 or result.find("Usage")!=-1 or result.find("declaringType")!=-1:
		_p(result)	
	else:
		_('b')

addPycommand("breakonly", "bo", breakonly, COMPLETER.CLASS_METHOD)

def enableonly(*args):
	'''
	Enable only one breakpoint and disable the other breakpoints
	usage:
		enableonly/eo breakpoint id	
	'''
	nums=""
	__('dis')
	for arg in args:
		nums=nums+" "+arg	
	__('en '+nums)
	_('b')
		
addPycommand("enableonly", "eo", enableonly, COMPLETER.NONE)

def monitorprint(*args):
	'''
	Mointor and print expr
	usage:
		monitorprint/mp breakpoint id	
	'''	
	expr=""
	for arg in args:
		expr=expr+" "+arg	
	_('m p '+expr)
	_('p '+expr)
	
addPycommand("monitorprint", "mp", monitorprint, COMPLETER.PRINT, False, False)