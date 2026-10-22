/*
 * Copyright (c) 2005-2018, BearWare.dk
 * 
 * Contact Information:
 *
 * Bjoern D. Rasmussen
 * Kirketoften 5
 * DK-8260 Viby J
 * Denmark
 * Email: contact@bearware.dk
 * Phone: +45 20 20 54 59
 * Web: http://www.bearware.dk
 *
 * This source code is part of the TeamTalk SDK owned by
 * BearWare.dk. Use of this file, or its compiled unit, requires a
 * TeamTalk SDK License Key issued by BearWare.dk.
 *
 * The TeamTalk SDK License Agreement along with its Terms and
 * Conditions are outlined in the file License.txt included with the
 * TeamTalk SDK distribution.
 *
 */

package dk.bearware.backend;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ServerRebootHelper {

    private static final String TAG = "ServerRebootHelper";
    private static final String REBOOT_COMMAND = "sudo reboot || sudo shutdown -r now || sudo systemctl reboot";
    private static final int TIMEOUT_MS = 10000;

    public interface RebootCallback {
        void onConnecting();
        void onSuccess(String output);
        void onError(String error);
    }

    public static void rebootServerAsync(final String host, final int port, final String user, final String password, final RebootCallback callback) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        mainHandler.post(() -> {
            if (callback != null) {
                callback.onConnecting();
            }
        });

        new Thread(() -> {
            Session session = null;
            ChannelExec channel = null;
            try {
                JSch jsch = new JSch();
                int sshPort = (port > 0 && port <= 65535) ? port : 22;
                session = jsch.getSession(user, host, sshPort);
                session.setPassword(password);

                Properties config = new Properties();
                config.put("StrictHostKeyChecking", "no");
                session.setConfig(config);
                session.setTimeout(TIMEOUT_MS);
                session.connect(TIMEOUT_MS);

                channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(REBOOT_COMMAND);
                channel.setInputStream(null);

                InputStream in = channel.getInputStream();
                InputStream err = channel.getErrStream();

                channel.connect(TIMEOUT_MS);

                StringBuilder output = new StringBuilder();
                StringBuilder errOutput = new StringBuilder();
                byte[] buffer = new byte[1024];

                long startTime = System.currentTimeMillis();
                long maxWaitMs = 15000;

                while (true) {
                    while (in.available() > 0) {
                        int read = in.read(buffer, 0, buffer.length);
                        if (read < 0) break;
                        output.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                    while (err.available() > 0) {
                        int read = err.read(buffer, 0, buffer.length);
                        if (read < 0) break;
                        errOutput.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
                    }
                    if (channel.isClosed()) {
                        if (in.available() > 0 || err.available() > 0) continue;
                        break;
                    }
                    if (System.currentTimeMillis() - startTime > maxWaitMs) {
                        break;
                    }
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }

                int exitStatus = channel.getExitStatus();
                if (exitStatus > 0 && errOutput.length() > 0) {
                    final String errStr = errOutput.toString().trim();
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onError(errStr);
                        }
                    });
                } else {
                    final String outStr = output.toString().trim();
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSuccess(outStr);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Server reboot failed", e);
                String errorMsg = e.getMessage();
                if (channel != null && errorMsg != null && (errorMsg.contains("session is down") || errorMsg.contains("Pipe closed") || errorMsg.contains("Socket closed") || errorMsg.contains("Connection reset"))) {
                    mainHandler.post(() -> {
                        if (callback != null) {
                            callback.onSuccess("");
                        }
                    });
                    return;
                }
                if (errorMsg != null) {
                    String lower = errorMsg.toLowerCase();
                    if (lower.contains("auth fail")) {
                        errorMsg = "Authentication failed: Invalid username or password.";
                    } else if (lower.contains("timeout") || lower.contains("timed out")) {
                        errorMsg = "Connection timed out. Please check server address and port.";
                    } else if (lower.contains("connection refused")) {
                        errorMsg = "Connection refused. Please check if SSH is running on port " + (port > 0 ? port : 22) + ".";
                    } else if (lower.contains("unknownhost") || lower.contains("name or service not known")) {
                        errorMsg = "Unknown host: Unable to resolve server address.";
                    }
                } else {
                    errorMsg = "SSH error: " + e.getClass().getSimpleName();
                }
                final String finalError = errorMsg;
                mainHandler.post(() -> {
                    if (callback != null) {
                        callback.onError(finalError);
                    }
                });
            } finally {
                if (channel != null && channel.isConnected()) {
                    try {
                        channel.disconnect();
                    } catch (Exception ignored) {}
                }
                if (session != null && session.isConnected()) {
                    try {
                        session.disconnect();
                    } catch (Exception ignored) {}
                }
            }
        }).start();
    }
}
