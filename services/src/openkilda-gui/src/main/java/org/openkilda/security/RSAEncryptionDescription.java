package org.openkilda.security;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyFactory;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;

import javax.crypto.Cipher;

import org.apache.log4j.Logger;


/**
 * The Class RSAEncryptionDescription.
 */
public class RSAEncryptionDescription {

    /** The Constant _log. */
    private static final Logger _log = Logger.getLogger(RSAEncryptionDescription.class);


    /**
     * Read key from file.
     *
     * @param keyFileName the key file name
     * @return the key
     * @throws IOException Signals that an I/O exception has occurred.
     */
    // Return the saved key
    public static Key readKeyFromFile(final String keyFileName) throws IOException {
        _log.info("ReadKeyFromFile called with keyFileName : " + keyFileName);
        InputStream in = null;
        ObjectInputStream oin = null;
        try {
            in = Thread.currentThread().getContextClassLoader().getResourceAsStream(keyFileName);
            if (in != null) {
                oin = new ObjectInputStream(new BufferedInputStream(in));
                BigInteger m = (BigInteger) oin.readObject();
                BigInteger e = (BigInteger) oin.readObject();
                KeyFactory fact = KeyFactory.getInstance("RSA");
                if (keyFileName.startsWith("public")) {
                    return fact.generatePublic(new RSAPublicKeySpec(m, e));
                } else {
                    return fact.generatePrivate(new RSAPrivateKeySpec(m, e));
                }
            } else {
                return null;
            }
        } catch (Exception e) {
            _log.fatal("Exception in readKeyFromFile : " + e.getMessage());
            throw new RuntimeException("Spurious serialisation error", e);
        } finally {
            if (oin != null) {
                oin.close();
            }
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Rsa decrypt.
     *
     * @param text the text
     * @param file_des the file_des
     * @return the string
     * @throws Exception the exception
     */
    // Use this PublicKey object to initialize a Cipher and encrypt some data
    @SuppressWarnings("restriction")
    public static String rsaEncrypt(final byte[] text, final String file_des) throws Exception {
        Key pubKey = readKeyFromFile("public.key");
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.ENCRYPT_MODE, pubKey);
        byte[] data = cipher.doFinal(text);
        /*
         * StringBuilder sb = new StringBuilder(); for (byte b : data) { sb.append((char)b); }
         * return sb.toString();
         */
        // return new sun.misc.BASE64Encoder().encode(data);
        return new sun.misc.BASE64Encoder().encode(data);

    }

    /**
     * Rsa decrypt.
     *
     * @param text the text
     * @param file_des the file_des
     * @return the string
     * @throws Exception the exception
     */
    // Use this PublicKey object to initialize a Cipher and decrypt some data
    public static String rsaDecrypt(final String text, final String file_des) throws Exception {
        Key priKey = readKeyFromFile("private.key");
        Cipher cipher = Cipher.getInstance("RSA");
        cipher.init(Cipher.DECRYPT_MODE, priKey);
        byte[] data = cipher.doFinal(new sun.misc.BASE64Decoder().decodeBuffer(text));
        // return new String(data);
        return new String(data, "UTF8");
    }
}

