package vn.ducthe.organic.integration.payment;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class GatewaySignature {
    private GatewaySignature() {}

    public static String sign(String algorithm, String secret, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Unable to sign payment message", exception);
        }
    }

    public static boolean matches(String expected, String actual) {
        if (actual == null || actual.length() != expected.length()) {
            return false;
        }
        try {
            return MessageDigest.isEqual(HexFormat.of().parseHex(expected), HexFormat.of().parseHex(actual));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
