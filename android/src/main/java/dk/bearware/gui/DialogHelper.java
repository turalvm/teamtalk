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

package dk.bearware.gui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import androidx.appcompat.app.AlertDialog;

import dk.bearware.User;
import dk.bearware.backend.TeamTalkConstants;
import dk.bearware.backend.TeamTalkService;
import dk.bearware.data.Preferences;

public class DialogHelper {
    public static void showWithImeSubmit(android.app.AlertDialog.Builder alert, EditText input) {
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.requestFocus();
        android.app.AlertDialog dialog = alert.create();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        dialog.show();
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
    }

    public static void showChangeNicknameStatusDialog(final Activity activity, final TeamTalkService service, final Runnable onUpdated) {
        if (activity == null || activity.isFinishing()) return;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity);

        User myself = (service != null && service.getTTInstance() != null)
                ? service.getUsers().get(service.getTTInstance().getMyUserID())
                : null;

        final int[] modeValues = {
                TeamTalkConstants.STATUSMODE_AVAILABLE,
                TeamTalkConstants.STATUSMODE_AWAY,
                TeamTalkConstants.STATUSMODE_QUESTION
        };

        int currentMode = (myself != null)
                ? (myself.nStatusMode & TeamTalkConstants.STATUSMODE_MODE)
                : prefs.getInt("pref_status_mode", TeamTalkConstants.STATUSMODE_AVAILABLE);

        View layout = activity.getLayoutInflater().inflate(R.layout.dialog_change_nickname_status, null);
        EditText nicknameInput = layout.findViewById(R.id.nickname_input);
        EditText statusMessageInput = layout.findViewById(R.id.status_message_input);
        RadioGroup modeGroup = layout.findViewById(R.id.mode_group);

        String currentNick = "";
        String currentStatusMsg = "";

        if (service != null && service.getServerEntry() != null && !TextUtils.isEmpty(service.getServerEntry().nickname)) {
            currentNick = service.getServerEntry().nickname;
        } else {
            currentNick = prefs.getString(Preferences.PREF_GENERAL_NICKNAME, "");
        }

        if (service != null && service.getServerEntry() != null && !TextUtils.isEmpty(service.getServerEntry().statusmsg)) {
            currentStatusMsg = service.getServerEntry().statusmsg;
        } else {
            currentStatusMsg = prefs.getString(Preferences.PREF_GENERAL_STATUSMSG, "");
        }

        nicknameInput.setText(currentNick);
        if (!TextUtils.isEmpty(currentNick)) {
            nicknameInput.setSelection(currentNick.length());
        }
        statusMessageInput.setText(currentStatusMsg);
        if (!TextUtils.isEmpty(currentStatusMsg)) {
            statusMessageInput.setSelection(currentStatusMsg.length());
        }

        for (int i = 0; i < modeValues.length; i++) {
            if (modeValues[i] == currentMode) {
                modeGroup.check(((RadioButton) modeGroup.getChildAt(i)).getId());
                break;
            }
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(activity);
        alert.setTitle(R.string.action_statusnick);
        alert.setView(layout);
        alert.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            int selectedIndex = modeGroup.indexOfChild(
                    modeGroup.findViewById(modeGroup.getCheckedRadioButtonId()));
            if (selectedIndex < 0 || selectedIndex >= modeValues.length) {
                selectedIndex = 0;
            }
            int selectedMode = modeValues[selectedIndex];

            String newNick = nicknameInput.getText().toString().trim();
            String newStatusMsg = statusMessageInput.getText().toString().trim();

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(Preferences.PREF_GENERAL_NICKNAME, newNick);
            editor.putString(Preferences.PREF_GENERAL_STATUSMSG, newStatusMsg);
            editor.putInt("pref_status_mode", selectedMode);
            editor.apply();

            if (service != null && service.getServerEntry() != null) {
                service.getServerEntry().nickname = newNick;
                service.getServerEntry().statusmsg = newStatusMsg;
                service.setServerEntry(service.getServerEntry());
            }

            if (service != null && service.getTTInstance() != null && myself != null) {
                int statusMode = (myself.nStatusMode & ~TeamTalkConstants.STATUSMODE_MODE) | selectedMode;
                service.getTTInstance().doChangeNickname(newNick);
                service.getTTInstance().doChangeStatus(statusMode, newStatusMsg);
            }

            if (onUpdated != null) {
                onUpdated.run();
            }
        });

        alert.setNegativeButton(android.R.string.cancel, null);
        final AlertDialog dialog = alert.create();
        dialog.show();

        statusMessageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_NULL) {
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
    }
}
