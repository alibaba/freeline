package com.antfortune.freeline.router.schema;

import android.util.Log;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.router.ISchemaAction;
import com.antfortune.freeline.server.EmbedHttpServer;
import com.antfortune.freeline.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by huangyong on 16/7/28.
 */
public class PushFullResourcePackSchema implements ISchemaAction {

    private static final String TAG = "Freeline.PushFullRes";

    @Override
    public String getDescription() {
        return "pushFullResourcePack";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        Log.i(TAG, "receive full res pack: " + path);
        String dst = FreelineCore.getBundleFilePathByPackageId("base-res");
        File dstFile = new File(dst);
        Log.i(TAG, "dst path: " + dstFile.getAbsolutePath());
        File pending = new File(dst + ".bak");
        try {
            if (!pending.exists()) {
                pending.createNewFile();
            }
            FileOutputStream fos = new FileOutputStream(pending);
            byte[] buf = new byte[4096];
            int l;
            while ((l = input.read(buf)) != -1) {
                fos.write(buf, 0, l);
            }
            fos.close();
        } catch (Exception e) {
            Log.e(TAG, "read full resource failed");
            Log.d(TAG, e.getStackTrace().toString());
            response.setStatusCode(500);
            return;
        }
        if (dstFile.exists()) {
            FileUtils.rm(dstFile);
        }
        pending.renameTo(dstFile);
        Log.i(TAG, "receive full res pack successfully");
        response.setStatusCode(201);
    }
}
