package com.flipphoneguy.app;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public final class ApkInstaller {

    public static class LatestRelease {
        public String tag;
        public String apkUrl;
    }

    public static LatestRelease checkLatest(String repo) throws Exception {
        URL url = new URL(
            "https://api.github.com/repos/" + repo + "/releases/latest");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        conn.setRequestProperty("User-Agent",
            BuildConfig.APP_NAME + "/" + BuildConfig.VERSION_NAME);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);

        try {
            if (conn.getResponseCode() != 200) return null;

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            String json = sb.toString();
            LatestRelease release = new LatestRelease();
            release.tag = extractJsonString(json, "tag_name");

            int searchFrom = 0;
            while (true) {
                int idx = json.indexOf("browser_download_url", searchFrom);
                if (idx < 0) break;
                String u = extractJsonString(
                    json.substring(idx), "browser_download_url");
                if (u != null && u.endsWith(".apk")) {
                    release.apkUrl = u;
                    break;
                }
                searchFrom = idx + 1;
            }
            return release;
        } finally {
            conn.disconnect();
        }
    }

    public static int compareVersions(String a, String b) {
        String cleanA = a.replaceFirst("^[vV]", "");
        String cleanB = b.replaceFirst("^[vV]", "");
        String[] partsA = cleanA.split("\\.");
        String[] partsB = cleanB.split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int va = i < partsA.length ? parseIntSafe(partsA[i]) : 0;
            int vb = i < partsB.length ? parseIntSafe(partsB[i]) : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    public static File download(Context ctx, String apkUrl) throws Exception {
        File out = new File(ctx.getCacheDir(), "update.apk");
        URL url = new URL(apkUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);

        try (InputStream in = new BufferedInputStream(conn.getInputStream());
             FileOutputStream fos = new FileOutputStream(out)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) fos.write(buf, 0, n);
        } finally {
            conn.disconnect();
        }
        return out;
    }

    public static void installRoot(File apk) throws Exception {
        String tmp = "/data/local/tmp/update.apk";
        String cmd = "cp " + apk.getAbsolutePath() + " " + tmp
            + " && chmod 644 " + tmp
            + " && pm install -r " + tmp
            + " && rm -f " + tmp;

        Process proc = Runtime.getRuntime().exec(
            new String[]{"su", "-c", cmd});
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(proc.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null)
            sb.append(line).append('\n');
        int exit = proc.waitFor();
        String output = sb.toString();

        apk.delete();

        if (exit != 0 || !output.contains("Success"))
            throw new Exception("pm install failed: " + output.trim());
    }

    public static void installSystem(Context ctx, File apk) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= 24) {
            Uri uri = ApkFileProvider.getUri(ctx, apk);
            intent.setDataAndType(uri,
                "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else {
            intent.setDataAndType(Uri.fromFile(apk),
                "application/vnd.android.package-archive");
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(intent);
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = json.indexOf('"', idx + search.length() + 1);
        if (start < 0) return null;
        int end = json.indexOf('"', start + 1);
        if (end < 0) return null;
        return json.substring(start + 1, end);
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return 0; }
    }
}
