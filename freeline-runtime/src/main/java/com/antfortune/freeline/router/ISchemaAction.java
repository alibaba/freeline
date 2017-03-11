package com.antfortune.freeline.router;

import com.antfortune.freeline.server.EmbedHttpServer;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public interface ISchemaAction {

    String DESCRIPTION = "description";

    String getDescription();

    void handle(String method,
                String path,
                HashMap<String, String> headers,
                Map<String, String> queries,
                InputStream input,
                EmbedHttpServer.ResponseOutputStream response) throws Exception;

}
