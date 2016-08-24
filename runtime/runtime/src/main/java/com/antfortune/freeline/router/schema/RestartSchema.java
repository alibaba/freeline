package com.antfortune.freeline.router.schema;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.router.ISchemaAction;
import com.antfortune.freeline.server.EmbedHttpServer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by huangyong on 16/7/28.
 */
public class RestartSchema implements ISchemaAction {

    @Override
    public String getDescription() {
        return "restart";
    }

    @Override
    public void handle(String method, String path, HashMap<String, String> headers, Map<String, String> queries, InputStream input, EmbedHttpServer.ResponseOutputStream response) throws Exception {
        FreelineCore.restartApplication(null, null, null, null);
        response.setStatusCode(200);
    }
}
