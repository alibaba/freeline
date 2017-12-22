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
        String dexName = queries.get("dexName");
        File dexDir = new File(FreelineCore.getDynamicDexDir());
        File file = new File(dexDir, dexName + ".pending");
        File dir = new File(FreelineCore.getDynamicInfoTempDir());
        File optDir = new File(dir, "opt");
        if (!optDir.exists()) {
            optDir.mkdirs();
        }
        String vmVersion = System.getProperty("java.vm.version");
        File finalFile = null;
        if (vmVersion != null && vmVersion.startsWith("2")) {
            finalFile = new File(dexDir, dexName + ".apk");
        } else {
            finalFile = new File(dexDir, dexName + ".dex");
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
        LongLinkServer.setDynamicDexPath(dexDir.getAbsolutePath());
        LongLinkServer.setOptDirPath(optDir.getAbsolutePath());
        FreelineCore.applyDynamicDex(dexDir.getAbsolutePath(), optDir.getAbsolutePath());
        Log.d(TAG, "dex file received (" + finalFile.length() + " bytes), rename result :" + rst  + ", save to " + finalFile.getAbsolutePath());
    }
}
