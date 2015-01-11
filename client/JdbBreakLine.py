import sublime, sublime_plugin
import os
import subprocess #as sub
# -------------------------------------------
# You will need to create a key mapping for this, something like:
# { "keys": ["f3"], "command": "jdb_break_line" }
# -------------------------------------------
class JdbBreakLineCommand(sublime_plugin.WindowCommand):
    def run(self):
        view = self.window.active_view();
        path = view.file_name();
        pos =  view.sel()[0];
        line, column = view.rowcol(pos.begin());        
        cmd = 'java -jar D:\\batch\\ASTParser.jar ' + path + ' ' + str(line+1);
        #print("cmd="+cmd);
        p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE) 

        for line in p.stdout.readlines():
            brkpoint = line.decode('utf8').strip();
   
        cmd = 'java -classpath /usr/lib/xjdb CommandClient localhost 6666 b '+ brkpoint;
        print("cmd="+cmd);
        p = sub.Popen(cmd,stdout=sub.PIPE,stderr=sub.PIPE);
        

