package com.oai.insightar;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int CAMERA_REQUEST = 41;
    private WebView webView;
    private LocalServer localServer;
    private Thread serverThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.BLACK);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (Build.VERSION.SDK_INT < 23 ||
                                checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(request.getResources());
                        } else {
                            request.deny();
                        }
                    }
                });
            }
        });

        setContentView(webView);

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
        } else {
            startGame();
        }
    }

    private void startGame() {
        if (localServer != null) return;
        try {
            localServer = new LocalServer();
            serverThread = new Thread(localServer, "InsightLocalServer");
            serverThread.setDaemon(true);
            serverThread.start();
            final String url = "http://127.0.0.1:" + localServer.getPort() + "/index.html";
            webView.postDelayed(new Runnable() {
                @Override
                public void run() {
                    webView.loadUrl(url);
                }
            }, 120);
        } catch (IOException e) {
            webView.loadDataWithBaseURL(null,
                    "<html><body style='background:#050608;color:white;font-family:sans-serif;padding:24px'>" +
                    "<h2>Insight AR</h2><p>Errore avvio locale: " + escape(e.getMessage()) + "</p></body></html>",
                    "text/html", "UTF-8", null);
        }
    }

    private static String escape(String s) {
        if (s == null) return "sconosciuto";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startGame();
        } else if (requestCode == CAMERA_REQUEST) {
            webView.loadDataWithBaseURL(null,
                    "<html><body style='background:#050608;color:white;font-family:sans-serif;padding:24px'>" +
                    "<h2>Serve la fotocamera</h2><p>Abilita il permesso Fotocamera dalle impostazioni dell'app e riaprila.</p></body></html>",
                    "text/html", "UTF-8", null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) webView.onResume();
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override
    protected void onPause() {
        if (webView != null) webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        if (localServer != null) localServer.close();
        super.onDestroy();
    }

    private final class LocalServer implements Runnable {
        private final ServerSocket serverSocket;
        private final byte[] page;
        private volatile boolean running = true;

        LocalServer() throws IOException {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0));
            page = readAsset("index.html");
        }

        int getPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void run() {
            while (running) {
                Socket socket = null;
                try {
                    socket = serverSocket.accept();
                    serve(socket);
                } catch (IOException ignored) {
                    if (!running) break;
                } finally {
                    if (socket != null) try { socket.close(); } catch (IOException ignored) {}
                }
            }
        }

        private void serve(Socket socket) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            String first = reader.readLine();
            String line;
            while ((line = reader.readLine()) != null && line.length() > 0) { }

            OutputStream out = socket.getOutputStream();
            if (first != null && (first.startsWith("GET / ") || first.startsWith("GET /index.html"))) {
                String header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=utf-8\r\n" +
                        "Content-Length: " + page.length + "\r\n" +
                        "Cache-Control: no-store\r\n" +
                        "Connection: close\r\n\r\n";
                out.write(header.getBytes(StandardCharsets.US_ASCII));
                out.write(page);
            } else {
                byte[] body = "Not found".getBytes(StandardCharsets.UTF_8);
                String header = "HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\nContent-Length: " +
                        body.length + "\r\nConnection: close\r\n\r\n";
                out.write(header.getBytes(StandardCharsets.US_ASCII));
                out.write(body);
            }
            out.flush();
        }

        private byte[] readAsset(String name) throws IOException {
            InputStream in = getAssets().open(name);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) bos.write(buffer, 0, n);
            in.close();
            return bos.toByteArray();
        }

        void close() {
            running = false;
            try { serverSocket.close(); } catch (IOException ignored) { }
        }
    }
}
