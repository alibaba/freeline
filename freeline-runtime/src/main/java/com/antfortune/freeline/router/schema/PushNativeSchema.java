package com.antfortune.freeline.router.schema;

import android.util.Log;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.router.ISchemaAction;
import com.antfortune.freeline.server.EmbedHttpServer;
import com.antfortune.freeline.server.LongLinkServer;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Created by huangyong on 16/7/28.
 */
public class PushNativeSchema implements ISchemaAction {

    private static final String TAG = "Freeline.pushNative";

    @Override
    public String getDescription() {
        return "pushNative";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        Log.i(TAG, "method " + method + ", " + path + headers + queries);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(input));
        try {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int count;
                while ((count = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, count);
                }
                String filename = ze.getName().substring(ze.getName().lastIndexOf(File.separator) + 1);
                byte[] bytes = baos.toByteArray();
                File dir = new File(FreelineCore.getDynamicNativeDir());
                File file = new File(dir, filename);
                if (file.exists()) file.delete();
                file.createNewFile();
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                fileOutputStream.write(bytes);
                fileOutputStream.close();
                Log.d(TAG, new StringBuilder().append("native file ").append(filename).append("received (").append(file.length()).append(" bytes)").toString());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            zis.close();
        }
        LongLinkServer.markNativeChanged();
        response.setStatusCode(201);
    }
}
