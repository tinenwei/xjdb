/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package sun.tools.javap;

/**
 * Stores constant pool entry information with one field.
 *
 * @author  Sucheta Dambalkar (Adopted code from jdis)
 */
class CPX {
    int cpx;
    
    CPX (int cpx) {
	this.cpx=cpx;
    }
}
