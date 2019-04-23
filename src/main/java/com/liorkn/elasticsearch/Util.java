package com.liorkn.elasticsearch;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Base64;

/**
 * Created by Lior Knaany on 4/7/18.
 */
public class Util {

    public static float[] convertBase64ToArray(String base64Str) {
        final byte[] decode = Base64.getDecoder().decode(base64Str.getBytes());
        final FloatBuffer floatBuffer = ByteBuffer.wrap(decode).asFloatBuffer();
        final float[] dims = new float[floatBuffer.capacity()];
        floatBuffer.get(dims);

        return dims;
    }

    public static String convertArrayToBase64(float[] array) {
        final int capacity = Float.BYTES * array.length;
        final ByteBuffer bb = ByteBuffer.allocate(capacity);
        for (double v : array) {
            bb.putFloat((float) v);
        }
        bb.rewind();
        final ByteBuffer encodedBB = Base64.getEncoder().encode(bb);

        return new String(encodedBB.array());
    }
}
