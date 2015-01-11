/* 
	Jump to lineno. 
	usage: 
		nextnext/nn lineno
*/
addJscommand("nextnext", "nn", function(line){
	if(!line)
	{
		_p("usage: nn lineno");
		return
	}
	var res =__('tb '+line);
	if(!res.match("No code"))	
		_('c');
	else
		_p("No code in line "+line);
}, COMPLETER.NONE);

/* 
	Add breakpoint to current line.  
	usage: 
		breakbreak/bb 
*/
addJscommand("breakbreak", "bb", function(){
	var location = getCurrentSourceLocation();
	if(location)
	{
	
		var re=/(\d+) (.*)/;
		var k=location.match(re);
		var lineno;
		var filepath;
		
		if(k && k[1]&&k[2])
		{
			lineno = k[1];
			_('b '+lineno);
		}
		
	}	 
}, COMPLETER.NONE); 

/*
	Add breakpoint and disable the other breakpoints.
	usage:
		breakonly/bo breakpstr
*/
addJscommand("breakonly", "bo", function(){
	var breakline=""
	__('dis');	
	for(var i = 0; i < arguments.length; i++) 
		breakline=breakline+" "+arguments[i];
	var result = __('b '+breakline);	
	if(result.match("No code") || result.match("Usage") || result.match("declaringType") )
		_p(result);
	else
		_('b');	
}, COMPLETER.CLASS_METHOD); 

/*
	Enable only one breakpoint and disable the other breakpoints
	usage:
		enableonly/eo breakpoint id
*/
addJscommand("enableonly", "eo", function(){
	var nums=""
	__('dis');
	for(var i = 0; i < arguments.length; i++) 
		nums=nums+" "+arguments[i];
	__('en '+nums);	
	_('b');	
	
	
}, COMPLETER.NONE);


/*
	Mointor and print expr
	usage:
		monitorprint/mp breakpoint id
*/
addJscommand("monitorprint", "mp", function(){
	var expr=""	
	for(var i = 0; i < arguments.length; i++) 
		expr=expr+" "+arguments[i];
	_('m p '+expr);
	_('p '+expr);	
}, COMPLETER.PRINT,false,false);