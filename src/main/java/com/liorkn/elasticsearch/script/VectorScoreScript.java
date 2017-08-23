/*
Based on: https://discuss.elastic.co/t/vector-scoring/85227/4
and https://github.com/MLnick/elasticsearch-vector-scoring

another slower implementation using strings: https://github.com/ginobefun/elasticsearch-feature-vector-scoring

storing arrays is no luck - lucine index doesn't keep the array members orders
https://www.elastic.co/guide/en/elasticsearch/guide/current/complex-core-fields.html

Delimited Payload Token Filter: https://www.elastic.co/guide/en/elasticsearch/reference/2.4/analysis-delimited-payload-tokenfilter.html


 */

package com.liorkn.elasticsearch.script;

import com.liorkn.elasticsearch.plugin.VectorScoringPlugin;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.store.ByteArrayDataInput;
import org.elasticsearch.common.Nullable;
import org.elasticsearch.script.ExecutableScript;
import org.elasticsearch.script.LeafSearchScript;
import org.elasticsearch.script.NativeScriptFactory;
import org.elasticsearch.script.ScriptException;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.Map;

/**
 * Script that scores documents based on cosine similarity embedding vectors.
 */
public final class VectorScoreScript implements LeafSearchScript, ExecutableScript {

    //    private final static ESLogger logger = ESLoggerFactory.getLogger(VectorScoreScript.class.getName());
    public final static String SCRIPT_NAME = "binary_vector_score";

    // the field containing the vectors to be scored against
    public final String field;

    private static final int DOUBLE_SIZE = 8;

    private int docId;
    private BinaryDocValues binaryEmbeddingReader;

    private final double[] inputVector;
    private final double magnitude;

    private final boolean cosine;

    @Override
    public void setScorer(Scorer scorer) {
    }
    public void setSource(Map<String, Object> source) {
    }
    public float runAsFloat() {
        return ((Number)this.run()).floatValue();
    }

    public long runAsLong() {
        return ((Number)this.run()).longValue();
    }
    public double runAsDouble() {
        return ((Number)this.run()).doubleValue();
    }
    public Object unwrap(Object value) {
        return value;
    }

    @Override
    public void setDocument(int docId) {
        this.docId = docId;
    }

    public void setBinaryEmbeddingReader(BinaryDocValues binaryEmbeddingReader) {
        if(binaryEmbeddingReader == null) {
            throw new IllegalStateException("binaryEmbeddingReader can't be null");
        }
        this.binaryEmbeddingReader = binaryEmbeddingReader;
    }


    /**
     * Factory that is registered in
     * {@link VectorScoringPlugin#onModule(org.elasticsearch.script.ScriptModule)}
     * method when the plugin is loaded.
     */
    public static class Factory implements NativeScriptFactory {

        /**
         * This method is called for every search on every shard.
         * 
         * @param params
         *            list of script parameters passed with the query
         * @return new native script
         */
        @Override
        public ExecutableScript newScript(@Nullable Map<String, Object> params) throws ScriptException {
            return new VectorScoreScript(params);
        }

        /**
         * Indicates if document scores may be needed by the produced scripts.
         *
         * @return {@code true} if scores are needed.
         */
        @Override
        public boolean needsScores() {
            return false;
        }
    }

    /**
     * Init
     * @param params index that a scored are placed in this parameter. Initialize them here.
     */
    @SuppressWarnings("unchecked")
    public VectorScoreScript(Map<String, Object> params) throws ScriptException {
        final Object cosineBool = params.get("cosine");
        cosine = cosineBool != null ?
                (boolean)cosineBool :
                true;

        final Object field = params.get("field");
        if (field == null)
            throw new ScriptException("binary_vector_score script requires field input");
        this.field = field.toString();

        // get query inputVector - convert to primitive
        final ArrayList<Double> tmp = (ArrayList<Double>) params.get("vector");
        this.inputVector = new double[tmp.size()];
        for (int i = 0; i < inputVector.length; i++) {
            inputVector[i] = tmp.get(i);
        }

        if(cosine) {
            // calc magnitude
            double queryVectorNorm = 0.0;
            // compute query inputVector norm once
            for (double v : this.inputVector) {
                queryVectorNorm += v * v;
            }
            magnitude =  Math.sqrt(queryVectorNorm);
        } else {
            magnitude = 0.0;
        }
    }

    @Override
    public void setNextVar(String name, Object value) {
    }

    /**
     * Called for each document
     * @return cosine similarity of the current document against the input inputVector
     */
    @Override
    public final Object run() {
        final int size = inputVector.length;

        final byte[] bytes = binaryEmbeddingReader.get(docId).bytes;
        final ByteArrayDataInput input = new ByteArrayDataInput(bytes);
        input.readVInt(); // returns the number of values which should be 1, MUST appear hear since it affect the next calls
        final int len = input.readVInt(); // returns the number of bytes to read
        if(len != size * DOUBLE_SIZE) {
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
            score += docVector[i] * inputVector[i];
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
    }

}