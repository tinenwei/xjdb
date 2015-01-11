import java.util.*;

public class XJdbTest {
    XJdbTest()
    {
        System.out.println("XJdbTest");
    }
    
    void TestScriptBreak() {
        String testStr="abc";        // stop at XJdbTest:10
        int testInt=0;
        boolean testBool=true;

        int add_statement = 0;
        add_statement=add_statement+1; // break for script to show testStr
        add_statement=add_statement+1;
        add_statement=add_statement+1;
        add_statement=add_statement+1;
        
    }

    void TestPreCommand() {
        String testStr="abc";    // stop at XJdbTest:23
        int testInt=0;
        boolean testBool=true;

        int add_statement = 0;
        add_statement=add_statement+1; // stop at XJdbTest:28 {p "precommand:"+testStr}
        add_statement=add_statement+1; // stop at XJdbTest:29 {if testStr=="abc"}
        add_statement=add_statement+1; 
        add_statement=add_statement+1; 
        
    }

    void TestCallFunction() {
        String testStr="abc";    // stop at XJdbTest:36
        int testInt=0;
        boolean testBool=true;

        int add_statement = 0;
        add_statement=add_statement+1;
        add_statement=add_statement+1; // stop for call callA() callB("script") in script
        add_statement=add_statement+1; 
        add_statement=add_statement+1;

        String testNull = null;

        add_statement=add_statement+1; // stop at XJdbTest:48 {if testNull==null}
        
    }

    String callA() {
        System.out.println("inside call: callA");
        return "retrun string:callA";
    }

    String callB(String s) {
        System.out.println("inside call: "+s);
        return "retrun string:"+s;
    }

    void exception() {
        String e=null;
        int a = e.length();
    }
    
    public static void main(String[] args) {
        System.out.println("hello world!!");
        XJdbTest test = new XJdbTest();
        test.TestScriptBreak();
        test.TestPreCommand();
        test.TestCallFunction();    
        //test.exception();
    }


}
