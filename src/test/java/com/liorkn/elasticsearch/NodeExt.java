package com.liorkn.elasticsearch;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.node.InternalSettingsPreparer;
import org.elasticsearch.node.Node;
import org.elasticsearch.plugins.Plugin;

import java.util.Collection;

/**
 * a really god explanation for this is on: https://discuss.elastic.co/t/unsupported-http-type-netty3-when-trying-to-start-embedded-elasticsearch-node/69669/7
 */
public class NodeExt extends Node {

    public NodeExt(Settings preparedSettings, Collection<Class<? extends Plugin>> classpathPlugins) {
        super(InternalSettingsPreparer.prepareEnvironment(preparedSettings, null),
                classpathPlugins);
    }

}
