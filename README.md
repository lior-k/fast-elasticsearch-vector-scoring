# Fast Elasticsearch Vector Scoring

This Plugin allows you to score Elasticsearch documents based on embedding-vectors, using dot-product or cosine-similarity.

## General
* This plugin was inspired from [This elasticsearch vector scoring plugin](https://github.com/MLnick/elasticsearch-vector-scoring) and [this discussion](https://discuss.elastic.co/t/vector-scoring/85227/6) to achieve 10 times faster processing over the original.
give it a try.
* I gained this substantial speed improvement by using the lucene index directly
* I developed it for my workplace which needs to pick KNN from a set of ~4M vectors. our current ES setup is able to answer this in ~80ms
* **Note: Elasticsearch introduced a similar vector similarity functions in version 7.4 and above. [Elasticsearch version 8.0] (https://www.elastic.co/blog/introducing-approximate-nearest-neighbor-search-in-elasticsearch-8-0) includes native ANN support. This makes this plug-in obsolete for new Elasticsearch versions, unless for some reason their implementation is slower than this plugin**.


## Elasticsearch version
* master branch is designed for Elasticsearch 5.6.9.
* for Elasticsearch 7.9.0 use branch `es-7.9.0`
* for Elasticsearch 7.5.2 use branch `es-7.5.2`
* for Elasticsearch 7.5.0 use branch `es-7.5.0`
* for Elasticsearch 7.2.1 use branch `es-7.2.1`
* for Elasticsearch 7.1.0 use branch `es-7.1`
* for Elasticsearch 6.8.1 use branch `es-6.8.1`
* for Elasticsearch 5.2.2 use branch `es-5.2.2`
* for Elasticsearch 2.4.4 use branch `es-2.4.4`


## Maven configuration
* Clone the project
* `mvn package` to compile the plugin as a zip file
* In the Elasticsearch root folder run `./bin/elasticsearch-plugin install file://<PATH_TO_ZIP>` to install plugin. for example: `./bin/elasticsearch-plugin install file:///Users/lior/dev/fast-elasticsearch-vector-scoring/target/releases/elasticsearch-binary-vector-scoring-5.6.9.zip`



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
	}
```
* The vector can be of any dimension

### Converting a vector to Base64
to convert an array of float32 to a base64 string we use these example methods:

**Java**
```
public static float[] convertBase64ToArray(String base64Str) {
    final byte[] decode = Base64.getDecoder().decode(base64Str.getBytes());
    final FloatBuffer floatBuffer = ByteBuffer.wrap(decode).asFloatBuffer();
    final float[] dims = new float[floatBuffer.capacity()];
    floatBuffer.get(dims);

    return dims;
}

public static String convertArrayToBase64(float[] array) {
    final int capacity = Float.BYTES * array.length;
    final ByteBuffer bb = ByteBuffer.allocate(capacity);
    for (float v : array) {
        bb.putFloat(v);
    }
    bb.rewind();
    final ByteBuffer encodedBB = Base64.getEncoder().encode(bb);

    return new String(encodedBB.array());
}
```
**Python**
```
import base64
import numpy as np

dfloat32 = np.dtype('>f4')

def decode_float_list(base64_string):
    bytes = base64.b64decode(base64_string)
    return np.frombuffer(bytes, dtype=dfloat32).tolist()

def encode_array(arr):
    base64_str = base64.b64encode(np.array(arr).astype(dfloat32)).decode("utf-8")
    return base64_str
```

**Ruby**
```
require 'base64'

def decode_float_list(base64_string)
  Base64.strict_decode64(base64_string).unpack('g*')
end

def encode_array(arr)
  Base64.strict_encode64(arr.pack('g*'))
end
```

**Go**
```
import(
    "math"
    "encoding/binary"
    "encoding/base64"
)

func convertArrayToBase64(array []float32) string {
	bytes := make([]byte, 0, 4*len(array))
	for _, a := range array {
		bits := math.Float32bits(a)
		b := make([]byte, 4)
		binary.BigEndian.PutUint32(b, bits)
		bytes = append(bytes, b...)
	}

	encoded := base64.StdEncoding.EncodeToString(bytes)
	return encoded
}

func convertBase64ToArray(base64Str string) ([]float32, error) {
	decoded, err := base64.StdEncoding.DecodeString(base64Str)
	if err != nil {
		return nil, err
	}

	length := len(decoded)
	array := make([]float32, 0, length/4)

	for i := 0; i < len(decoded); i += 4 {
		bits := binary.BigEndian.Uint32(decoded[i : i+4])
		f := math.Float32frombits(bits)
		array = append(array, f)
	}
	return array, nil
}
```

### Querying
* For querying the 100 KNN documents use this POST message on your ES index:


    For ES 5.X and ES 7.X:
```
{
  "query": {
    "function_score": {
      "boost_mode": "replace",
      "script_score": {
        "script": {
	      "source": "binary_vector_score",
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
 
* Note **for ElasticSearch 6 and 7 only**:
   Because scores produced by the script_score function must be non-negative on elasticsearch 7, We convert the dot product score and cosine similarity score by using these simple equations:
    (changed dot product) = e^(original dot product)
    (changed cosine similarity) = ((original cosine similarity) + 1) / 2

    We can use these simple equation to convert them to original score.
    (original dot product) = ln(changed dot product)
    (original cosine similarity) = (changed cosine similarity) * 2 - 1

* Question: I've encountered the error `java.lang.IllegalStateException: binaryEmbeddingReader can't be null` while running the query. what should I do?

    Answer: this error happens when the plugin fails to access the field you specified in the `field` parameter in at least one of the documents.

    To solve it: 
        
	* make sure that **all** the documents in your index contains the filed you specified in the `field` parameter.
see more details [here](https://github.com/lior-k/fast-elasticsearch-vector-scoring/issues/6)
    * make sure that the filed you specified in the `field` parameter has a `binary` type in the index mapping

