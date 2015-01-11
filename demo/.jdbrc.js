var bkstring = "XJdbTest:15";
var bp = addBreakPoint(bkstring,function(bp,id,threadName)
{
	var expr=$('testStr');
	_p('javascript break:expr='+expr);
	_p('javascript break:id='+id);
	_p('javascript break:threadName='+threadName);
			
	if(expr=="abc")
		_('list');
	else
		_('cont');	
},true);

var bkstring = "XJdbTest:42";
var bp = addBreakPoint(bkstring,function(bp,id)
{
	var expr=$('testStr');				
	if(expr=="abc")
	{
		_('list')
		$('this.callA()')
		$('this.callB("script****************")')
		_p("-------------------------")
		_('p this.callA()')
		_('p this.callB("script")')
	}
	else
		_('cont');	
},true);


function stop_event(threadName)
{
	_p("[stop event] threadName is "+threadName)	
}

addStopEvent(stop_event)

addExitedEvent(function(cause)
{
	_p("exited event cause="+cause)
});
