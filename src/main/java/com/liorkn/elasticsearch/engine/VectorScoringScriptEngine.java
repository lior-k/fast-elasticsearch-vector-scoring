package com.liorkn.elasticsearch.engine;

import com.liorkn.elasticsearch.script.VectorScoreScript;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.ScoreScript;

/** This {@link ScriptEngine} uses Lucene segment details to implement document scoring based on their similarity with submitted document. */
public class VectorScoringScriptEngine implements ScriptEngine {

    public static final String NAME = "knn";
    private static final String SCRIPT_SOURCE = "binary_vector_score";
    
    @Override
    public String getType() {
        return NAME;
    }
    
    @Override
    public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {
    	if (context.equals(ScoreScript.CONTEXT) == false) {
            throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name + "]");
        }
    	
    	// we use the script "source" as the script identifier
        if (!SCRIPT_SOURCE.equals(scriptSource)) {
            throw new IllegalArgumentException("Unknown script name " + scriptSource);
        }

    	ScoreScript.Factory factory = VectorScoreScript.VectorScoreScriptFactory::new;
        return context.factoryClazz.cast(factory);
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public Set<ScriptContext<?>> getSupportedContexts() {
        return Collections.singleton(ScoreScript.CONTEXT);
    }
}
