* For vim:
1. copy -f taglist.vim to ~/.vim/plugin/taglist.vim 
2. copy CommandClient.class to your xjdb directory
3. modify line number of 3773 in taglist.vim 
    let send_cmd="java -classpath /usr/lib/xjdb CommandClient"
    to 
    let send_cmd="java -classpath <your path>/xjdb CommandClient"

4. add 'noremap <F3> :TlistSetBreakPoint<CR>' to ~/.vimrc
5. use F3 to send break string

*For sublime:
1. copy CommandClient.class to your xjdb directory
2. modify line number of 21 in JdbBreakLine.py
  cmd = 'java -classpath /usr/lib/xjdb CommandClient localhost 6666 b '+ brkpoint;
  to 
  cmd = 'java -classpath <your path>/xjdb CommandClient localhost 6666 b '+ brkpoint;
3. add JdbBreakLine.py to the plugin path of sublime.

*For eclipse :
EclipseXjdb.tar.bz2 is a eclipse plugin project for xjdb
and it export a plugins:com.tnw.xjdb_1.0.0.201501071841.jar.

