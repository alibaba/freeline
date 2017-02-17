package com.antfortune.freeline.idea.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by pengwei on 16/9/11.
 */
public class StreamUtil {

    /**
     * string 转 InputStream
     * @param text
     * @return
     */
    public static final InputStream string2InputStream(String text) {
        return new ByteArrayInputStream(text.getBytes());
    }

    /**
     * InputStream 转 byte[]
     * @param inStream
     * @return
     * @throws IOException
     */
    public static final byte[] inputStream2byte(InputStream inStream) throws IOException {
        ByteArrayOutputStream swapStream = new ByteArrayOutputStream();
        byte[] buff = new byte[100];
        int rc = 0;
        while ((rc = inStream.read(buff, 0, 100)) > 0) {
            swapStream.write(buff, 0, rc);
        }
        byte[] in2b = swapStream.toByteArray();
        return in2b;
    }

    /**
     * InputStream 转 string
     * @param inStream
     * @return
     * @throws IOException
     */
    public static final String inputStream2String(InputStream inStream) throws IOException {
        return new String(inputStream2byte(inStream));
    }
}
