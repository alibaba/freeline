package com.antfortune.freeline.router.schema;

import android.text.TextUtils;
import android.util.Log;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.router.ISchemaAction;
import com.antfortune.freeline.server.EmbedHttpServer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hejie on 17/4/17
 */
public class GetSyncTicketSchema implements ISchemaAction {

    private static final String TAG = "Freeline.GetSyncTicket";

    @Override
    public String getDescription() {
        return "getSyncTicket";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        long apkBuildFlag = FreelineCore.getApkBuildFlag();
        long lastSync = FreelineCore.getLastDynamicSyncId() + apkBuildFlag;
        Log.i(TAG, "apkBuildFlag: " + apkBuildFlag);
        Log.i(TAG, "lastSync: " + lastSync);

        String result = "{'apkBuildFlag':"+apkBuildFlag+",'lastSync':"+lastSync+"}";

        response.setContentTypeText();
        response.write(result.getBytes("utf-8"));
    }


}
