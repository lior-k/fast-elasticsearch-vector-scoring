# Fast Elasticsearch Vector Scoring

This Plugin allows you to score Elasticsearch documents based on embedding-vectors, using dot-product or cosine-similarity.

## General
* This plugin was inspired from [This elasticsearch vector scoring plugin](https://github.com/MLnick/elasticsearch-vector-scoring) and [this discussion](https://discuss.elastic.co/t/vector-scoring/85227/6) to achieve 10 times faster processing over the original.
give it a try.
* I gained this substantial speed improvement by using the lucene index directly
* I developed it for my workplace which needs to pick KNN from a set of ~4M vectors. our current ES setup is able to answer this in ~80ms


## Elasticsearch version
* Currently designed for Elasticsearch 5.6.0.
* for Elasticsearch 5.2.2 use branch `es-5.2.2`
* for Elasticsearch 2.4.4 use branch `es-2.4.4`


## Maven configuration
* Clone the project
* `mvn package` to compile the plugin as a zip file
* In Elasticsearch run `elasticsearch-plugin install file:/PATH_TO_ZIP` to install plugin



## Usage

### Documents
* Each document you score should have a field containing the base64 representation of your vector. for example:
```
   {
   	"id": 1,
   	....
   	"embedding_vector": "v7l48eAAAAA/s4VHwAAAAD+R7I5AAAAAv8MBMAAAAAA/yEI3AAAAAL/IWkeAAAAAv7s480AAAAC/v6DUgAAAAL+wJi0gAAAAP76VqUAAAAC/sL1ZYAAAAL/dyq/gAAAAP62FVcAAAAC/tQRvYAAAAL+j6ycAAAAAP6v1KcAAAAC/bN5hQAAAAL+u9ItAAAAAP4ckTsAAAAC/pmkjYAAAAD+cYpwAAAAAP5renEAAAAC/qY0HQAAAAD+wyYGgAAAAP5WrCcAAAAA/qzjTQAAAAD++LBzAAAAAP49wNKAAAAC/vu/aIAAAAD+hqXfAAAAAP4FfNCAAAAA/pjC64AAAAL+qwT2gAAAAv6S3OGAAAAC/gfMtgAAAAD/If5ZAAAAAP5mcXOAAAAC/xYAU4AAAAL+2nlfAAAAAP7sCXOAAAAA/petBIAAAAD9soYnAAAAAv5R7X+AAAAC/pgM/IAAAAL+ojI/gAAAAP2gPz2AAAAA/3FonoAAAAL/IHg1AAAAAv6p1SmAAAAA/tvKlQAAAAD/I2OMAAAAAP3FBiCAAAAA/wEd8IAAAAL94wI9AAAAAP2Y1IIAAAAA/rnS4wAAAAL9vriVgAAAAv1QxoCAAAAC/1/qu4AAAAL+inZFAAAAAv7aGA+AAAAA/lqYVYAAAAD+kNP0AAAAAP730BiAAAAA="
   }
   ```
* Use this field mapping:
```
        "embedding_vector": {
        "type": "binary",
        "doc_values": true
```
* The vector can be of any dimension

### Converting a vector to Base64
to convert an array of doubles to a base64 string we use these example methods:

**Java**
```
public static final String convertArrayToBase64(double[] array) {
	final int capacity = 8 * array.length;
	final ByteBuffer bb = ByteBuffer.allocate(capacity);
	for (int i = 0; i < array.length; i++) {
		bb.putDouble(array[i]);
	}
	bb.rewind();
	final ByteBuffer encodedBB = Base64.getEncoder().encode(bb);
	return new String(encodedBB.array());
}

public static double[] convertBase64ToArray(String base64Str) {
	final byte[] decode = Base64.getDecoder().decode(base64Str.getBytes());
	final DoubleBuffer doubleBuffer = ByteBuffer.wrap(decode).asDoubleBuffer();

	final double[] dims = new double[doubleBuffer.capacity()];
	doubleBuffer.get(dims);
	return dims;
}
```
**Python**
```
import base64
import numpy as np

dbig = np.dtype('>f8')

def decode_float_list(base64_string):
    bytes = base64.b64decode(base64_string)
    return np.frombuffer(bytes, dtype=dbig).tolist()

def encode_array(arr):
    base64_str = base64.b64encode(np.array(arr).astype(dbig)).decode("utf-8")
    return base64_str
```

**Ruby**
```
require 'base64'

def decode_float_list(base64_string)
  Base64.strict_decode64(base64_string).unpack('G*')
end

def encode_array(arr)
  Base64.strict_encode64(arr.pack('G*'))
end
```

### Querying
* For querying the 100 KNN documents use this POST message on your ES index:


    For ES 5.X:
```
{
  "query": {
    "function_score": {
      "boost_mode": "replace",
      "script_score": {
        "script": {
          "inline": "binary_vector_score",
          "lang": "knn",
          "params": {
            "cosine": false,
            "field": "embedding_vector",
            "vector": [
               -0.09217305481433868, 0.010635560378432274, -0.02878434956073761, 0.06988169997930527, 0.1273992955684662, -0.023723633959889412, 0.05490724742412567, -0.12124507874250412, -0.023694118484854698, 0.014595639891922474, 0.1471538096666336, 0.044936809688806534, -0.02795785665512085, -0.05665992572903633, -0.2441125512123108, 0.2755320072174072, 0.11451690644025803, 0.20242854952812195, -0.1387604922056198, 0.05219579488039017, 0.1145530641078949, 0.09967200458049774, 0.2161576747894287, 0.06157230958342552, 0.10350126028060913, 0.20387393236160278, 0.1367097795009613, 0.02070528082549572, 0.19238869845867157, 0.059613026678562164, 0.014012521132826805, 0.16701748967170715, 0.04985826835036278, -0.10990987718105316, -0.12032567709684372, -0.1450948715209961, 0.13585780560970306, 0.037511035799980164, 0.04251480475068092, 0.10693439096212387, -0.08861573040485382, -0.07457160204648972, 0.0549330934882164, 0.19136285781860352, 0.03346432000398636, -0.03652812913060188, -0.1902569830417633, 0.03250952064990997, -0.3061246871948242, 0.05219300463795662, -0.07879918068647385, 0.1403723508119583, -0.08893408626317978, -0.24330253899097443, -0.07105310261249542, -0.18161986768245697, 0.15501035749912262, -0.216160386800766, -0.06377710402011871, -0.07671763002872467, 0.05360138416290283, -0.052845533937215805, -0.02905619889497757, 0.08279753476381302
             ]
          }
        }
      }
    }
  },
  "size": 100
}
```
  
  
    For ES 2.X:
    
```
{
  "query": {
    "function_score": {
      "boost_mode": "replace",
      "script_score": {
        "lang": "knn",
        "params": {
          "cosine": false,
          "field": "embedding_vector",
          "vector": [
               -0.09217305481433868, 0.010635560378432274, -0.02878434956073761, 0.06988169997930527, 0.1273992955684662, -0.023723633959889412, 0.05490724742412567, -0.12124507874250412, -0.023694118484854698, 0.014595639891922474, 0.1471538096666336, 0.044936809688806534, -0.02795785665512085, -0.05665992572903633, -0.2441125512123108, 0.2755320072174072, 0.11451690644025803, 0.20242854952812195, -0.1387604922056198, 0.05219579488039017, 0.1145530641078949, 0.09967200458049774, 0.2161576747894287, 0.06157230958342552, 0.10350126028060913, 0.20387393236160278, 0.1367097795009613, 0.02070528082549572, 0.19238869845867157, 0.059613026678562164, 0.014012521132826805, 0.16701748967170715, 0.04985826835036278, -0.10990987718105316, -0.12032567709684372, -0.1450948715209961, 0.13585780560970306, 0.037511035799980164, 0.04251480475068092, 0.10693439096212387, -0.08861573040485382, -0.07457160204648972, 0.0549330934882164, 0.19136285781860352, 0.03346432000398636, -0.03652812913060188, -0.1902569830417633, 0.03250952064990997, -0.3061246871948242, 0.05219300463795662, -0.07879918068647385, 0.1403723508119583, -0.08893408626317978, -0.24330253899097443, -0.07105310261249542, -0.18161986768245697, 0.15501035749912262, -0.216160386800766, -0.06377710402011871, -0.07671763002872467, 0.05360138416290283, -0.052845533937215805, -0.02905619889497757, 0.08279753476381302
             ]
        },
        "script": "binary_vector_score"
      }
    }
  },
  "size": 100
}
```
* The example above shows a vector of 64 dimensions
* Parameters:
   1. `field`: The field containing the base64 vector.
   2. `cosine`: Boolean. if true - use cosine-similarity, else use dot-product.
   3. `vector`: The vector (comma separated) to compare to.
 

* Question: I've encountered the error `java.lang.IllegalStateException: binaryEmbeddingReader can't be null` while running the query. what should I do?

    Answer: this error happens when the plugin fails to access the field you specified in the `field` parameter in at least one of the documents.

    To solve it: 
        
	* make sure that **all** the documents in your index contains the filed you specified in the `field` parameter.
see more details [here](https://github.com/lior-k/fast-elasticsearch-vector-scoring/issues/6)
    * make sure that the filed you specified in the `field` parameter has a `binary` type in the index mapping


