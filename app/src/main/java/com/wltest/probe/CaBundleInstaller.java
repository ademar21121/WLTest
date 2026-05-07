package com.wltest.probe;

import android.content.Context;
import android.util.Base64;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Arrays;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public final class CaBundleInstaller {
    private static final String FILE_NAME = "android-system-ca.pem";

    private CaBundleInstaller() {
    }

    public static String ensureInstalled(Context context) throws Exception {
        File target = new File(context.getFilesDir(), FILE_NAME);
        if (target.exists() && target.length() > 0) {
            return target.getAbsolutePath();
        }

        X509TrustManager trustManager = defaultTrustManager();
        X509Certificate[] issuers = trustManager.getAcceptedIssuers();
        if (issuers == null || issuers.length == 0) {
            throw new IllegalStateException("Android system CA store is empty");
        }

        try (FileOutputStream output = new FileOutputStream(target, false)) {
            for (X509Certificate certificate : issuers) {
                writeCertificate(output, certificate);
            }
        }
        return target.getAbsolutePath();
    }

    private static X509TrustManager defaultTrustManager() throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm()
        );
        factory.init((KeyStore) null);
        TrustManager[] managers = factory.getTrustManagers();
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalStateException("X509TrustManager is unavailable");
    }

    private static void writeCertificate(FileOutputStream output, X509Certificate certificate)
            throws Exception {
        output.write("-----BEGIN CERTIFICATE-----\n".getBytes(StandardCharsets.US_ASCII));
        String encoded = Base64.encodeToString(certificate.getEncoded(), Base64.NO_WRAP);
        int index = 0;
        while (index < encoded.length()) {
            int end = Math.min(index + 64, encoded.length());
            output.write(encoded.substring(index, end).getBytes(StandardCharsets.US_ASCII));
            output.write('\n');
            index = end;
        }
        output.write("-----END CERTIFICATE-----\n\n".getBytes(StandardCharsets.US_ASCII));
    }
}
