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

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.tabs.TabLayout;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import dk.bearware.Channel;
import dk.bearware.ChannelType;
import dk.bearware.TeamTalkBase;
import dk.bearware.UserAccount;
import dk.bearware.UserRight;
import dk.bearware.UserType;
import dk.bearware.backend.TeamTalkConnection;
import dk.bearware.backend.TeamTalkConnectionListener;
import dk.bearware.backend.TeamTalkService;

public class UserAccountEditActivity extends AppCompatActivity implements TeamTalkConnectionListener {

    private static final String TAG = "UserAccountEditActivity";

    public static final String EXTRA_USERNAME = "username";
    public static final String EXTRA_READONLY = "readonly";
    public static final String EXTRA_ACCOUNT_JSON = "account_json";

    public static final int USERRIGHT_DEFAULT = (UserRight.USERRIGHT_MULTI_LOGIN |
            UserRight.USERRIGHT_VIEW_ALL_USERS |
            UserRight.USERRIGHT_CREATE_TEMPORARY_CHANNEL |
            UserRight.USERRIGHT_UPLOAD_FILES |
            UserRight.USERRIGHT_DOWNLOAD_FILES |
            UserRight.USERRIGHT_TRANSMIT_VOICE |
            UserRight.USERRIGHT_TRANSMIT_VIDEOCAPTURE |
            UserRight.USERRIGHT_TRANSMIT_DESKTOP |
            UserRight.USERRIGHT_TRANSMIT_DESKTOPINPUT |
            UserRight.USERRIGHT_TRANSMIT_MEDIAFILE_AUDIO |
            UserRight.USERRIGHT_TRANSMIT_MEDIAFILE_VIDEO |
            UserRight.USERRIGHT_TEXTMESSAGE_USER |
            UserRight.USERRIGHT_TEXTMESSAGE_CHANNEL);

    private TeamTalkConnection mConnection;
    private UserAccount mAccount = new UserAccount();
    private boolean mIsReadOnly = false;
    private boolean mIsCreateMode = false;
    private String mUsername = "";

    // Tabs
    private TabLayout tabLayout;
    private View layoutTabBasic;
    private View layoutTabRights;

    // Basic Info Views
    private EditText editUsername;
    private EditText editPassword;
    private EditText editNote;
    private Spinner spinnerInitChannel;
    private RadioGroup radioGroupUserType;
    private RadioButton radioTypeDefault;
    private RadioButton radioTypeAdmin;
    private RadioButton radioTypeNone;

    // Rights CheckBoxes
    private CheckBox checkRightChangeNickname;
    private CheckBox checkRightMultiLogin;
    private CheckBox checkRightViewAllUsers;
    private CheckBox checkRightViewHiddenChannels;
    private CheckBox checkRightModifyChannels;
    private CheckBox checkRightCreateTempChannel;
    private CheckBox checkRightUpdateServerProperties;
    private CheckBox checkRightKickUsers;
    private CheckBox checkRightBanUsers;
    private CheckBox checkRightMoveUsers;
    private CheckBox checkRightOperatorEnable;
    private CheckBox checkRightUploadFiles;
    private CheckBox checkRightDownloadFiles;
    private CheckBox checkRightRecordVoice;
    private CheckBox checkRightTransmitVoice;
    private CheckBox checkRightTransmitVideocapture;
    private CheckBox checkRightTransmitDesktop;
    private CheckBox checkRightTransmitDesktopinput;
    private CheckBox checkRightTransmitMediafileAudio;
    private CheckBox checkRightTransmitMediafileVideo;
    private CheckBox checkRightTextmessageUser;
    private CheckBox checkRightTextmessageChannel;
    private CheckBox checkRightTextmessageBroadcast;

    private CheckBox[] allRightsCheckboxes;

    // Action Buttons
    private Button btnDeleteAccount;
    private Button btnCancelAccount;
    private Button btnSaveAccount;

    private ArrayAdapter<String> channelSpinnerAdapter;
    private final List<String> channelPathsList = new ArrayList<>();

    TeamTalkService getService() {
        return mConnection.getService();
    }

