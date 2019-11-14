package br.com.classapp.RNSensitiveInfo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import androidx.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

public class RNSensitiveInfoModule extends ReactContextBaseJavaModule {

    private SensitiveInfo mSensitiveInfo;

    public RNSensitiveInfoModule(ReactApplicationContext reactContext) {
        super(reactContext);

        mSensitiveInfo = new SensitiveInfo(reactContext);

        try {
            mSensitiveInfo.initKeyStore(reactContext);
        } catch (Exception e) {
           e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "RNSensitiveInfo";
    }

    @ReactMethod
    public void getItem(String key, ReadableMap options, Promise pm) {

        String name = sharedPreferences(options);

        String value = prefs(name).getString(key, null);
        if (value != null) {
            try {
                value = mSensitiveInfo.decrypt(value);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        pm.resolve(value);
    }

    @ReactMethod
    public void setItem(String key, String value, ReadableMap options, Promise pm) {

        String name = sharedPreferences(options);

        try {
            putExtra(key, value, prefs(name));
            pm.resolve(null);
        } catch (Exception e) {
            pm.reject(e);
        }
    }


    @ReactMethod
    public void deleteItem(String key, ReadableMap options, Promise pm) {

        String name = sharedPreferences(options);

        SharedPreferences.Editor editor = prefs(name).edit();

        editor.remove(key).apply();

        pm.resolve(null);
    }


    @ReactMethod
    public void getAllItems(ReadableMap options, Promise pm) {

        String name = sharedPreferences(options);

        Map<String, ?> allEntries = prefs(name).getAll();
        WritableMap resultData = new WritableNativeMap();

        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            String value = entry.getValue().toString();
            try {
                value = mSensitiveInfo.decrypt(value);
            } catch (Exception e) {
               e.printStackTrace();
            }
            resultData.putString(entry.getKey(), value);
        }
        pm.resolve(resultData);
    }

    private SharedPreferences prefs(String name) {
        return getReactApplicationContext().getSharedPreferences(name, Context.MODE_PRIVATE);
    }

    @NonNull
    private String sharedPreferences(ReadableMap options) {
        String name = options.hasKey("sharedPreferencesName") ? options.getString("sharedPreferencesName") : "app";
        if (name == null) {
            name = "app";
        }
        return name;
    }

    private void putExtra(String key, String value, SharedPreferences mSharedPreferences) throws Exception {
        SharedPreferences.Editor editor = mSharedPreferences.edit();
        String encrypted = mSensitiveInfo.encrypt(value);
        editor.putString(key, encrypted).apply();
    }


}
