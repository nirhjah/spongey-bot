import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class SignatureGenerator {

    // Example method for generating API signature
    public static String generateApiSignature(String apiKey, String method, String token, String apiSecret) {
        try {
            // Create a map to hold parameters
            Map<String, String> params = new TreeMap<>(); // TreeMap to maintain alphabetical order
            params.put("api_key", apiKey);
            params.put("method", method);
            params.put("token", token);

            // Concatenate parameters into a single string
            StringBuilder paramString = new StringBuilder();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                paramString.append(entry.getKey()).append(entry.getValue());
            }

            // Append API secret to the string
            paramString.append(apiSecret);

            // Generate MD5 hash of the resulting string
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(paramString.toString().getBytes(StandardCharsets.UTF_8));

            // Convert the byte array to a hex string
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                hexString.append(String.format("%02x", b));
            }

            return hexString.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}