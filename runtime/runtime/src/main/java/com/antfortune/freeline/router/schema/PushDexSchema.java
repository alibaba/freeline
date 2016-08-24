package com.antfortune.freeline.router.schema;

import android.util.Log;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.router.ISchemaAction;
import com.antfortune.freeline.server.EmbedHttpServer;
import com.antfortune.freeline.server.LongLinkServer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by huangyong on 16/7/28.
 */
public class PushDexSchema implements ISchemaAction {

    private static final String TAG = "Freeline.PushDex";

    @Override
    public String getDescription() {
        return "pushDex";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        File dir = new File(FreelineCore.getDynamicInfoTempDir());
        File file = new File(dir, "dex.pending");
        File optDir = new File(dir, "opt");
        if (!optDir.exists()) {
            optDir.mkdirs();
        }
        String vmVersion = System.getProperty("java.vm.version");
        File finalFile = null;
        if (vmVersion != null && vmVersion.startsWith("2")) {
            finalFile = new File(dir, "dynamic.apk");
        } else {
            finalFile = new File(dir, "dynamic.dex");
        }
        FileOutputStream fos = new FileOutputStream(file);
        byte[] buf = new byte[4096];
        int l;
        while ((l = input.read(buf)) != -1) {
            fos.write(buf, 0, l);
        }
        fos.close();
        LongLinkServer.markDexChanged();
        response.setStatusCode(201);
        boolean rst = file.renameTo(finalFile);
        //FreelineCore.applyDynamicDex(finalFile.getAbsolutePath(), optDir.getAbsolutePath());
        LongLinkServer.setDynamicDexPath(finalFile.getAbsolutePath());
        LongLinkServer.setOptDirPath(optDir.getAbsolutePath());
        Log.d(TAG, new StringBuilder().append("dex file received (").append(finalFile.length()).append(" bytes)").toString() + " rename rst :" + rst);
    }
}
