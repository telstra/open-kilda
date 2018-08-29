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

package org.openkilda.floodlight.utils;

import org.openkilda.floodlight.error.CorruptedNetworkDataException;
import org.openkilda.floodlight.error.InvalidSignatureConfigurationException;
import org.openkilda.floodlight.model.ISignPayload;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTCreator;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

public class DataSignature {
    private final Algorithm signAlgorithm;
    private final JWTVerifier signVerification;

    public DataSignature(String secret) throws InvalidSignatureConfigurationException {
        try {
            signAlgorithm = Algorithm.HMAC256(secret);
            signVerification = JWT.require(signAlgorithm).build();
        } catch (UnsupportedEncodingException e) {
            throw new InvalidSignatureConfigurationException("Can't initialize sing/verify objects", e);
        }
    }

    public byte[] sign(ISignPayload payload) {
        JWTCreator.Builder token = payload.toSign(JWT.create());
        return token.sign(signAlgorithm).getBytes(Charset.forName("UTF-8"));
    }

    /**
     * Verify data signature.
     */
    public DecodedJWT verify(byte[] payload) throws CorruptedNetworkDataException {
        String payloadStr = new String(payload, Charset.forName("UTF-8"));
        DecodedJWT token;
        try {
            token = signVerification.verify(payloadStr);
        } catch (JWTVerificationException e) {
            throw new CorruptedNetworkDataException(String.format("Bad signature: %s", e));
        }

        return token;
    }
}
