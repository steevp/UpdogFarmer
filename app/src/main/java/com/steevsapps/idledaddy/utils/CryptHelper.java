package com.steevsapps.idledaddy.utils;

import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.security.auth.x500.X500Principal;

public class CryptHelper {
    private final static String TAG = CryptHelper.class.getSimpleName();
    private final static String KEYSTORE = "AndroidKeyStore";
    private final static String ALIAS = "IdleDaddy";
    private final static String TYPE_RSA = "RSA";
    private final static String CYPHER = "RSA/ECB/PKCS1Padding";
    private final static String ENCODING = "UTF-8";

    public static String encryptString(Context context, String toEncrypt) {
        if (TextUtils.isEmpty(toEncrypt)) {
            return "";
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                final KeyStore.PrivateKeyEntry privateKeyEntry = getPrivateKey(context);
                if (privateKeyEntry != null) {
                    final PublicKey publicKey = privateKeyEntry.getCertificate().getPublicKey();

                    // Encrypt the text
                    final Cipher input = Cipher.getInstance(CYPHER);
                    input.init(Cipher.ENCRYPT_MODE, publicKey);

                    final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    final CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, input);
                    cipherOutputStream.write(toEncrypt.getBytes(Charset.forName(ENCODING)));
                    cipherOutputStream.close();

                    return Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT);
                }
            } else {
                return Base64.encodeToString(toEncrypt.getBytes(Charset.forName(ENCODING)), Base64.DEFAULT);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to encrypt string", e);
        }
        return "";
    }

    public static String decryptString(Context context, String encrypted) {
        if (TextUtils.isEmpty(encrypted)) {
            return "";
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                final KeyStore.PrivateKeyEntry privateKeyEntry = getPrivateKey(context);
                if (privateKeyEntry != null) {
                    final PrivateKey privateKey = privateKeyEntry.getPrivateKey();

                    final Cipher output = Cipher.getInstance(CYPHER);
                    output.init(Cipher.DECRYPT_MODE, privateKey);

                    final CipherInputStream cipherInputStream = new CipherInputStream(
                            new ByteArrayInputStream(Base64.decode(encrypted, Base64.DEFAULT)), output);

                    final List<Byte> values = new ArrayList<>();
                    int nextByte;
                    while ((nextByte = cipherInputStream.read()) != -1) {
                        values.add((byte) nextByte);
                    }

                    final byte[] bytes = new byte[values.size()];
                    for (int i=0;i<bytes.length;i++) {
                        bytes[i] = values.get(i);
                    }

                    return new String(bytes, Charset.forName(ENCODING));
                }
            } else {
                return new String(Base64.decode(encrypted, Base64.DEFAULT), Charset.forName(ENCODING));
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to decrypt string", e);
        }
        return "";
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static KeyStore.PrivateKeyEntry getPrivateKey(Context context) throws KeyStoreException, NoSuchAlgorithmException,
            CertificateException, IOException, UnrecoverableEntryException {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);

        // Weird artifact of Java API.  If you don't have an InputStream to load, you still need
        // to call "load", or it'll crash.
        ks.load(null);

        // Load the keypair from the Android Key Store
        KeyStore.Entry entry = ks.getEntry(ALIAS, null);

        if (entry == null) {
            Log.w(TAG, "No keys found under alias: " + ALIAS);
            Log.w(TAG, "Generating new key...");
            try {
                createKeys(context);

                // Reload key store
                ks = KeyStore.getInstance(KEYSTORE);
                ks.load(null);

                entry = ks.getEntry(ALIAS, null);
                if (entry == null) {
                    Log.i(TAG, "Generating new key failed");
                    return null;
                }
            } catch (NoSuchProviderException|InvalidAlgorithmParameterException e) {
                Log.e(TAG, "Generating new key failed", e);
                return null;
            }
        }

        /* If entry is not a KeyStore.PrivateKeyEntry, it might have gotten stored in a previous
         * iteration of your application that was using some other mechanism, or been overwritten
         * by something else using the same keystore with the same alias.
         * You can determine the type using entry.getClass() and debug from there.
         */
        if (!(entry instanceof KeyStore.PrivateKeyEntry)) {
            Log.w(TAG, "Not an instance of a PrivateKeyEntry");
            Log.w(TAG, "Exiting signData()...");
            return null;
        }

        return (KeyStore.PrivateKeyEntry) entry;
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private static void createKeys(Context context) throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        // Create a start and end time, for the validity range of the key pair that's about to be
        // generated.
        final Calendar start = new GregorianCalendar();
        final Calendar end = new GregorianCalendar();
        end.add(Calendar.YEAR, 25);

        final KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                .setAlias(ALIAS)
                .setSubject(new X500Principal("CN=" + ALIAS))
                .setSerialNumber(BigInteger.valueOf(1337))
                .setStartDate(start.getTime())
                .setEndDate(end.getTime())
                .build();

        // Initialize a KeyPair generator using the the intended algorithm (in this example, RSA
        // and the KeyStore.  This example uses the AndroidKeyStore.
        final KeyPairGenerator generator = KeyPairGenerator.getInstance(TYPE_RSA, KEYSTORE);
        generator.initialize(spec);

        final KeyPair kp = generator.generateKeyPair();
        Log.i(TAG, "Public key is " + kp.getPublic().toString());
    }
}
