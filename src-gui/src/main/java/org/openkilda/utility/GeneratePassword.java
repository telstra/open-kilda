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

package org.openkilda.utility;

import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public final class GeneratePassword {
    
    public static final String AES = "AES";
    public static final String KEY = "DB99A2A8EB6904F492E9DF0595ED683C";
    
    private GeneratePassword() {}

    @SuppressWarnings("unused")
    private static String byteArrayToHexString(byte[] b) {
        StringBuffer sb = new StringBuffer(b.length * 2);
        for (int i = 0; i < b.length; i++) {
            int v = b[i] & 0xff;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString().toUpperCase();
    }

    private static byte[] hexStringToByteArray(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(s.substring(index, index + 2), 16);
            b[i] = (byte) v;
        }
        return b;
    }

    /**
     * Encrypt.
     *
     * @param password the password
     * @return the string
     * @throws Exception the exception
     */
    public static String encrypt(String password)
            throws Exception {
        byte[] bytekey = hexStringToByteArray(KEY);
        SecretKeySpec sks = new SecretKeySpec(bytekey, GeneratePassword.AES);
        Cipher cipher = Cipher.getInstance(GeneratePassword.AES);
        cipher.init(Cipher.ENCRYPT_MODE, sks, cipher.getParameters());
        byte[] encrypted = cipher.doFinal(password.getBytes());
        return Base64.getEncoder().withoutPadding().encodeToString(encrypted);
    }
    
    /**
     * Decrypt.
     *
     * @param password the password
     * @return the string
     * @throws Exception the exception
     */
    public static String decrypt(String password) throws Exception {
        
        byte[] bytekey = hexStringToByteArray(KEY);
        SecretKeySpec sks = new SecretKeySpec(bytekey, GeneratePassword.AES);
        Cipher cipher = Cipher.getInstance(GeneratePassword.AES);
        cipher.init(Cipher.DECRYPT_MODE, sks);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(password));
        return new String(decrypted);
    }
    
}
