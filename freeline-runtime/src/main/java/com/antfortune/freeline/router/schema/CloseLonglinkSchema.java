package com.antfortune.freeline.router.schema;

import android.util.Log;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.router.ISchemaAction;
import com.antfortune.freeline.server.EmbedHttpServer;
import com.antfortune.freeline.server.LongLinkServer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by huangyong on 16/7/28.
 */
public class CloseLonglinkSchema implements ISchemaAction {

    private static final String TAG = "Freeline.CloseLongLink";

    @Override
    public String getDescription() {
        return "closeLongLink";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        String lastSync = queries.get("lastSync");
        FreelineCore.saveLastDynamicSyncId(Long.parseLong(lastSync));
        Log.i(TAG, "save last sync value: " + lastSync);
        boolean forceRestart = queries.containsKey("restart");
        if (forceRestart) {
            Log.i(TAG, "find restart marker, appliacation will restart.");
        }
        if (LongLinkServer.isDexChanged()
                || LongLinkServer.isResourcesChanged()
                || LongLinkServer.isNativeChanged()) {
            if (LongLinkServer.isDexChanged()
                    || LongLinkServer.isNativeChanged()
                    || forceRestart) {
                if (LongLinkServer.isDexChanged()) {
                    Log.i(TAG, "with dex changes, need to restart the process (activity stack will be reserved)");
                } else if (LongLinkServer.isNativeChanged()) {
                    Log.i(TAG, "with .so files changed, need to restart the process (activity stack will be reserved)");
                }
                FreelineCore.restartApplication(LongLinkServer.getBundleName(), LongLinkServer.getDstPath(), LongLinkServer.getDynamicDexPath(), LongLinkServer.getOptDirPath());
                LongLinkServer.resetDexChangedFlag();
                LongLinkServer.resetResourcesChangedFlag();
                LongLinkServer.resetNativeChangedFlag();
            } else if (LongLinkServer.isResourcesChanged()) {
                FreelineCore.clearResourcesCache();
                FreelineCore.updateActivity(LongLinkServer.getBundleName(), LongLinkServer.getDstPath());
                LongLinkServer.resetResourcesChangedFlag();
                Log.i(TAG, "with only res changes, just recreate the running activity.");
            }
            response.setStatusCode(200);
        }
    }
}
