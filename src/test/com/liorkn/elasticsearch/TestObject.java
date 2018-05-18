package com.liorkn.elasticsearch;

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/**
 * Created by Lior Knaany on 4/7/18.
 */
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TestObject {
    int jobId;
    String base64Vector;
    double[] vector;

    public TestObject(int jobId, double[] vector) {
        this.jobId = jobId;
        this.vector = vector;
        this.base64Vector = Util.convertArrayToBase64(vector);
    }
}
