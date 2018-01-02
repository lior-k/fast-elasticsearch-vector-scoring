/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.liorkn.elasticsearch.plugin;

import com.liorkn.elasticsearch.script.VectorScoreScript;
import com.liorkn.elasticsearch.service.VectorScoringScriptEngineService;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.script.ScriptModule;

/**
 * This class is instantiated when Elasticsearch loads the plugin for the
 * first time. If you change the name of this plugin, make sure to update
 * src/main/resources/es-plugin.properties file that points to this class.
 */
public final class VectorScoringPlugin extends Plugin {
	
    /**
     * The name of the plugin.
     * <p>
     * This name will be used by elasticsearch in the log file to refer to this plugin.
     *
     * @return plugin name.
     */
	@Override
    public final String name() {
        return "binary-vector-scoring";
    }
	
    /**
     * The description of the plugin.
     *
     * @return plugin description
     */
	@Override
    public final String description() {
        return "Binary vector scoring";
    }

    public final void onModule(ScriptModule module) {
        // Register vector scoring script.
        module.registerScript(VectorScoreScript.SCRIPT_NAME, VectorScoreScript.Factory.class);
        module.addScriptEngine(VectorScoringScriptEngineService.class);
    }
}