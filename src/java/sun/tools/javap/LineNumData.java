/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package sun.tools.javap;

import java.util.*;
import java.io.*;

/**
 * Strores LineNumberTable data information.
 *
 * @author  Sucheta Dambalkar (Adopted code from jdis)
 */
class LineNumData {
    short start_pc, line_number;
    
    public LineNumData() {}
    
    /**
     * Read LineNumberTable attribute.
     */
    public LineNumData(DataInputStream in) throws IOException {
	start_pc = in.readShort();
	line_number=in.readShort();
	
    }
}

