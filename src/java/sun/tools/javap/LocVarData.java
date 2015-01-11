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
 * Strores LocalVariableTable data information.
 *
 * @author  Sucheta Dambalkar (Adopted code from jdis)
 */
class LocVarData {
    short start_pc, length, name_cpx, sig_cpx, slot;
    
    public LocVarData() {
    }
    
    /**
     * Read LocalVariableTable attribute.
     */
    public LocVarData(DataInputStream in) throws IOException {
	start_pc = in.readShort();
	length=in.readShort();
	name_cpx=in.readShort();
	sig_cpx=in.readShort();
	slot=in.readShort();
	
    }
}