    TeamTalkBase getClient() {
        return getService().getTTInstance();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mConnection = new TeamTalkConnection(this);
        setContentView(R.layout.activity_user_account_edit);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        extractIntentData();
        initViews();
        setupTabs();
        populateAccountData();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!mConnection.isBound()) {
            Intent intent = new Intent(getApplicationContext(), TeamTalkService.class);
            if (!bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
                Log.e(TAG, "Failed to bind to TeamTalk service");
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mConnection.isBound()) {
            onServiceDisconnected(getService());
            unbindService(mConnection);
            mConnection.setBound(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (mConnection.isBound()) {
            onServiceDisconnected(getService());
            unbindService(mConnection);
            mConnection.setBound(false);
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void extractIntentData() {
        mUsername = getIntent().getStringExtra(EXTRA_USERNAME);
        mIsReadOnly = getIntent().getBooleanExtra(EXTRA_READONLY, false);
        String accountJson = getIntent().getStringExtra(EXTRA_ACCOUNT_JSON);

        if (accountJson != null && !accountJson.isEmpty()) {
            try {
                mAccount = new Gson().fromJson(accountJson, UserAccount.class);
            } catch (Exception e) {
                Log.e(TAG, "Failed to parse UserAccount json", e);
                mAccount = new UserAccount();
            }
        } else {
            mAccount = new UserAccount();
            if (mUsername != null && !mUsername.isEmpty()) {
                mAccount.szUsername = mUsername;
            }
        }

        mIsCreateMode = (mUsername == null || mUsername.trim().isEmpty());
    }

    private void initViews() {
        tabLayout = findViewById(R.id.tab_layout);
        layoutTabBasic = findViewById(R.id.layout_tab_basic);
        layoutTabRights = findViewById(R.id.layout_tab_rights);

        editUsername = findViewById(R.id.edit_username);
        editPassword = findViewById(R.id.edit_password);
        editPassword.setTransformationMethod(null);
        editNote = findViewById(R.id.edit_note);
        spinnerInitChannel = findViewById(R.id.spinner_init_channel);

        radioGroupUserType = findViewById(R.id.radiogroup_user_type);
        radioTypeDefault = findViewById(R.id.radio_type_default);
        radioTypeAdmin = findViewById(R.id.radio_type_admin);
        radioTypeNone = findViewById(R.id.radio_type_none);

        checkRightChangeNickname = findViewById(R.id.check_right_change_nickname);
        checkRightMultiLogin = findViewById(R.id.check_right_multi_login);
        checkRightViewAllUsers = findViewById(R.id.check_right_view_all_users);
        checkRightViewHiddenChannels = findViewById(R.id.check_right_view_hidden_channels);
        checkRightModifyChannels = findViewById(R.id.check_right_modify_channels);
        checkRightCreateTempChannel = findViewById(R.id.check_right_create_temp_channel);
        checkRightUpdateServerProperties = findViewById(R.id.check_right_update_server_properties);
        checkRightKickUsers = findViewById(R.id.check_right_kick_users);
        checkRightBanUsers = findViewById(R.id.check_right_ban_users);
        checkRightMoveUsers = findViewById(R.id.check_right_move_users);
        checkRightOperatorEnable = findViewById(R.id.check_right_operator_enable);
        checkRightUploadFiles = findViewById(R.id.check_right_upload_files);
        checkRightDownloadFiles = findViewById(R.id.check_right_download_files);
        checkRightRecordVoice = findViewById(R.id.check_right_record_voice);
        checkRightTransmitVoice = findViewById(R.id.check_right_transmit_voice);
        checkRightTransmitVideocapture = findViewById(R.id.check_right_transmit_videocapture);
        checkRightTransmitDesktop = findViewById(R.id.check_right_transmit_desktop);
        checkRightTransmitDesktopinput = findViewById(R.id.check_right_transmit_desktopinput);
        checkRightTransmitMediafileAudio = findViewById(R.id.check_right_transmit_mediafile_audio);
        checkRightTransmitMediafileVideo = findViewById(R.id.check_right_transmit_mediafile_video);
        checkRightTextmessageUser = findViewById(R.id.check_right_textmessage_user);
        checkRightTextmessageChannel = findViewById(R.id.check_right_textmessage_channel);
        checkRightTextmessageBroadcast = findViewById(R.id.check_right_textmessage_broadcast);

        allRightsCheckboxes = new CheckBox[]{
                checkRightChangeNickname,
                checkRightMultiLogin,
                checkRightViewAllUsers,
                checkRightViewHiddenChannels,
                checkRightModifyChannels,
                checkRightCreateTempChannel,
                checkRightUpdateServerProperties,
                checkRightKickUsers,
                checkRightBanUsers,
                checkRightMoveUsers,
                checkRightOperatorEnable,
                checkRightUploadFiles,
                checkRightDownloadFiles,
                checkRightRecordVoice,
                checkRightTransmitVoice,
                checkRightTransmitVideocapture,
                checkRightTransmitDesktop,
                checkRightTransmitDesktopinput,
                checkRightTransmitMediafileAudio,
                checkRightTransmitMediafileVideo,
                checkRightTextmessageUser,
                checkRightTextmessageChannel,
                checkRightTextmessageBroadcast
        };

        btnDeleteAccount = findViewById(R.id.btn_delete_account);
        btnCancelAccount = findViewById(R.id.btn_cancel_account);
        btnSaveAccount = findViewById(R.id.btn_save_account);

        btnCancelAccount.setOnClickListener(v -> finish());
        btnDeleteAccount.setOnClickListener(v -> confirmDeleteAccount());
        btnSaveAccount.setOnClickListener(v -> saveAccount());

        radioGroupUserType.setOnCheckedChangeListener((group, checkedId) -> onUserTypeChanged(checkedId));

        channelPathsList.add("");
        channelSpinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, channelPathsList);
        channelSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInitChannel.setAdapter(channelSpinnerAdapter);
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    layoutTabBasic.setVisibility(View.VISIBLE);
                    layoutTabRights.setVisibility(View.GONE);
                } else {
                    layoutTabBasic.setVisibility(View.GONE);
                    layoutTabRights.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void populateAccountData() {
        if (mIsCreateMode) {
            setTitle(R.string.btn_add_account);
            btnDeleteAccount.setVisibility(View.GONE);
            radioTypeDefault.setChecked(true);
            mAccount.uUserType = UserType.USERTYPE_DEFAULT;
            mAccount.uUserRights = USERRIGHT_DEFAULT;
            updateRightsCheckboxes(USERRIGHT_DEFAULT);
        } else {
            setTitle(mAccount.szUsername != null ? mAccount.szUsername : getString(R.string.title_activity_user_account_edit));
            editUsername.setText(mAccount.szUsername);
            editUsername.setEnabled(false);
            editPassword.setText(mAccount.szPassword);
            editNote.setText(mAccount.szNote);

            if ((mAccount.uUserType & UserType.USERTYPE_ADMIN) != 0) {
                radioTypeAdmin.setChecked(true);
            } else if ((mAccount.uUserType & UserType.USERTYPE_DEFAULT) != 0) {
                radioTypeDefault.setChecked(true);
            } else {
                radioTypeNone.setChecked(true);
            }

            updateRightsCheckboxes(mAccount.uUserRights);

            if ((mAccount.uUserType & UserType.USERTYPE_ADMIN) != 0) {
                setAllRightsChecked(true);
                setRightsEnabled(false);
            }

            if (mIsReadOnly) {
                btnDeleteAccount.setVisibility(View.GONE);
                btnSaveAccount.setVisibility(View.GONE);
                editPassword.setEnabled(false);
                editNote.setEnabled(false);
                spinnerInitChannel.setEnabled(false);
                radioTypeDefault.setEnabled(false);
                radioTypeAdmin.setEnabled(false);
                radioTypeNone.setEnabled(false);
                setRightsEnabled(false);
            } else {
                btnDeleteAccount.setVisibility(View.VISIBLE);
            }
        }
    }

    private void updateRightsCheckboxes(int rightsMask) {
        checkRightChangeNickname.setChecked((rightsMask & UserRight.USERRIGHT_LOCKED_NICKNAME) == 0);
        checkRightMultiLogin.setChecked((rightsMask & UserRight.USERRIGHT_MULTI_LOGIN) != 0);
        checkRightViewAllUsers.setChecked((rightsMask & UserRight.USERRIGHT_VIEW_ALL_USERS) != 0);
        checkRightViewHiddenChannels.setChecked((rightsMask & UserRight.USERRIGHT_VIEW_HIDDEN_CHANNELS) != 0);
        checkRightModifyChannels.setChecked((rightsMask & UserRight.USERRIGHT_MODIFY_CHANNELS) != 0);
        checkRightCreateTempChannel.setChecked((rightsMask & UserRight.USERRIGHT_CREATE_TEMPORARY_CHANNEL) != 0);
        checkRightUpdateServerProperties.setChecked((rightsMask & UserRight.USERRIGHT_UPDATE_SERVERPROPERTIES) != 0);
        checkRightKickUsers.setChecked((rightsMask & UserRight.USERRIGHT_KICK_USERS) != 0);
        checkRightBanUsers.setChecked((rightsMask & UserRight.USERRIGHT_BAN_USERS) != 0);
        checkRightMoveUsers.setChecked((rightsMask & UserRight.USERRIGHT_MOVE_USERS) != 0);
        checkRightOperatorEnable.setChecked((rightsMask & UserRight.USERRIGHT_OPERATOR_ENABLE) != 0);
        checkRightUploadFiles.setChecked((rightsMask & UserRight.USERRIGHT_UPLOAD_FILES) != 0);
        checkRightDownloadFiles.setChecked((rightsMask & UserRight.USERRIGHT_DOWNLOAD_FILES) != 0);
        checkRightRecordVoice.setChecked((rightsMask & UserRight.USERRIGHT_RECORD_VOICE) != 0);
        checkRightTransmitVoice.setChecked((rightsMask & UserRight.USERRIGHT_TRANSMIT_VOICE) != 0);
        checkRightTransmitVideocapture.setChecked((rightsMask & UserRight.USERRIGHT_TRANSMIT_VIDEOCAPTURE) != 0);
        checkRightTransmitDesktop.setChecked((rightsMask & UserRight.USERRIGHT_TRANSMIT_DESKTOP) != 0);
        checkRightTransmitDesktopinput.setChecked((rightsMask & UserRight.USERRIGHT_TRANSMIT_DESKTOPINPUT) != 0);
        checkRightTransmitMediafileAudio.setChecked((rightsMask & UserRight.USERRIGHT_TRANSMIT_MEDIAFILE_AUDIO) != 0);
        checkRightTransmitMediafileVideo.setChecked((rightsMask & UserRight.USERRIGHT_TRANSMIT_MEDIAFILE_VIDEO) != 0);
        checkRightTextmessageUser.setChecked((rightsMask & UserRight.USERRIGHT_TEXTMESSAGE_USER) != 0);
        checkRightTextmessageChannel.setChecked((rightsMask & UserRight.USERRIGHT_TEXTMESSAGE_CHANNEL) != 0);
        checkRightTextmessageBroadcast.setChecked((rightsMask & UserRight.USERRIGHT_TEXTMESSAGE_BROADCAST) != 0);
    }

    private int calculateRightsMask() {
        int rights = 0;
        if (!checkRightChangeNickname.isChecked()) {
            rights |= UserRight.USERRIGHT_LOCKED_NICKNAME;
        }
        if (checkRightMultiLogin.isChecked()) rights |= UserRight.USERRIGHT_MULTI_LOGIN;
        if (checkRightViewAllUsers.isChecked()) rights |= UserRight.USERRIGHT_VIEW_ALL_USERS;
        if (checkRightViewHiddenChannels.isChecked()) rights |= UserRight.USERRIGHT_VIEW_HIDDEN_CHANNELS;
        if (checkRightModifyChannels.isChecked()) rights |= UserRight.USERRIGHT_MODIFY_CHANNELS;
        if (checkRightCreateTempChannel.isChecked()) rights |= UserRight.USERRIGHT_CREATE_TEMPORARY_CHANNEL;
        if (checkRightUpdateServerProperties.isChecked()) rights |= UserRight.USERRIGHT_UPDATE_SERVERPROPERTIES;
        if (checkRightKickUsers.isChecked()) rights |= UserRight.USERRIGHT_KICK_USERS;
        if (checkRightBanUsers.isChecked()) rights |= UserRight.USERRIGHT_BAN_USERS;
        if (checkRightMoveUsers.isChecked()) rights |= UserRight.USERRIGHT_MOVE_USERS;
        if (checkRightOperatorEnable.isChecked()) rights |= UserRight.USERRIGHT_OPERATOR_ENABLE;
        if (checkRightUploadFiles.isChecked()) rights |= UserRight.USERRIGHT_UPLOAD_FILES;
        if (checkRightDownloadFiles.isChecked()) rights |= UserRight.USERRIGHT_DOWNLOAD_FILES;
        if (checkRightRecordVoice.isChecked()) rights |= UserRight.USERRIGHT_RECORD_VOICE;
        if (checkRightTransmitVoice.isChecked()) rights |= UserRight.USERRIGHT_TRANSMIT_VOICE;
        if (checkRightTransmitVideocapture.isChecked()) rights |= UserRight.USERRIGHT_TRANSMIT_VIDEOCAPTURE;
        if (checkRightTransmitDesktop.isChecked()) rights |= UserRight.USERRIGHT_TRANSMIT_DESKTOP;
        if (checkRightTransmitDesktopinput.isChecked()) rights |= UserRight.USERRIGHT_TRANSMIT_DESKTOPINPUT;
        if (checkRightTransmitMediafileAudio.isChecked()) rights |= UserRight.USERRIGHT_TRANSMIT_MEDIAFILE_AUDIO;
        if (checkRightTransmitMediafileVideo.isChecked()) rights |= UserRight.USERRIGHT_TRANSMIT_MEDIAFILE_VIDEO;
        if (checkRightTextmessageUser.isChecked()) rights |= UserRight.USERRIGHT_TEXTMESSAGE_USER;
        if (checkRightTextmessageChannel.isChecked()) rights |= UserRight.USERRIGHT_TEXTMESSAGE_CHANNEL;
        if (checkRightTextmessageBroadcast.isChecked()) rights |= UserRight.USERRIGHT_TEXTMESSAGE_BROADCAST;
        return rights;
    }

    private void setRightsEnabled(boolean enabled) {
        for (CheckBox cb : allRightsCheckboxes) {
            cb.setEnabled(enabled);
        }
    }

    private void setAllRightsChecked(boolean checked) {
        for (CheckBox cb : allRightsCheckboxes) {
            cb.setChecked(checked);
        }
    }

    private void onUserTypeChanged(int checkedId) {
        boolean isAdmin = (checkedId == R.id.radio_type_admin);
        if (isAdmin) {
            setAllRightsChecked(true);
            setRightsEnabled(false);
        } else {
            if (!mIsReadOnly) {
                setRightsEnabled(true);
            }
        }
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        if (mIsReadOnly && (mAccount == null || mAccount.szUsername == null || mAccount.szUsername.isEmpty())) {
            mAccount = new UserAccount();
            getClient().getMyUserAccount(mAccount);
            populateAccountData();
        }
        populateChannels();
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
    }

    private void populateChannels() {
        channelPathsList.clear();
        channelPathsList.add("");

        Map<Integer, Channel> channels = getService().getChannels();
        if (channels != null) {
            for (Channel chan : channels.values()) {
                if ((chan.uChannelType & ChannelType.CHANNEL_PERMANENT) != 0) {
                    String path = getClient().getChannelPath(chan.nChannelID);
                    if (path != null && !path.isEmpty() && !channelPathsList.contains(path)) {
                        channelPathsList.add(path);
                    }
                }
            }
        }

        if (channelPathsList.size() > 1) {
            Collections.sort(channelPathsList.subList(1, channelPathsList.size()), String.CASE_INSENSITIVE_ORDER);
        }

        channelSpinnerAdapter.notifyDataSetChanged();

        if (mAccount != null && mAccount.szInitChannel != null && !mAccount.szInitChannel.isEmpty()) {
            int pos = channelPathsList.indexOf(mAccount.szInitChannel);
            if (pos >= 0) {
                spinnerInitChannel.setSelection(pos);
            } else {
                channelPathsList.add(mAccount.szInitChannel);
                channelSpinnerAdapter.notifyDataSetChanged();
                spinnerInitChannel.setSelection(channelPathsList.size() - 1);
            }
        } else {
            spinnerInitChannel.setSelection(0);
        }
    }

    private void saveAccount() {
        String username = editUsername.getText().toString().trim();
        if (username.isEmpty()) {
            Toast.makeText(this, R.string.err_invalid_account, Toast.LENGTH_SHORT).show();
            editUsername.requestFocus();
            return;
        }

        UserAccount account = new UserAccount();
        if (mAccount != null) {
            account.copy(mAccount);
        }

        account.szUsername = username;
        account.szPassword = editPassword.getText().toString();
        account.szNote = editNote.getText().toString();

        Object selectedChan = spinnerInitChannel.getSelectedItem();
        account.szInitChannel = (selectedChan != null) ? selectedChan.toString() : "";

        if (radioTypeAdmin.isChecked()) {
            account.uUserType = UserType.USERTYPE_ADMIN;
        } else if (radioTypeDefault.isChecked()) {
            account.uUserType = UserType.USERTYPE_DEFAULT;
        } else {
            account.uUserType = UserType.USERTYPE_NONE;
        }

        account.uUserRights = calculateRightsMask();

        int res = getClient().doNewUserAccount(account);
        if (res > 0) {
            Toast.makeText(this, R.string.btn_save_account, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            Toast.makeText(this, R.string.text_con_cmderr, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteAccount() {
        if (mAccount == null || mAccount.szUsername == null || mAccount.szUsername.isEmpty()) {
            return;
        }

        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setMessage(getString(R.string.confirm_delete_account, mAccount.szUsername));
        alert.setPositiveButton(android.R.string.yes, (dialog, which) -> {
            int res = getClient().doDeleteUserAccount(mAccount.szUsername);
            if (res > 0) {
                Toast.makeText(UserAccountEditActivity.this, R.string.btn_delete_account, Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(UserAccountEditActivity.this, R.string.text_con_cmderr, Toast.LENGTH_SHORT).show();
            }
        });
        alert.setNegativeButton(android.R.string.no, null);
        alert.show();
    }
}
