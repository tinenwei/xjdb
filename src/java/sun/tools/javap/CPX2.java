/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */


package sun.tools.javap;

/**
 *  Stores constant pool entry information with two fields.
 *
 * @author  Sucheta Dambalkar (Adopted code from jdis)
 */
class CPX2 {
    int cpx1,cpx2;
    
    CPX2 (int cpx1, int cpx2) {
	this.cpx1=cpx1;
	this.cpx2=cpx2;
    }
}
