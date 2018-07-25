package com.liorkn.elasticsearch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Lior Knaany on 4/7/18.
 */
public class PluginTest {

    private static EmbeddedElasticsearchServer esServer;
    private static RestClient esClient;

    @BeforeClass
    public static void init() throws Exception {
        esServer = new EmbeddedElasticsearchServer();
        esClient = RestClient.builder(new HttpHost("localhost", esServer.getPort(), "http")).build();

        // delete test index if exists
        try {
            esClient.performRequest("DELETE", "/test", Collections.emptyMap());
        } catch (Exception e) {}

        // create test index
        String mappingJson = "{\n" +
                "  \"mappings\": {\n" +
                "    \"type\": {\n" +
                "      \"properties\": {\n" +
                "        \"embedding_vector\": {\n" +
                "          \"doc_values\": true,\n" +
                "          \"type\": \"binary\"\n" +
                "        },\n" +
                "        \"job_id\": {\n" +
                "          \"type\": \"long\"\n" +
                "        },\n" +
                "        \"vector\": {\n" +
                "          \"type\": \"float\"\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        esClient.performRequest("PUT", "/test", Collections.emptyMap(), new NStringEntity(mappingJson, ContentType.APPLICATION_JSON));
    }

    public static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
    }


    @Test
    public void test() throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("refresh", "true");
        final ObjectMapper mapper = new ObjectMapper();
        final TestObject[] objs = {new TestObject(1, new double[] {0.0, 0.5, 1.0}),
                new TestObject(2, new double[] {0.2, 0.6, 0.99})};

        for (int i = 0; i < objs.length; i++) {
            final TestObject t = objs[i];
            final String json = mapper.writeValueAsString(t);
            System.out.println(json);
            final Response put = esClient.performRequest("PUT", "/test/type/" + t.jobId, params, new StringEntity(json, ContentType.APPLICATION_JSON));
            System.out.println(put);
            System.out.println(EntityUtils.toString(put.getEntity()));
            final int statusCode = put.getStatusLine().getStatusCode();
            Assert.assertTrue(statusCode == 200 || statusCode == 201);
        }

        // Test cosine score function
        String body = "{" +
                "  \"query\": {" +
                "    \"function_score\": {" +
                "      \"boost_mode\": \"replace\"," +
                "      \"script_score\": {" +
                "        \"script\": {" +
                "          \"inline\": \"binary_vector_score\"," +
                "          \"lang\": \"knn\"," +
                "          \"params\": {" +
                "            \"cosine\": false," +
                "            \"field\": \"embedding_vector\"," +
                "            \"vector\": [" +
                "               0.1, 0.2, 0.3" +
                "             ]" +
                "          }" +
                "        }" +
                "      }" +
                "    }" +
                "  }," +
                "  \"size\": 100" +
                "}";
        final Response res = esClient.performRequest("POST", "/test/_search", Collections.emptyMap(), new NStringEntity(body, ContentType.APPLICATION_JSON));
        System.out.println(res);
        final String resBody = EntityUtils.toString(res.getEntity());
        System.out.println(resBody);
        Assert.assertEquals("search should return status code 200", 200, res.getStatusLine().getStatusCode());
        Assert.assertTrue(String.format("There should be %d documents in the search response", objs.length), resBody.contains("\"hits\":{\"total\":" + objs.length));
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
