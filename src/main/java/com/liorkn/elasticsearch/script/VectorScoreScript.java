package com.liorkn.elasticsearch.script;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.ByteArrayDataInput;
import org.elasticsearch.script.ScoreScript;
import org.elasticsearch.search.lookup.SearchLookup;

import com.liorkn.elasticsearch.Util;

public final class VectorScoreScript extends ScoreScript {

    private BinaryDocValues binaryEmbeddingReader;
    
	private final String field;
    private final boolean cosine;

    private final double[] inputVector;
    private final double magnitude;
        
	public double execute() { 
		try {
            final byte[] bytes = binaryEmbeddingReader.binaryValue().bytes;
            final ByteArrayDataInput input = new ByteArrayDataInput(bytes);
            
            input.readVInt(); // returns the number of values which should be 1, MUST appear hear since it affect the next calls
            
            final int len = input.readVInt();
            // in case vector is of different size
            if (len != inputVector.length * Double.BYTES) {
                return 0.0;
            }
            
            float score = 0;

            if (cosine) {
                double docVectorNorm = 0.0f;
                for (int i = 0; i < inputVector.length; i++) {
                    double v = Double.longBitsToDouble(input.readLong());
                    docVectorNorm += v * v;  // inputVector norm
                    score += v * inputVector[i];  // dot product
                }

                if (docVectorNorm == 0 || magnitude == 0) {
                    return 0f;
                } else {
                    return score / (Math.sqrt(docVectorNorm) * magnitude);
                }
            } else {
                for (int i = 0; i < inputVector.length; i++) {
                    double v = Double.longBitsToDouble(input.readLong());
                    score += v * inputVector[i];  // dot product
                }

                return score;
            }
    	} catch (Exception e) {
    		return 0.0;
        }
	}
    
	@Override
    public void setDocument(int docId) {
		 try {
         	this.binaryEmbeddingReader.advanceExact(docId);
         } catch (IOException e) {
             throw new UncheckedIOException(e);
         }
    }
    
    @SuppressWarnings("unchecked")
	public VectorScoreScript(Map<String, Object> params, SearchLookup lookup, LeafReaderContext leafContext) {
		super(params, lookup, leafContext);
		
		final Object cosineBool = params.get("cosine");
        this.cosine = cosineBool != null ?
                (boolean)cosineBool :
                true;

        final Object field = params.get("field");
        if (field == null)
            throw new IllegalArgumentException("binary_vector_score script requires field input");
        this.field = field.toString();

        // get query inputVector - convert to primitive
        final Object vector = params.get("vector");
        if(vector != null) {
            final ArrayList<Double> tmp = (ArrayList<Double>) vector;
            inputVector = new double[tmp.size()];
            for (int i = 0; i < inputVector.length; i++) {
                inputVector[i] = tmp.get(i).doubleValue();
            }
        } else {
            final Object encodedVector = params.get("encoded_vector");
            if(encodedVector == null) {
                throw new IllegalArgumentException("Must have at 'vector' or 'encoded_vector' as a parameter");
            }
            inputVector = Util.convertBase64ToArray((String) encodedVector);
        }

        if (this.cosine) {
            // calc magnitude
            double queryVectorNorm = 0.0f;
            // compute query inputVector norm once
            for (double v: this.inputVector) {
                queryVectorNorm += v * v;
            }
            this.magnitude = (double) Math.sqrt(queryVectorNorm);
        } else {
            this.magnitude = 0.0f;
        }
        
        try {
			this.binaryEmbeddingReader = leafContext.reader().getBinaryDocValues(this.field);
		} catch (IOException e) {
			throw new IllegalStateException("binaryEmbeddingReader can't be null");
		}
	}
	
	public static class VectorScoreScriptFactory implements LeafFactory {
		private final Map<String, Object> params;
        private final SearchLookup lookup;
        
        public VectorScoreScriptFactory(Map<String, Object> params, SearchLookup lookup) {
            this.params = params;
            this.lookup = lookup;
        }
		
        public boolean needs_score() {
            return false;
        }

		@Override
		public ScoreScript newInstance(LeafReaderContext ctx) throws IOException {
			return new VectorScoreScript(this.params, this.lookup, ctx);
		}
    }
}