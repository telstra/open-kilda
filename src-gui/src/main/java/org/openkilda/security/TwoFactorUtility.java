/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.security;

import org.apache.commons.codec.binary.Base32;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

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

/**
 * The Class TwoFactorUtility.
 */

public final class TwoFactorUtility {

    /** The logger. */
    private static Log logger = LogFactory.getLog(TwoFactorUtility.class);
    
    /** The Constant DIGITS_POWER. */
    private static final int[] DIGITS_POWER
            // 0 1 2 3 4 5 6 7 8
            = { 1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000 };
    
    private TwoFactorUtility() {
        
    }

    /**
     * Gets the base 32 encrypted key.
     *
     * @return the base 32 encrypted key
     */
    public static String getBase32EncryptedKey() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        Base32 base32 = new Base32();
        String base32String = base32.encodeAsString(bytes);

        logger.debug("Get base32 encrypted key method response ");

        return base32String;
    }

    /**
     * Generate encrypted key.
     *
     * @param base32String the base 32 string
     * @return the string
     */
    public static String generateEncryptedKey(final String base32String) {
        logger.debug("Generate encrypted key.");
        String generatedKey = null;
        try {
            String rsaString = RsaEncryptionDescription.rsaEncrypt(base32String.getBytes(), "");
            byte[] key = Base64.encodeBase64(rsaString.getBytes());
            generatedKey = new String(key);
        } catch (Exception e) {
            logger.error("Error occurred while generating encrypted key", e);

        }

        return generatedKey;
    }

    /**
     * Decrypt key.
     *
     * @param secretKey the secret key
     * @return the string
     * @throws Exception the exception
     */
    public static String decryptKey(final String secretKey) throws Exception {
        logger.debug("Decrypt key. ");
        byte[] decodedArray = Base64.decodeBase64(secretKey.getBytes());
        String decodeValue = new String(decodedArray, StandardCharsets.UTF_8);
        String decryptKey = RsaEncryptionDescription.rsaDecrypt(decodeValue, "");

        return decryptKey;
    }

    /**
     * Validate otp.
     *
     * @param otp the otp
     * @param decryptKey the decrypt key
     * @return true, if successful
     */
    public static boolean validateOtp(final String otp, final String decryptKey) {
        logger.debug("Validate otp.");
        boolean otpValid = false;
        Base32 base32 = new Base32();
        byte[] decoded = base32.decode(decryptKey);
        String byteToHex = bytesToHex(decoded);
        String seed = byteToHex;
        long t0 = 0;
        long x = 30;
        String codelength = "6";
        long z = System.currentTimeMillis() / 1000;

        long[] testTime = { z };
        String steps = "0";
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        df.setTimeZone(TimeZone.getTimeZone("UTC"));
        String otpVal = "";
        try {
            for (int i = 0; i < testTime.length; i++) {
                long t = (testTime[i] - t0) / x;
                steps = Long.toHexString(t).toUpperCase();
                while (steps.length() < 16) {
                    steps = "0" + steps;
                }
                otpVal = generateOtp(seed, steps, codelength, "HmacSHA1");
            }
        } catch (Exception e) {
            logger.error("Error occurred while vaildating OTP", e);
        }

        if (otp.equals(otpVal)) {
            otpValid = true;
        }

        logger.debug("Validate otp. Response: " + otpValid);

        return otpValid;
    }

    /**
     * Generate OTP.
     *
     * @param key the key
     * @param time the time
     * @param returnDigits the return digits
     * @return the string
     */
    public static String generateOtp(final String key, final String time, final String returnDigits) {
        logger.debug("Generate Otp.");
        return generateOtp(key, time, returnDigits, "HmacSHA1");
    }

    /**
     * This method generates a OTP value for the given set of parameters.
     *
     * @param key : the shared secret, HEX encoded
     * @param time : a value that reflects a time
     * @param returnDigits : number of digits to return
     * @param crypto : the crypto function to use
     * @return the string
     * @return: a numeric String in base 10 that includes
     *          {@link truncationDigits} digits
     */

    public static String generateOtp(final String key, final String time, final String returnDigits,
            final String crypto) {
        logger.debug("Generate Otp.");
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

    /**
     * Hex str 2 bytes.
     *
     * @param hex the hex
     * @return the byte[]
     */
    private static byte[] hexStr2Bytes(final String hex) {
        logger.debug("Hex string to byte. ");
        // Adding one byte to get the right conversion
        // Values starting with "0" can be converted
        byte[] byteArray = new BigInteger("10" + hex, 16).toByteArray();

        // Copy all the REAL bytes, not the "first"
        byte[] ret = new byte[byteArray.length - 1];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = byteArray[i + 1];
        }

        return ret;
    }

    /**
     * Hmac sha.
     *
     * @param crypto the crypto
     * @param keyBytes the key bytes
     * @param text the text
     * @return the byte[]
     */
    private static byte[] hmac_sha(final String crypto, final byte[] keyBytes, final byte[] text) {
        logger.debug("hmac sha.");

        try {
            Mac hmac;
            hmac = Mac.getInstance(crypto);
            SecretKeySpec macKey = new SecretKeySpec(keyBytes, "RAW");
            hmac.init(macKey);

            return hmac.doFinal(text);
        } catch (GeneralSecurityException gse) {
            logger.error("Error occurred while generating otp", gse);
            throw new UndeclaredThrowableException(gse);
        }
    }

    /**
     * Bytes to hex.
     *
     * @param bytes the bytes
     * @return the string
     */
    private static String bytesToHex(final byte[] bytes) {
        logger.debug("Bytes to hex.");
        final char[] hexArray = "0123456789ABCDEF".toCharArray();
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
