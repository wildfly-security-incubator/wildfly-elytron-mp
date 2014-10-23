/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.security.sasl.md5digest._private;

import org.wildfly.security.sasl.util.ByteStringBuilder;
import org.wildfly.security.sasl.util.Charsets;
import org.wildfly.security.sasl.util.HexConverter;

import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>.
 */
public final class DigestMD5Utils {

    public static final String QOP_AUTH = "auth";
    public static final String QOP_AUTH_INT = "auth-int";
    public static final String QOP_AUTH_CONF = "auth-conf";
    public static final String[] QOP_VALUES = {QOP_AUTH, QOP_AUTH_INT, QOP_AUTH_CONF};

    public static final String AUTH_METHOD = "AUTHENTICATE";
    public static final String SECURITY_MARK = "00000000000000000000000000000000";   // 32 zeros

    public static final String HASH_algorithm = "MD5";

    /**
     * Calculates H(A1).
     *
     * @param md5
     * @param username
     * @param realm
     * @param password
     * @param nonce
     * @param cnonce
     * @param authzid
     * @param responseCharset
     * @return
     */
    public static byte[] H_A1(String username, String realm, char[] password,
                       byte[] nonce, byte[] cnonce, String authzid, Charset responseCharset) {

        CharsetEncoder latin1Encoder = Charsets.LATIN_1.newEncoder();
        latin1Encoder.reset();
        boolean bothLatin1 = latin1Encoder.canEncode(username);
        latin1Encoder.reset();
        if (bothLatin1) {
            for (char c: password) {
                bothLatin1 = bothLatin1 && latin1Encoder.canEncode(c);
            }
        }

        ByteStringBuilder urp = new ByteStringBuilder(); // username:realm:password
        urp.append(username.getBytes((bothLatin1 ? Charsets.LATIN_1 : responseCharset)));
        urp.append(':');
        if (realm != null) {
            urp.append(realm.getBytes((bothLatin1 ? Charsets.LATIN_1 : responseCharset)));
        } else {
            urp.append("");
        }
        urp.append(':');
        urp.append(new String(password).getBytes((bothLatin1 ? Charsets.LATIN_1 : responseCharset)));

        MessageDigest md5 = getMD5MessageDigest();
        byte[] digest_urp = md5.digest(urp.toArray());

        // A1
        ByteStringBuilder A1 = new ByteStringBuilder();
        A1.append(digest_urp);
        A1.append(':');
        A1.append(nonce);
        A1.append(':');
        A1.append(cnonce);
        if (authzid != null) {
            A1.append(':');
            A1.append(authzid);
        }
        return md5.digest(A1.toArray());
    }

    /**
     * Method to produce digest-response:
     * response-value  =
     *    HEX( KD ( HEX(H(A1)),
     *             { nonce-value, ":" nc-value, ":",
     *               cnonce-value, ":", qop-value, ":", HEX(H(A2)) }))
     *
     */
    public static byte[] digestResponse(byte[] H_A1,
                                 byte[] nonce, int nonce_count, byte[] cnonce,
                                 String authzid, String qop, String digest_uri) {

        // QOP
        String qop_value;
        if (qop != null && ! "".equals(qop)) {
            qop_value = qop;
        } else {
            qop_value = QOP_AUTH;
        }

        // A2
        ByteStringBuilder A2 = new ByteStringBuilder();
        A2.append(AUTH_METHOD);
        A2.append(':');
        A2.append(digest_uri);
        if (QOP_AUTH_CONF.equals(qop_value) || QOP_AUTH_INT.equals(qop_value)) {
            A2.append(':');
            A2.append(SECURITY_MARK);
        }

        MessageDigest md5 = getMD5MessageDigest();
        byte[] digest_A2 = md5.digest(A2.toArray());

        ByteStringBuilder KD = new ByteStringBuilder();
        KD.append(HexConverter.convertToHexBytes(H_A1));
        KD.append(':');
        KD.append(nonce);
        KD.append(':');
        KD.append(convertToHexBytesWithLeftPadding(nonce_count, 8));
        KD.append(':');
        KD.append(cnonce);
        KD.append(':');
        KD.append(qop_value);
        KD.append(':');
        KD.append(HexConverter.convertToHexBytes(digest_A2));

        KD.updateDigest(md5);
        return HexConverter.convertToHexBytes(md5.digest());
    }

    /**
     * Converts input to HEX and pad it from left with zeros to totalLength.
     *
     * @param input to be converted to HEX
     * @param totalLength length of returned array of bytes
     * @return
     */
    public static byte[] convertToHexBytesWithLeftPadding(int input, int totalLength) {
        byte[] retValue = new byte[totalLength];
        Arrays.fill(retValue, (byte) '0');
        byte[] hex = Integer.valueOf(String.valueOf(input), 16).toString().getBytes(Charsets.UTF_8);
        if (hex.length > totalLength) {
            throw new IllegalArgumentException("totalLength ("+totalLength+") is less than length of conversion result.");
        }

        int from = totalLength - hex.length;
        for (int i = 0; i < hex.length; i++) {
            retValue[from + i] = hex[i];
        }
        return retValue;
    }

    private static MessageDigest getMD5MessageDigest() {
        try {
            return MessageDigest.getInstance(HASH_algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
