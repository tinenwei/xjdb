package com.sun.tools.example.debug.tty;

public class BreakCondition {
    private String condition = null;
    private String expression = null;
    private String command = null;
    private String threadName = null;  //{thread name} {if  condition@threadName} { p var@threadName}
    static private String THREAD_SPLIT = "@";
    BreakCondition(String condition) {
        this.condition = condition;        
        parse();        
    }
    
    public void parse() {
        String cond = condition.trim();
        int index = cond.indexOf(" ");
        String cmd = null;
        String expr = null;
        
        if (index != -1) {
            cmd = cond.substring(0,index).trim();
            expr = cond.substring(index + 1).trim();            
        }
        
        if (cmd.length() > 0)
            command = cmd;            
        if (expr.length() > 0) {
            int tindex = expr.indexOf(THREAD_SPLIT);
            if (tindex != -1) {
                expression = expr.substring(0,tindex).trim();
                threadName = expr.substring(tindex + 1).trim();    
            } else {
                expression = expr;                
            }
        }
        
    }
    
    public String toString() {
        return condition;
    }

    public String expr() {
        return expression;
    }

    public String cmd() {
        if (command != null && command.equals("p"))
            return "display";
        if (command != null && command.equals("s"))
            return "script";            
        else
            return command;
    }

    public String threadname() {
        return threadName;
    }

    static boolean validate(String condition) {
        String cond = condition.trim();
        int index = cond.indexOf(" ");
        String cmd = null;
        String expr = null;
        if (index != -1) {
            cmd = cond.substring(0,index).trim();
            expr=cond.substring(index + 1).trim();

            if (cmd.equals("if") ||cmd.equals("display") || cmd.equals("p") || cmd.equals("script")
            || cmd.equals("s") || cmd.equals("thread") || cmd.equals("bt") || cmd.equals("count")) {
                if(cmd.equals("bt")) {
                    String[] bt_expr= expr.split(" ");
                    if(bt_expr.length>=1 || bt_expr.length<=2) {
                        try {
                            Integer.parseInt(bt_expr[0]);
                            }catch(NumberFormatException exc) {
                                return false;
                            }
                    } else {
                        return false;
                    }
                    return true;
                }  

                if (cmd.equals("count") && expr.length() > 0) {
                    try {
                        Integer.parseInt(expr);
                    }catch(NumberFormatException exc) {
                        return false;
                    }

                }
                
                if (expr.length()>0) {
                    int tindex = expr.indexOf(THREAD_SPLIT);
                    if (tindex != -1) {
                        String threadName=expr.substring(tindex+1).trim();
                        if (threadName.length() > 0)
                            return true;
                    } else
                        return true;
                }
            }            
        }
        return false;
    }

    static void println(String condition) {
        String cond = condition.trim();
        int index = cond.indexOf(" ");
        String breakcmd = null;
        String breakexpr = null;
        String threadName = null;
        
        if (index != -1) {
            breakcmd = cond.substring(0,index).trim();
            breakexpr = cond.substring(index + 1).trim();
            
            int tindex = breakexpr.indexOf(THREAD_SPLIT);
            
            if (tindex != -1) {
                threadName = breakexpr.substring(tindex + 1).trim();
                breakexpr = breakexpr.substring(0,tindex).trim();                    
            }
            
            String expr_thread = "";

            if (threadName != null)
                expr_thread = breakexpr+" at thread \"" + threadName + "\"";

            if (breakcmd.equals("if"))
                System.out.println("break if " + breakexpr + expr_thread);
            else if (breakcmd.equals("display") || breakcmd.equals("p"))
                System.out.println("break to display " + breakexpr + expr_thread);
            else if (breakcmd.equals("script") || breakcmd.equals("s"))
                System.out.println("break to execute script: " + breakexpr + expr_thread);
            else if (breakcmd.equals("thread"))
                System.out.println("break at thread: " + breakexpr);
            else if (breakcmd.equals("bt"))
                System.out.println("break to dispay backtrace (depth=" + breakexpr +")" + expr_thread);
            else if (breakcmd.equals("count"))
                System.out.println("break until the count of hits is reached to " + breakexpr + expr_thread);
        }
    }
}
