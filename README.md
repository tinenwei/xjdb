What is xjdb?
=============
xjdb is a extension of jdb and the source code is a modification of jdb's source code. 
The objective is to make jdb more convenient as gdb. 
xjdb added new commands and enhanced the commands of jdb. 
xjdb uses other open source projects, 
including libreadline-java-0.8.0, Rhino 1.7R4 javascript engine and jython-2.5.3.
Therefore, you can use readline in command prompt and 
scripts (javascript or jython) in xjdb.
For now, xjdb is developed in linux. In the future, it will also target in windows.


Install
=============
Install prerequisite for linux : libreadline6-dev packages.  
You must set the JAVA_HOME environment variable 
to point to the JDK installation directory 
or modify the JAVA_HOME of Makefile.

The command for installing is :

    make install

The default installed directory is "/usr/lib/xjdb".
You can set prefix for your desired path: 

    make install prefix=<path>. 

Then you can use &lt;path&gt;/xjdb instead of jdb.

The default script engine is javascript.
For jython, you can use command: 

    make install SCRIPT_LANGUAGE=jython

or modify the SCRIPT_LANGUAGE of Makefile.
You can use following commands to do testing:

    make demo
    make test

How to use?
=============
The usage of new commands can refer to commands.txt or type helpx in command line of xjdb.
The usage of script can refer to scriptAPI.txt.
Also, You can refer to ./script/js/init.js and ./script/js/jscmd.js for javascript 
or ./script/py/init.py and ./script/py/pycmd.py for jython.

The startup script is ".jdbrc.js" for javascript
and ".jdbrc.py" for jython. They can be placed in user.home or user.dir.


The verification of Breakpoints 
=============
When you use "break classId:num" command, xjdb take four steps 
to verify if the line number in ClassId can be inserted a breakpoint 
(including its inner or anonymous classes):

1. Verify if line number is available in the class. (jdb only uses this step)
2. Verify if line number is available 
   in the loaded inner or anonymous classes.
3. Load inner or anonymous classes that have never been loaded
   to verify if line number is available.
   (This executes java.lang.Class.forName() but it may not work.)
4. Search in classpath to parse the files of inner or anonymous classes 
   to verify if line number is available.

For customed classloader, you can use "-classpath" 
to let xjdb parse the desired classes in step 4.
For remote debugging, for example, the java debugging of android, 
"-classpath" can't be assigned, you can use "-xclasspath" in xjdb.  
ex:

    "xjdb -xclasspath <path>/classes.jar ... " for android


The xjdb client
=============
In ./client directory, it implemented xjdb client that can send break string to 
xjdb socket server at port 6666.
It contains the projects of eclipse plugin, vim and sublime. 
You may use ASTParser.jar to implement your client.

