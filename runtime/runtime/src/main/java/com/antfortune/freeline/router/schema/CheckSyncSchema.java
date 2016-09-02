package com.antfortune.freeline.router.schema;

import android.text.TextUtils;
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
        String uuid = queries.get("uuid");

        long apkBuildFlag = FreelineCore.getApkBuildFlag();
        long lastSync = FreelineCore.getLastDynamicSyncId() + apkBuildFlag;

        String devUuid = FreelineCore.getUuid();
        Log.i(TAG, "devUuid: " + devUuid);

        int result = clientSync == lastSync ? 1 : 0;
        Log.i(TAG, "dev apkflag:" + apkBuildFlag + ", last sync is:" + lastSync + ", current sync is:" + clientSync);

        if (!TextUtils.isEmpty(uuid) && !uuid.equals(devUuid)) {
            result = -1;
            Log.i(TAG, "check uuid failed, skip check sync.");
        }

        response.setContentTypeText();
        response.write(String.valueOf(result).getBytes("utf-8"));
    }


}
