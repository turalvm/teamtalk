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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import androidx.appcompat.app.AlertDialog;
import dk.bearware.gui.R;

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

    public static void showServerRebootDialog(final Activity activity, final TeamTalkService service) {
        if (activity == null || activity.isFinishing()) return;

        View layout = activity.getLayoutInflater().inflate(R.layout.dialog_server_reboot, null);
        EditText editHost = layout.findViewById(R.id.edit_reboot_host);
        EditText editPort = layout.findViewById(R.id.edit_reboot_port);
        EditText editUser = layout.findViewById(R.id.edit_reboot_user);
        EditText editPassword = layout.findViewById(R.id.edit_reboot_password);
        CheckBox checkSave = layout.findViewById(R.id.check_reboot_save);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);
        String savedHost = prefs.getString("reboot_host", "");
        String savedPort = prefs.getString("reboot_port", "");
        String savedUser = prefs.getString("reboot_user", "");
        String savedPassword = prefs.getString("reboot_password", "");
        boolean savedRemember = prefs.getBoolean("reboot_save", true);

        if (TextUtils.isEmpty(savedHost)) {
            if (service != null && service.getServerEntry() != null && !TextUtils.isEmpty(service.getServerEntry().ipaddr)) {
                savedHost = service.getServerEntry().ipaddr;
            }
        }

        if (TextUtils.isEmpty(savedPort)) {
            savedPort = "22";
        }

        editHost.setText(savedHost);
        if (!TextUtils.isEmpty(savedHost)) {
            editHost.setSelection(savedHost.length());
        }
        editPort.setText(savedPort);
        editUser.setText(savedUser);
        editPassword.setText(savedPassword);
        checkSave.setChecked(savedRemember);

        AlertDialog.Builder alert = new AlertDialog.Builder(activity);
        alert.setTitle(R.string.dialog_title_restart_server);
        alert.setView(layout);
        alert.setPositiveButton(R.string.dialog_btn_save_restart, null);
        alert.setNegativeButton(android.R.string.cancel, null);

        final AlertDialog dialog = alert.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String host = editHost.getText().toString().trim();
            String portText = editPort.getText().toString().trim();
            String user = editUser.getText().toString().trim();
            String password = editPassword.getText().toString();

            if (TextUtils.isEmpty(host)) {
                editHost.setError(activity.getString(R.string.dialog_hint_server_host));
                editHost.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(user)) {
                editUser.setError(activity.getString(R.string.dialog_hint_ssh_username));
                editUser.requestFocus();
                return;
            }

            if (TextUtils.isEmpty(password)) {
                editPassword.setError(activity.getString(R.string.dialog_hint_ssh_password));
                editPassword.requestFocus();
                return;
            }

            int port = 22;
            if (!TextUtils.isEmpty(portText)) {
                try {
                    port = Integer.parseInt(portText);
                } catch (NumberFormatException ignored) {}
            }

            boolean remember = checkSave.isChecked();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("reboot_host", host);
            editor.putString("reboot_port", portText.isEmpty() ? "22" : portText);
            editor.putString("reboot_user", user);
            editor.putBoolean("reboot_save", remember);
            if (remember) {
                editor.putString("reboot_password", password);
            } else {
                editor.remove("reboot_password");
            }
            editor.apply();

            dialog.dismiss();

            ProgressDialog progressDialog = new ProgressDialog(activity);
            progressDialog.setMessage(activity.getString(R.string.msg_reboot_connecting));
            progressDialog.setIndeterminate(true);
            progressDialog.setCancelable(false);
            progressDialog.show();

            ServerRebootHelper.rebootServerAsync(host, port, user, password, new ServerRebootHelper.RebootCallback() {
                @Override
                public void onConnecting() {
                }

                @Override
                public void onSuccess(String output) {
                    if (progressDialog.isShowing()) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception ignored) {}
                    }
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.dialog_title_restart_server)
                                .setMessage(R.string.msg_reboot_success)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                }

                @Override
                public void onError(String error) {
                    if (progressDialog.isShowing()) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception ignored) {}
                    }
                    if (!activity.isFinishing() && !activity.isDestroyed()) {
                        String msg = activity.getString(R.string.msg_reboot_failed, error);
                        new AlertDialog.Builder(activity)
                                .setTitle(R.string.dialog_title_restart_server)
                                .setMessage(msg)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                    }
                }
            });
        });
    }
}
