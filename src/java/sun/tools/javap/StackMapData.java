/*
 * %W% %E%
 *
 * Copyright (c) 2004, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package sun.tools.javap;

import java.util.*;
import java.io.*;

import static sun.tools.javap.RuntimeConstants.*;

/* represents one entry of StackMap attribute
 */
class StackMapData {
    final int offset;
    final int[] locals;
    final int[] stack;
  
    StackMapData(int offset, int[] locals, int[] stack) {
        this.offset = offset;
        this.locals = locals;
        this.stack = stack;
    }
    
    StackMapData(DataInputStream in, MethodData method) throws IOException {
        offset = in.readUnsignedShort();
        int local_size = in.readUnsignedShort();
        locals = readTypeArray(in, local_size, method);
        int stack_size = in.readUnsignedShort();
        stack = readTypeArray(in, stack_size, method);
    }
  
    static final int[] readTypeArray(DataInputStream in, int length, MethodData method) throws IOException {
	int[] types = new int[length];
	for (int i=0; i<length; i++) {
	    types[i] = readType(in, method);
	}
	return types;
    }
  
    static final int readType(DataInputStream in, MethodData method) throws IOException {
	int type = in.readUnsignedByte();
	if (type == ITEM_Object || type == ITEM_NewObject) {
	    type = type | (in.readUnsignedShort()<<8);
        }
	return type;
    }
        
    void print(JavapPrinter p) {
        p.out.println("   " + offset + ":");
        p.printMap("    locals = [", locals);
        p.printMap("    stack = [", stack);
    }
}
