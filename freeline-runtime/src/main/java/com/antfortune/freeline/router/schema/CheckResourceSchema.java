package com.antfortune.freeline.router.schema;

import android.text.TextUtils;
import android.util.Log;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.router.ISchemaAction;
import com.antfortune.freeline.server.EmbedHttpServer;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by huangyong on 16/7/28.
 */
public class CheckResourceSchema implements ISchemaAction {

    private static final String TAG = "Freeline.CheckResource";

    @Override
    public String getDescription() {
        return "checkResource";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        String dst = FreelineCore.getBundleFilePathByPackageId("base-res");
        if (TextUtils.isEmpty(dst)) {
            response.write("get base res path error.".getBytes("utf-8"));
            Log.e(TAG, "base resource path not found");
            response.setStatusCode(500);
        } else {
            File dstFile = new File(dst);
            String result = dstFile.exists() ? "1" : "0";
            response.write(result.getBytes("utf-8"));
            response.setStatusCode(200);
        }
    }
}
