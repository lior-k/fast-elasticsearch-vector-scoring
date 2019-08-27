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

    private final float[] inputVector;
    private final float magnitude;

    @Override
    public double execute() {
      if (binaryEmbeddingReader == null) {
        return 0f;
      }

	    try {
            final byte[] bytes = binaryEmbeddingReader.binaryValue().bytes;
            final ByteArrayDataInput input = new ByteArrayDataInput(bytes);

            input.readVInt(); // returns the number of values which should be 1, MUST appear hear since it affect the next calls

            final int len = input.readVInt();
            // in case vector is of different size
            if (len != inputVector.length * Float.BYTES) {
                // Failing in order not to hide potential bugs
                throw new IllegalArgumentException("Input vector & indexed vector don't have the same dimensions");
            }

            float score = 0;
            if (cosine) {
            	float docVectorNorm = 0.0f;
                for (int i = 0; i < inputVector.length; i++) {
               	    float v = Float.intBitsToFloat(input.readInt());
                    docVectorNorm += v * v;  // inputVector norm
                    score += v * inputVector[i];  // dot product
                }

                if (docVectorNorm == 0 || magnitude == 0) {
                    return 0f;
                } else { // Convert cosine similarity range from (-1 to 1) to (0 to 1)
                    return (1.0f + score / (Math.sqrt(docVectorNorm) * magnitude)) / 2.0f;
                }
            } else {
                for (int i = 0; i < inputVector.length; i++) {
                    float v = Float.intBitsToFloat(input.readInt());
                    score += v * inputVector[i];  // dot product
                }

                return Math.exp(score); // Convert dot-proudct range from (-INF to +INF) to (0 to +INF)
            }
    	} catch (IOException e) {
    		throw new UncheckedIOException(e); // again - Failing in order not to hide potential bugs
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
            inputVector = new float[tmp.size()];
            for (int i = 0; i < inputVector.length; i++) {
                inputVector[i] = tmp.get(i).floatValue();
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
            float queryVectorNorm = 0.0f;
            // compute query inputVector norm once
            for (float v: this.inputVector) {
                queryVectorNorm += v * v;
            }
            this.magnitude = (float) Math.sqrt(queryVectorNorm);
        } else {
            this.magnitude = 0.0f;
        }

        try {
			this.binaryEmbeddingReader = leafContext.reader().getBinaryDocValues(this.field);
		} catch (Exception e) {
      this.binaryEmbeddingReader = null;
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
