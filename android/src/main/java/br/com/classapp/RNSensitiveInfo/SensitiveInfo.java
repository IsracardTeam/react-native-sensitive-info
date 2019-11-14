package br.com.classapp.RNSensitiveInfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

public class SensitiveInfo {
    private static final String AndroidKeyStore = "AndroidKeyStore";
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String AES_ECB = "AES/ECB/PKCS7Padding";
    private static KeyStore keyStore;
    private static final String KEY_ALIAS = "SHARED_PREFERENCE_KEY";
    private static final String ENCRYPTED_KEY = "ENCRYPTED_KEY";
    private static final String ENCRYPTION_SHARED_PREFERENCE_NAME = "ENCRYPTION_SHARED_PREFERENCE";
    private static final byte[] FIXED_IV = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1};
    private static Key secretKey;

    private Context mContext;

    public SensitiveInfo(Context context)
    {
        mContext = context;
    }

    public String get(String key, String defValue, String prefName) {
        SharedPreferences usersettings = mContext.getSharedPreferences(prefName, Context.MODE_PRIVATE);
        String value = usersettings.getString(key, defValue);

        return value;
    }

    public <T> T getObject(String key, Class<T> cls, String prefName) {
        SharedPreferences usersettings = mContext.getSharedPreferences(prefName, Context.MODE_PRIVATE);

        Gson gson = new Gson();
        String jsonEncrypted = usersettings.getString(key, "");
        String json = "";

        try
        {
            json = decrypt(jsonEncrypted);
        }
        catch (Exception ex)
        {
            Log.d("ex = ", ex.toString());
        }

        return gson.fromJson(json, cls);
    }

    public void initKeyStore(Context context) throws Exception {
        keyStore = KeyStore.getInstance(AndroidKeyStore);
        keyStore.load(null);
        // Generate the RSA key pairs
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            // Generate a key pair for encryption
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore);
                keyGenerator.init(
                        new KeyGenParameterSpec.Builder(KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .setRandomizedEncryptionRequired(false)
                                .build());
                keyGenerator.generateKey();
            } else {
                Calendar start = Calendar.getInstance();
                Calendar end = Calendar.getInstance();
                end.add(Calendar.YEAR, 30);
                KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(context)
                        .setAlias(KEY_ALIAS)
                        .setSubject(new X500Principal("CN=" + KEY_ALIAS))
                        .setSerialNumber(BigInteger.TEN)
                        .setStartDate(start.getTime())
                        .setEndDate(end.getTime())
                        .build();
                KeyPairGenerator kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, AndroidKeyStore);
                kpg.initialize(spec);
                kpg.generateKeyPair();
            }

        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            secretKey = ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        } else {
            //Generate and Store the AES Key
            SharedPreferences pref = context.getSharedPreferences(ENCRYPTION_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
            String encryptedKeyB64 = pref.getString(ENCRYPTED_KEY, null);
            if (encryptedKeyB64 == null) {
                byte[] key = new byte[16];
                SecureRandom secureRandom = new SecureRandom();
                secureRandom.nextBytes(key);
                byte[] encryptedKey = rsaEncrypt(key);
                encryptedKeyB64 = Base64.encodeToString(encryptedKey, Base64.DEFAULT);
                SharedPreferences.Editor edit = pref.edit();
                edit.putString(ENCRYPTED_KEY, encryptedKeyB64);
                edit.commit();
            }

            byte[] encryptedKey = Base64.decode(encryptedKeyB64, Base64.DEFAULT);
            byte[] key = rsaDecrypt(encryptedKey);
            secretKey = new SecretKeySpec(key, "AES");
        }


    }

    private byte[] rsaEncrypt(byte[] secret) throws Exception {
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_ALIAS, null);

        Cipher inputCipher = Cipher.getInstance(RSA_MODE, "AndroidOpenSSL");
        inputCipher.init(Cipher.ENCRYPT_MODE, privateKeyEntry.getCertificate().getPublicKey());

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, inputCipher);
        cipherOutputStream.write(secret);
        cipherOutputStream.close();

        return outputStream.toByteArray();
    }

    private byte[] rsaDecrypt(byte[] encrypted) throws Exception {
        KeyStore.PrivateKeyEntry privateKeyEntry = (KeyStore.PrivateKeyEntry) keyStore.getEntry(KEY_ALIAS, null);

        Cipher outputCipher = Cipher.getInstance(RSA_MODE, "AndroidOpenSSL");
        outputCipher.init(Cipher.DECRYPT_MODE, privateKeyEntry.getPrivateKey());

        CipherInputStream cipherInputStream = new CipherInputStream(new ByteArrayInputStream(encrypted), outputCipher);
        ArrayList<Byte> values = new ArrayList<>();
        int nextByte;
        while ((nextByte = cipherInputStream.read()) != -1) {
            values.add((byte) nextByte);
        }

        byte[] bytes = new byte[values.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = values.get(i).byteValue();
        }
        return bytes;
    }

    public String encrypt(String input) throws Exception {
        byte[] bytes = input.getBytes();

        Cipher c;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            c = Cipher.getInstance(AES_GCM);
            c.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(128, FIXED_IV));
        } else {
            c = Cipher.getInstance(AES_ECB, "BC");
            c.init(Cipher.ENCRYPT_MODE, secretKey);
        }
        byte[] encodedBytes = c.doFinal(bytes);
        String encryptedBase64Encoded = Base64.encodeToString(encodedBytes, Base64.DEFAULT);
        return encryptedBase64Encoded;
    }


    public String decrypt(String encrypted) throws Exception {
        Cipher c;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            c = Cipher.getInstance(AES_GCM);
            c.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, FIXED_IV));
        } else {
            c = Cipher.getInstance(AES_ECB, "BC");
            c.init(Cipher.DECRYPT_MODE, secretKey);
        }
        byte[] decodedBytes = c.doFinal(Base64.decode(encrypted, Base64.DEFAULT));
        return new String(decodedBytes);
    }
}
