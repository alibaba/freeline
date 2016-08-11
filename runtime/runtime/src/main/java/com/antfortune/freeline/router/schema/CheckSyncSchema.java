package com.antfortune.freeline.router.schema;

import android.util.Log;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.router.ISchemaAction;
import com.antfortune.freeline.server.EmbedHttpServer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class CheckSyncSchema implements ISchemaAction {

    private static final String TAG = "Freeline.CheckSync";

    @Override
    public String getDescription() {
        return "checkSync";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        long clientSync = Long.parseLong(queries.get("sync"));
        long apkBuildFlag = FreelineCore.getApkBuildFlag();
        long lastSync = FreelineCore.getLastDynamicSyncId() + apkBuildFlag;

        int result = clientSync == lastSync ? 1 : 0;
        Log.i(TAG, "dev apkflag:" + apkBuildFlag + ", last sync is:" + lastSync + ", current sync is:" + clientSync);

        response.setContentTypeText();
        response.write(String.valueOf(result).getBytes("utf-8"));
    }
}
