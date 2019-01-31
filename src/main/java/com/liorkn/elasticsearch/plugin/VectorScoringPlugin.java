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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.Collection;
import java.util.Map;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteArrayDataInput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.plugins.ScriptPlugin;
import org.elasticsearch.script.ScriptContext;
import org.elasticsearch.script.ScriptEngine;
import org.elasticsearch.script.SearchScript;
import java.util.ArrayList;
/**
 * This class is instantiated when Elasticsearch loads the plugin for the
 * first time. If you change the name of this plugin, make sure to update
 * src/main/resources/es-plugin.properties file that points to this class.
 */
public final class VectorScoringPlugin extends Plugin implements ScriptPlugin {

	@Override
    public ScriptEngine getScriptEngine(Settings settings, Collection<ScriptContext<?>> contexts) {
        return new VectorScoringPluginEngine();
    }

    /** This {@link ScriptEngine} uses Lucene segment details to implement document scoring based on their similarity with submitted document. */
    private static class VectorScoringPluginEngine implements ScriptEngine {
        @Override
        public String getType() {
            return "knn";
        }
        
        private static final int DOUBLE_SIZE = 8;
        
        @Override
        public <T> T compile(String scriptName, String scriptSource, ScriptContext<T> context, Map<String, String> params) {
            
        	if (context.equals(SearchScript.CONTEXT) == false) {
                throw new IllegalArgumentException(getType() + " scripts cannot be used for context [" + context.name + "]");
            }
            
        	// we use the script "source" as the script identifier
            if ("binary_vector_score".equals(scriptSource)) {
                SearchScript.Factory factory = (p, lookup) -> new SearchScript.LeafFactory() {
                    final String field;
                    final boolean cosine;
                    {
                        if (p.containsKey("vector") == false) {
                            throw new IllegalArgumentException("Missing parameter [vector]");
                        }
                        if (p.containsKey("field") == false) {
                            throw new IllegalArgumentException("Missing parameter [field]");
                        }
                        if (p.containsKey("cosine") == false) {
                            throw new IllegalArgumentException("Missing parameter [cosine]");
                        }
                        field = p.get("field").toString();
                        cosine = (boolean) p.get("cosine");
                    }
                    
                    final ArrayList<Double> searchVector = (ArrayList<Double>) p.get("vector");
                    double magnitude;
                    {
	                    if (cosine) {
	                        // calc magnitude
	                        double queryVectorNorm = 0.0;
	                        // compute query inputVector norm once
	                        for (Double v : this.searchVector) {
	                            queryVectorNorm += v.doubleValue() * v.doubleValue();
	                        }
	                        magnitude =  Math.sqrt(queryVectorNorm);
	                    } else {
	                        magnitude = 0.0;
	                    }
                    }

                    @Override
                    public SearchScript newInstance(LeafReaderContext context) throws IOException {
                        return new SearchScript(p, lookup, context) {
                        	BinaryDocValues docAccess = context.reader().getBinaryDocValues(field);
                            int currentDocid = -1;
                            
                            @Override
                            public void setDocument(int docid) {
                                // Move to desired document
                                try {
                                	docAccess.advanceExact(docid);
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                                currentDocid = docid;
                            }
                            
                            @Override
                            public double runAsDouble() {
                            	if (currentDocid < 0) {
                            		return 0.0;
                            	}
                            	//actually run scoring
                            	final int size = searchVector.size();

                            	try {
                                    final byte[] bytes = docAccess.binaryValue().bytes;
                                    final ByteArrayDataInput input = new ByteArrayDataInput(bytes);
                                    input.readVInt(); // returns the number of values which should be 1, MUST appear hear since it affect the next calls
                                    final int len = input.readVInt(); // returns the number of bytes to read//if submitted vector is different size
                                    if (len != size * DOUBLE_SIZE) {
                                        return 0.0;
                                    }
                                    
                                    final int position = input.getPosition();
                                    final DoubleBuffer doubleBuffer = ByteBuffer.wrap(bytes, position, len).asDoubleBuffer();

                                    final double[] docVector = new double[size];
                                    doubleBuffer.get(docVector);
                                    double docVectorNorm = 0.0f;
                                    double score = 0;
                                    for (int i = 0; i < size; i++) {
                                        // doc inputVector norm
                                        if(cosine) {
                                            docVectorNorm += docVector[i]*docVector[i];
                                        }
                                        // dot product
                                        score += docVector[i] * searchVector.get(i).doubleValue();
                                    }
                                    if(cosine) {
                                        // cosine similarity score
                                        if (docVectorNorm == 0 || magnitude == 0){
                                            return 0f;
                                        } else {
                                            return score / (Math.sqrt(docVectorNorm) * magnitude);
                                        }
                                    } else {
                                        return score;
                                    }
                            	} catch (Exception e) {
                            		return 0;
                                }
                            }
                        };
                    }

                    @Override
                    public boolean needs_score() {
                        return false;
                    }
                };
                return context.factoryClazz.cast(factory);
            }
            throw new IllegalArgumentException("Unknown script name " + scriptSource);
        }

        @Override
        public void close() {
            // optionally close resources
        }
    }
}