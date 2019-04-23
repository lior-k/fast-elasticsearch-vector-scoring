package com.liorkn.elasticsearch;

import com.google.common.annotations.VisibleForTesting;
import com.liorkn.elasticsearch.plugin.VectorScoringPlugin;
import org.apache.commons.io.FileUtils;
import org.elasticsearch.client.Client;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.http.BindHttpException;
import org.elasticsearch.index.reindex.ReindexPlugin;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.node.NodeValidationException;
import org.elasticsearch.painless.PainlessPlugin;
import org.elasticsearch.transport.Netty4Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import static java.util.Collections.emptyMap;


public class EmbeddedElasticsearchServer {
    private static final Random RANDOM = new Random();
    private static final String DEFAULT_DATA_DIRECTORY = "target/elasticsearch-data";
    private static final String DEFAULT_HOME_DIRECTORY = "target/elasticsearch-home";
    private static final int MAX_PORT_RETRIES = 20;

    private Node node;
    private int port;
    private String dataDirectory;

    public EmbeddedElasticsearchServer() throws NodeValidationException {
        this(DEFAULT_DATA_DIRECTORY);
    }

    private EmbeddedElasticsearchServer(String dataDirectory) throws NodeValidationException {
        this(dataDirectory, randomPort());
    }

    private EmbeddedElasticsearchServer(String defaultDataDirectory, int port) throws NodeValidationException {
        this.dataDirectory = defaultDataDirectory;
        this.port = port;

        Settings.Builder settings = Settings.builder()
                .put("http.type", "netty4")
                .put("path.data", dataDirectory)
                .put("path.home", DEFAULT_HOME_DIRECTORY)
                .put("node.max_local_storage_nodes", 10000)
                .put("node.name", "test");

        startNodeInAvailablePort(settings);
    }

    private void startNodeInAvailablePort(Settings.Builder settings) throws NodeValidationException {
        int findFreePortRetries = MAX_PORT_RETRIES;
        boolean success = false;

        while(!success) {
            try {
                settings.put("http.port", String.valueOf(this.port));
                
                Settings envSettings = settings.build();
                Environment env = InternalSettingsPreparer.prepareEnvironment(envSettings, emptyMap(), null, null);
                // this a hack in order to load Groovy plug in since we want to enable the usage of scripts
                node = new NodeExt(env, Arrays.asList(Netty4Plugin.class, PainlessPlugin.class, ReindexPlugin.class, VectorScoringPlugin.class));
                node.start();
                success = true;
                System.out.println(EmbeddedElasticsearchServer.class.getName() + ": Using port: " + this.port);
            } catch (BindHttpException exception) {
                if(findFreePortRetries == 0) {
                    System.out.println("Could not find any free port in range: [" + (this.port - MAX_PORT_RETRIES) + " - " + this.port+"]");
                    throw exception;
                }
                findFreePortRetries--;
                System.out.println("Port already in use (" + this.port + "). Trying another port...");
                this.port = randomPort();
            }
        }
    }

    public String getUrl() {
        return "http://localhost:" + port;
    }

    public Client getClient() {
        return node.client();
    }

    public void shutdown() {
        if ( node != null )
            try {
                node.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        deleteDataDirectory();
    }

    private void deleteDataDirectory() {
        try {
            FileUtils.deleteDirectory(new File(dataDirectory));
        } catch (IOException e) {
            throw new RuntimeException("Could not delete data directory of embedded elasticsearch server", e);
        }
    }

    private static int randomPort() {
        return RANDOM.nextInt(500) + 4200;
    }

    @VisibleForTesting
    public int getPort() {
        return port;
    }
}
