package sun.tools.javap;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Vector;


public class ClassParser {
	public static void main(String[] args) {
	
		System.out.println("hello world!!");
		try {
			File file = new File(args[0]);
			InputStream is= new FileInputStream(file);
			ClassData cls =  new ClassData(is);
			MethodData[] methods = cls.getMethods();
			for(int m = 0; m < methods.length; m++){
			       MethodData method = methods[m];

			 	if((method.getName()).equals("<init>")){
				    System.out.print(cls.getClassName().replace('/','.'));
				    System.out.println(method.getParameters());
				}else if((method.getName()).equals("<clinit>")){
				    System.out.println("{}");
				}else{
				    System.out.print(method.getReturnType()+" ");
				    System.out.print(method.getName());
				    System.out.println(method.getParameters());
				}

				int numlines = method.getnumlines();
				Vector lin_num_tb = method.getlin_num_tb();
				if( lin_num_tb.size() > 0){
					System.out.println("  LineNumberTable: ");
					for (int i=0; i<numlines; i++) {
					LineNumData linnumtb_entry=(LineNumData)lin_num_tb.elementAt(i);
					System.out.println("	line " + linnumtb_entry.line_number + ": " 
							   + linnumtb_entry.start_pc);
					}
				}
				System.out.println("");



				
			}

		}catch(Exception ex){
			ex.printStackTrace();
		}

	}

	public static boolean matchClassMethodLines(String classFile, int lineno)
	{
		InputStream is = null;
		try {
			File file = new File(classFile);
			is= new FileInputStream(file);
		}catch(Exception ex) {
			ex.printStackTrace();
			return false;
		}

		return matchClassMethodLines(is, lineno);
	}

	public static boolean matchClassMethodLines(InputStream is, int lineno)
	{
		try {
			ClassData cls =  new ClassData(is);
			MethodData[] methods = cls.getMethods();
			for(int m = 0; m < methods.length; m++){
			       MethodData method = methods[m];
				int numlines = method.getnumlines();
				Vector lin_num_tb = method.getlin_num_tb();
				if( lin_num_tb.size() > 0){
					for (int i=0; i<numlines; i++) {
						LineNumData linnumtb_entry=(LineNumData)lin_num_tb.elementAt(i);
						if(linnumtb_entry.line_number==lineno)
							return true;					
					}
				}
				
			}			

		}catch(Exception ex){
			ex.printStackTrace();
		}
		return false;
	}
}

