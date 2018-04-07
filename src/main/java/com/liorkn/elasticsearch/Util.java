package com.liorkn.elasticsearch;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.Base64;

/**
 * Created by Lior Knaany on 4/7/18.
 */
public class Util {

    public static final double[] convertBase64ToArray(String base64Str) {
        final byte[] decode = Base64.getDecoder().decode(base64Str.getBytes());
        final DoubleBuffer doubleBuffer = ByteBuffer.wrap(decode).asDoubleBuffer();

        final double[] dims = new double[doubleBuffer.capacity()];
        doubleBuffer.get(dims);
        return dims;
    }

    public static final String convertArrayToBase64(double[] array) {
        final int capacity = 8 * array.length;
        final ByteBuffer bb = ByteBuffer.allocate(capacity);
        for (int i = 0; i < array.length; i++) {
            bb.putDouble(array[i]);
        }
        bb.rewind();
        final ByteBuffer encodedBB = Base64.getEncoder().encode(bb);
        return new String(encodedBB.array());
    }
}
