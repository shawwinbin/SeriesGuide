/*
 * Copyright 2012 Uwe Trottmann
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package com.uwetrottmann.androidutils;

import com.squareup.okhttp.OkHttpClient;

import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
import android.view.View;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.security.GeneralSecurityException;
import java.util.Locale;

import javax.net.ssl.SSLContext;

public class AndroidUtils {

    private static final int DEFAULT_BUFFER_SIZE = 8192;

    public static boolean isKitKatOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
    }

    public static boolean isJellyBeanMR1OrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1;
    }

    public static boolean isJellyBeanOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN;
    }

    public static boolean isICSOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH;
    }

    public static boolean isHoneycombOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB;
    }

    public static boolean isGingerbreadOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    public static boolean isFroyoOrHigher() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO;
    }

    public static boolean isGoogleTV(Context context) {
        return context.getPackageManager().hasSystemFeature("com.google.android.tv");
    }

    /**
     * Checks if {@link Environment}.MEDIA_MOUNTED is returned by {@code getExternalStorageState()}
     * and therefore external storage is read- and writeable.
     */
    public static boolean isExtStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    /**
     * Whether there is any network connected.
     */
    public static boolean isNetworkConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static boolean isRtlLayout() {
        if (AndroidUtils.isJellyBeanMR1OrHigher()) {
            int direction = TextUtils.getLayoutDirectionFromLocale(Locale.getDefault());
            return direction == View.LAYOUT_DIRECTION_RTL;
        }
        return false;
    }

    /**
     * Whether there is an active WiFi connection.
     */
    public static boolean isWifiConnected(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo wifiNetworkInfo = connectivityManager
                .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        return wifiNetworkInfo != null && wifiNetworkInfo.isConnected();
    }

    /**
     * Copies the contents of one file to the other using {@link FileChannel}s.
     *
     * @param src source {@link File}
     * @param dst destination {@link File}
     */
    public static void copyFile(File src, File dst) throws IOException {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        FileChannel inChannel = in.getChannel();
        FileChannel outChannel = out.getChannel();

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null) {
                inChannel.close();
            }
            if (outChannel != null) {
                outChannel.close();
            }
        }

        in.close();
        out.close();
    }

    /**
     * Copies data from one input stream to the other using a buffer of 8 kilobyte in size.
     *
     * @param input  {@link InputStream}
     * @param output {@link OutputStream}
     */
    public static int copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
        int count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        return count;
    }

    /**
     * Execute an {@link AsyncTask} on a thread pool.
     *
     * @param task Task to execute.
     * @param args Optional arguments to pass to {@link AsyncTask#execute(Object[])}.
     * @param <T>  Task argument type.
     */
    @TargetApi(11)
    public static <T> void executeAsyncTask(AsyncTask<T, ?, ?> task, T... args) {
        // TODO figure out how to subclass abstract and generalized AsyncTask,
        // then put this there
        if (AndroidUtils.isHoneycombOrHigher()) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, args);
        } else {
            task.execute(args);
        }
    }

    /**
     * Returns an {@link InputStream} using {@link HttpURLConnection} to connect to the given URL.
     */
    public static InputStream downloadUrl(String urlString) throws IOException {
        HttpURLConnection conn = buildHttpUrlConnection(urlString);
        conn.connect();

        InputStream stream = conn.getInputStream();
        return stream;
    }

    /**
     * Returns an {@link HttpURLConnection} using sensible default settings for mobile and taking
     * care of buggy behavior prior to Froyo.
     */
    public static HttpURLConnection buildHttpUrlConnection(String urlString) throws IOException {
        URL url = new URL(urlString);

        OkHttpClient client = createOkHttpClient();

        HttpURLConnection conn = client.open(url);
        conn.setConnectTimeout(15 * 1000 /* milliseconds */);
        conn.setReadTimeout(20 * 1000 /* milliseconds */);
        return conn;
    }

    /**
     * Create an OkHttpClient with its own private SSL context. Avoids libssl crash because other
     * libraries do not expect the global SSL context to be changed. Also see
     * https://github.com/square/okhttp/issues/184.
     */
    public static OkHttpClient createOkHttpClient() {
        OkHttpClient okHttpClient = new OkHttpClient();

        SSLContext sslContext;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
        } catch (GeneralSecurityException e) {
            throw new AssertionError(); // The system has no TLS. Just give up.
        }
        okHttpClient.setSslSocketFactory(sslContext.getSocketFactory());

        return okHttpClient;
    }

}
