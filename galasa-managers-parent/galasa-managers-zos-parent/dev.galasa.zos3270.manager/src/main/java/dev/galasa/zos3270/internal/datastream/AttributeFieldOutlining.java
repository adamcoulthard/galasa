/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2019.
 */
package dev.galasa.zos3270.internal.datastream;

import java.nio.ByteBuffer;

public class AttributeFieldOutlining implements IAttribute {

    public static final byte ATTRIBUTE_ID = -62;

    private final byte       validation;

    public AttributeFieldOutlining(ByteBuffer buffer) {
        this.validation = buffer.get();
    }
    
    public byte getValidation() {
        return validation;
    }

}
