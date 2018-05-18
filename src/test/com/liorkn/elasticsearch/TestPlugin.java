package com.liorkn.elasticsearch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Lior Knaany on 4/7/18.
 */
public class TestPlugin {

    private static EmbeddedElasticsearchServer esServer;
    private static RestClient esClient;

    @BeforeClass
    public static void init() throws Exception {
        esServer = new EmbeddedElasticsearchServer();
        esClient = RestClient.builder(new HttpHost("localhost", esServer.getPort(), "http")).build();

        // TODO: create test index
    }

    public static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
    }


    //@Test TODO: fix this
    public void test() throws Exception {
        final Map<String, String> params = new HashMap<>();
        final ObjectMapper mapper = new ObjectMapper();
        final TestObject[] objs = {new TestObject(1, new double[] {0.0, 0.5, 1.0}),
                new TestObject(2, new double[] {0.2, 0.6, 0.99})};

        for (int i = 0; i < objs.length; i++) {
            final TestObject t = objs[i];
            esClient.performRequest("PUT", "/test/" + t.jobId, params, new StringEntity(mapper.writeValueAsString(t)));
        }

        // Test cosine score function

    }

    @AfterClass
    public static void shutdown() {
        try {
            esClient.close();
            esServer.shutdown();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
