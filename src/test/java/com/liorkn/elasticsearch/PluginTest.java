package com.liorkn.elasticsearch;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.Response;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

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
        	Request deleteRequest = new Request("DELETE", "/test");
            esClient.performRequest(deleteRequest);
        } catch (Exception e) {}

        // create test index
        String mappingJson = "{\n" +
                "  \"mappings\": {\n" +
                "    \"properties\": {\n" +
                "      \"embedding_vector\": {\n" +
                "        \"doc_values\": true,\n" +
                "        \"type\": \"binary\"\n" +
                "      },\n" +
                "      \"job_id\": {\n" +
                "        \"type\": \"long\"\n" +
                "      },\n" +
                "      \"vector\": {\n" +
                "        \"type\": \"float\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";
        Request putRequest = new Request("PUT", "/test");
        putRequest.setJsonEntity(mappingJson);
        esClient.performRequest(putRequest);
    }

    public static final ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
        mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
    }


    @Test
    public void test() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();
        final TestObject[] objs = {new TestObject(1, new float[] {0.0f, 0.5f, 1.0f}),
                new TestObject(2, new float[] {0.2f, 0.6f, 0.99f})};

        for (int i = 0; i < objs.length; i++) {
            final TestObject t = objs[i];
            final String json = mapper.writeValueAsString(t);
            System.out.println(json);
            Request indexRequest = new Request("POST", "/test/_doc/" + t.jobId);
            indexRequest.addParameter("refresh", "true");
            indexRequest.setJsonEntity(json);
            final Response put = esClient.performRequest(indexRequest);
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
                "          \"source\": \"binary_vector_score\"," +
                "          \"lang\": \"knn\"," +
                "          \"params\": {" +
                "            \"cosine\": true," +
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
        Request searchRequest = new Request("POST", "/test/_search");
        searchRequest.setJsonEntity(body);
        Response res = esClient.performRequest(searchRequest);
        System.out.println(res);
        String resBody = EntityUtils.toString(res.getEntity());
        System.out.println(resBody);
        Assert.assertEquals("search should return status code 200", 200, res.getStatusLine().getStatusCode());
        Assert.assertTrue(String.format("There should be %d documents in the search response", objs.length), resBody.contains("\"hits\":{\"total\":{\"value\":" + objs.length));
	// Testing Scores
        ArrayNode hitsJson = (ArrayNode)mapper.readTree(resBody).get("hits").get("hits");
        Assert.assertEquals(0.9970867, hitsJson.get(0).get("_score").asDouble(), 0);
        Assert.assertEquals(0.9780914, hitsJson.get(1).get("_score").asDouble(), 0);

	// Test dot-product score function
        body = "{" +
                "  \"query\": {" +
                "    \"function_score\": {" +
                "      \"boost_mode\": \"replace\"," +
                "      \"script_score\": {" +
                "        \"script\": {" +
                "          \"source\": \"binary_vector_score\"," +
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
        searchRequest.setJsonEntity(body);
        res = esClient.performRequest(searchRequest);
        System.out.println(res);
        resBody = EntityUtils.toString(res.getEntity());
        System.out.println(resBody);
        Assert.assertEquals("search should return status code 200", 200, res.getStatusLine().getStatusCode());
        Assert.assertTrue(String.format("There should be %d documents in the search response", objs.length), resBody.contains("\"hits\":{\"total\":{\"value\":" + objs.length));
        // Testing Scores
        hitsJson = (ArrayNode)mapper.readTree(resBody).get("hits").get("hits");
        Assert.assertEquals(1.5480561, hitsJson.get(0).get("_score").asDouble(), 0);
        Assert.assertEquals(1.4918247, hitsJson.get(1).get("_score").asDouble(), 0);
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
