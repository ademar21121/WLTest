package com.wltest.probe;

public final class NativeCurlBridge {
    private static volatile boolean loaded;
    private static volatile Throwable loadError;

    static {
        try {
            System.loadLibrary("native_curl_probe");
            loaded = true;
        } catch (Throwable error) {
            loaded = false;
            loadError = error;
        }
    }

    private NativeCurlBridge() {
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static String loadErrorMessage() {
        Throwable error = loadError;
        if (error == null) {
            return "";
        }
        String message = error.getMessage();
        return message == null || message.isEmpty() ? error.getClass().getSimpleName() : message;
    }

    public static ProbeResponse execute(
            String url,
            String interfaceName,
            long networkHandle,
            int timeoutMs,
            String caBundlePath,
            String[] resolveRules
    ) {
        if (!loaded) {
            return new ProbeResponse(-1, 0, "native library is not loaded: " + loadErrorMessage(), "");
        }

        String[] raw = nativeExecuteRaw(
                url,
                interfaceName,
                networkHandle,
                "GET",
                new String[]{"User-Agent: WLTest/1.0 Android"},
                "",
                true,
                "",
                0,
                resolveRules == null ? new String[0] : resolveRules,
                0,
                timeoutMs,
                timeoutMs,
                caBundlePath == null ? "" : caBundlePath,
                false,
                ""
        );
        return ProbeResponse.fromRaw(raw);
    }

    private static native String[] nativeExecuteRaw(
            String url,
            String interfaceName,
            long networkHandle,
            String method,
            String[] headers,
            String body,
            boolean followRedirects,
            String proxyUrl,
            int proxyType,
            String[] resolveRules,
            int ipResolveMode,
            int timeoutMs,
            int connectTimeoutMs,
            String caBundlePath,
            boolean debugVerbose,
            String requestId
    );

    public static final class ProbeResponse {
        public final int curlCode;
        public final int httpCode;
        public final String error;
        public final String primaryIp;

        ProbeResponse(int curlCode, int httpCode, String error, String primaryIp) {
            this.curlCode = curlCode;
            this.httpCode = httpCode;
            this.error = error == null ? "" : error;
            this.primaryIp = primaryIp == null ? "" : primaryIp;
        }

        public boolean isSuccess() {
            return curlCode == 0 && httpCode >= 200 && httpCode < 500;
        }

        static ProbeResponse fromRaw(String[] raw) {
            if (raw == null) {
                return new ProbeResponse(-1, 0, "native response is null", "");
            }
            int curlCode = parseInt(raw, 0);
            int httpCode = parseInt(raw, 1);
            String error = raw.length > 3 && raw[3] != null ? raw[3] : "";
            String primaryIp = raw.length > 4 && raw[4] != null ? raw[4] : "";
            String localError = raw.length > 5 && raw[5] != null ? raw[5] : "";
            if (!localError.isEmpty()) {
                error = localError;
            }
            return new ProbeResponse(curlCode, httpCode, error, primaryIp);
        }

        private static int parseInt(String[] raw, int index) {
            if (raw.length <= index || raw[index] == null || raw[index].isEmpty()) {
                return 0;
            }
            try {
                return Integer.parseInt(raw[index]);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
    }
}
