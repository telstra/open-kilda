package org.openkilda.security;

import java.lang.reflect.UndeclaredThrowableException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TwoFactorUtility {

    private static Log logger = LogFactory.getLog(TwoFactorUtility.class);
    private static final int[] DIGITS_POWER
    // 0 1 2 3 4 5 6 7 8
            = {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000};

    public static String getBase32EncryptedKey() {
        SecureRandom random = new SecureRandom();
        byte bytes[] = new byte[20];
        random.nextBytes(bytes);
        Base32 base32 = new Base32();
        String base32String = base32.encodeAsString(bytes);

        logger.debug("  getBase32EncryptedKey method response " + base32String);

        return base32String;
    }

    public static String generateEncryptedKey(final String base32String) {
        logger.debug("[generateEncryptedKey] param : base32String " + base32String);
        String generatedKey = null;
        try {
            String rsaString = RSAEncryptionDescription.rsaEncrypt(base32String.getBytes(), "");
            byte[] key = Base64.encodeBase64(rsaString.getBytes());
            generatedKey = new String(key);
        } catch (Exception e) {
            logger.error("[generateEncryptedKey] Exception :" + e);

        }

        logger.debug("[generateEncryptedKey] response " + generatedKey);

        return generatedKey;
    }

    public static String decryptKey(final String secretKey) throws Exception {
        logger.debug("[decryptKey] param : secretKey " + secretKey);
        byte[] decodedArray = Base64.decodeBase64(secretKey.getBytes());
        String decodeValue = new String(decodedArray, StandardCharsets.UTF_8);
        String decryptKey = RSAEncryptionDescription.rsaDecrypt(decodeValue, "");

        logger.debug("[decryptKey] response " + decryptKey);
        return decryptKey;
    }

    public static boolean validateOtp(final String otp, final String decryptKey) {
        logger.debug("[validateOtp] param : ,decryptKey : " + decryptKey);
        boolean otpValid = false;
        Base32 base32 = new Base32();
        byte decoded[] = base32.decode(decryptKey);
        String byteToHex = bytesToHex(decoded);
        String seed = byteToHex;
        long T0 = 0;
        long X = 30;
        String codelength = "6";
        long z = System.currentTimeMillis() / 1000;

        long testTime[] = {z};
        String steps = "0";
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        String otpVal = "";
        try {
            for (int i = 0; i < testTime.length; i++) {
                long T = (testTime[i] - T0) / X;
                steps = Long.toHexString(T).toUpperCase();
                while (steps.length() < 16) {
                    steps = "0" + steps;
                }
                otpVal = generateTOTP(seed, steps, codelength, "HmacSHA1");
            }
        } catch (Exception e) {
            logger.error("[validateOtp] Exception :" + e);
        }

        if (otp.equals(otpVal)) {
            otpValid = true;
        }

        logger.debug("[validateOtp] response " + otpValid);

        return otpValid;
    }

    public static String generateTOTP(final String key, final String time, final String returnDigits) {
        logger.debug("[generateTOTP] param : key " + key + ",returnDigits : " + returnDigits);
        return generateTOTP(key, time, returnDigits, "HmacSHA1");
    }

    /**
     * This method generates a TOTP value for the given set of parameters.
     *
     * @param key : the shared secret, HEX encoded
     * @param time : a value that reflects a time
     * @param returnDigits : number of digits to return
     * @param crypto : the crypto function to use
     *
     * @return: a numeric String in base 10 that includes {@link truncationDigits} digits
     */

    public static String generateTOTP(final String key, final String time,
            final String returnDigits, final String crypto) {
        logger.debug("[generateTOTP] param : key " + key + ",time : " + time + ",returnDigits: "
                + returnDigits + ", crypto :" + crypto);
        int codeDigits = Integer.decode(returnDigits).intValue();
        String result = null;

        // Using the counter
        // First 8 bytes are for the movingFactor
        // Compliant with base RFC 4226 (HOTP)
        String updatedTime = time;
        while (updatedTime.length() < 16) {
            updatedTime = "0" + updatedTime;
        }

        // Get the HEX in a Byte[]
        byte[] msg = hexStr2Bytes(updatedTime);
        byte[] k = hexStr2Bytes(key);

        byte[] hash = hmac_sha(crypto, k, msg);

        // put selected bytes into result int
        int offset = hash[hash.length - 1] & 0xf;
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        int otp = binary % DIGITS_POWER[codeDigits];

        result = Integer.toString(otp);
        while (result.length() < codeDigits) {
            result = "0" + result;
        }
        return result;
    }

    private static byte[] hexStr2Bytes(final String hex) {
        logger.debug("[hexStr2Bytes] param : key " + hex);
        // Adding one byte to get the right conversion
        // Values starting with "0" can be converted
        byte[] bArray = new BigInteger("10" + hex, 16).toByteArray();

        // Copy all the REAL bytes, not the "first"
        byte[] ret = new byte[bArray.length - 1];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = bArray[i + 1];
        }

        logger.debug("[hexStr2Bytes] response " + ret);
        return ret;
    }

    private static byte[] hmac_sha(final String crypto, final byte[] keyBytes, final byte[] text) {
        logger.debug("[hmac_sha] param : keyBytes " + keyBytes + ",text : " + text + ", crypto :"
                + crypto);

        try {
            Mac hmac;
            hmac = Mac.getInstance(crypto);
            SecretKeySpec macKey = new SecretKeySpec(keyBytes, "RAW");
            hmac.init(macKey);

            return hmac.doFinal(text);
        } catch (GeneralSecurityException gse) {
            logger.error("[hmac_sha] Exception :" + gse);
            throw new UndeclaredThrowableException(gse);
        }
    }

    private static String bytesToHex(final byte[] bytes) {
        logger.debug("[bytesToHex] param : bytes " + bytes);
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        logger.debug("[bytesToHex] response " + new String(hexChars));
        return new String(hexChars);
    }
}
