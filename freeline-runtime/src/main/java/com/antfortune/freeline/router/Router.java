package com.antfortune.freeline.router;

import com.antfortune.freeline.server.EmbedHttpServer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Router {

    private static Router sInstance;

    private Map<String, ISchemaAction> mSchemaMap = new HashMap<>();

    private Router() {

    }

    public static Router getInstance() {
        synchronized (Router.class) {
            if (sInstance == null) {
                sInstance = new Router();
            }
            return sInstance;
        }
    }

    public void registerSchema(ISchemaAction schemaAction) {
        if (schemaAction != null) {
            mSchemaMap.put(schemaAction.getDescription(), schemaAction);
        }
    }

    public boolean dispatch(
            String method,
            String path,
            HashMap<String, String> headers,
            Map<String, String> queries,
            InputStream input,
            EmbedHttpServer.ResponseOutputStream response) throws Exception {
        if (queries == null || queries.size() == 0) {
            return false;
        }

        String description = queries.get(ISchemaAction.DESCRIPTION);
        for (String name : mSchemaMap.keySet()) {
            if (name.equals(description)) {
                mSchemaMap.get(name).handle(method, path, headers, queries, input, response);
                return true;
            }
        }

        return false;
    }

}
