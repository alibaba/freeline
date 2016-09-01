package com.antfortune.freeline.router.schema;

import android.text.TextUtils;
import android.util.Log;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.router.ISchemaAction;
import com.antfortune.freeline.server.EmbedHttpServer;

import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

        String pn = FreelineCore.getApplication().getPackageName();
        String devUuid = generateStringMD5(pn);
        Log.i(TAG, "packageName: " + pn + ", devUuid: " + devUuid);

        int result = clientSync == lastSync ? 1 : 0;
        Log.i(TAG, "dev apkflag:" + apkBuildFlag + ", last sync is:" + lastSync + ", current sync is:" + clientSync);

        if (!TextUtils.isEmpty(uuid) && !uuid.equals(devUuid)) {
            result = -1;
            Log.i(TAG, "check uuid failed, skip check sync.");
        }

        response.setContentTypeText();
        response.write(String.valueOf(result).getBytes("utf-8"));
    }

    private String generateStringMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer.toHexString((aByte & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "MD5 algorithm not found.");
            return input;
        }
    }
}
