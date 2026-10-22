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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dk.bearware.ClientErrorMsg;
import dk.bearware.TeamTalkBase;
import dk.bearware.UserAccount;
import dk.bearware.UserType;
import dk.bearware.backend.TeamTalkConnection;
import dk.bearware.backend.TeamTalkConnectionListener;
import dk.bearware.backend.TeamTalkService;
import dk.bearware.events.ClientEventListener;

public class UserAccountsActivity extends AppCompatActivity implements
        TeamTalkConnectionListener,
        ClientEventListener.OnCmdUserAccountListener,
        ClientEventListener.OnCmdUserAccountNewListener,
        ClientEventListener.OnCmdUserAccountRemoveListener,
        ClientEventListener.OnCmdSuccessListener,
        ClientEventListener.OnCmdErrorListener {

    private static final String TAG = "UserAccountsActivity";

    private TeamTalkConnection mConnection;
    private ListView listUserAccounts;
    private TextView textEmptyAccounts;
    private ProgressBar progressUserAccounts;
    private Button btnAddUserAccount;
    private UserAccountAdapter mAdapter;
    private final List<UserAccount> mAccountsList = new ArrayList<>();
    private int mListCmdId = 0;

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
        setContentView(R.layout.activity_user_accounts);
        EdgeToEdgeHelper.enableEdgeToEdge(this);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        btnAddUserAccount = findViewById(R.id.btn_add_user_account);
        listUserAccounts = findViewById(R.id.list_user_accounts);
        textEmptyAccounts = findViewById(R.id.text_empty_accounts);
        progressUserAccounts = findViewById(R.id.progress_user_accounts);

        mAdapter = new UserAccountAdapter();
        listUserAccounts.setAdapter(mAdapter);

        btnAddUserAccount.setOnClickListener(v -> {
            Intent intent = new Intent(UserAccountsActivity.this, UserAccountEditActivity.class);
            intent.putExtra(UserAccountEditActivity.EXTRA_READONLY, false);
            startActivity(intent);
        });

        listUserAccounts.setOnItemClickListener((parent, view, position, id) -> {
            UserAccount account = mAccountsList.get(position);
            Intent intent = new Intent(UserAccountsActivity.this, UserAccountEditActivity.class);
            intent.putExtra(UserAccountEditActivity.EXTRA_USERNAME, account.szUsername);
            intent.putExtra(UserAccountEditActivity.EXTRA_READONLY, false);
            intent.putExtra(UserAccountEditActivity.EXTRA_ACCOUNT_JSON, new Gson().toJson(account));
            startActivity(intent);
        });
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
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1001, 0, R.string.action_refresh)
                .setIcon(android.R.drawable.ic_menu_rotate)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == 1001) {
            fetchUserAccounts();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onServiceConnected(TeamTalkService service) {
        UserAccount myAccount = new UserAccount();
        boolean hasAccount = getClient().getMyUserAccount(myAccount);
        dk.bearware.User myself = new dk.bearware.User();
        boolean hasUser = getClient().getUser(getClient().getMyUserID(), myself);
        boolean isAdmin = (hasAccount && (myAccount.uUserType & UserType.USERTYPE_ADMIN) != 0)
                       || (hasUser && (myself.uUserType & UserType.USERTYPE_ADMIN) != 0);

        if (!isAdmin) {
            Intent intent = new Intent(UserAccountsActivity.this, UserAccountEditActivity.class);
            intent.putExtra(UserAccountEditActivity.EXTRA_USERNAME, myAccount.szUsername);
            intent.putExtra(UserAccountEditActivity.EXTRA_READONLY, true);
            intent.putExtra(UserAccountEditActivity.EXTRA_ACCOUNT_JSON, new Gson().toJson(myAccount));
            startActivity(intent);
            finish();
            return;
        }

        service.getEventHandler().registerOnCmdUserAccount(this, true);
        service.getEventHandler().registerOnCmdUserAccountNew(this, true);
        service.getEventHandler().registerOnCmdUserAccountRemove(this, true);
        service.getEventHandler().registerOnCmdSuccess(this, true);
        service.getEventHandler().registerOnCmdError(this, true);

        fetchUserAccounts();
    }

    private void fetchUserAccounts() {
        mAccountsList.clear();
        mAdapter.notifyDataSetChanged();
        if (progressUserAccounts != null) {
            progressUserAccounts.setVisibility(View.VISIBLE);
        }
        textEmptyAccounts.setVisibility(View.GONE);
        listUserAccounts.setVisibility(View.GONE);

        mListCmdId = getClient().doListUserAccounts(0, 10000);
        if (mListCmdId <= 0) {
            if (progressUserAccounts != null) {
                progressUserAccounts.setVisibility(View.GONE);
            }
            updateEmptyView();
        }
    }

    @Override
    public void onServiceDisconnected(TeamTalkService service) {
        if (service != null && service.getEventHandler() != null) {
            service.getEventHandler().unregisterListener(this);
        }
    }

    @Override
    public void onCmdUserAccount(UserAccount useraccount) {
        if (useraccount != null) {
            UserAccount copy = new UserAccount();
            copy.copy(useraccount);
            runOnUiThread(() -> updateOrAddAccount(copy));
        }
    }

    @Override
    public void onCmdUserAccountNew(UserAccount useraccount) {
        if (useraccount != null) {
            UserAccount copy = new UserAccount();
            copy.copy(useraccount);
            runOnUiThread(() -> updateOrAddAccount(copy));
        }
    }

    @Override
    public void onCmdUserAccountRemove(UserAccount useraccount) {
        if (useraccount != null && useraccount.szUsername != null) {
            runOnUiThread(() -> removeAccount(useraccount.szUsername));
        }
    }

    @Override
    public void onCmdSuccess(int cmdId) {
        if (cmdId == mListCmdId) {
            runOnUiThread(() -> {
                if (progressUserAccounts != null) {
                    progressUserAccounts.setVisibility(View.GONE);
                }
                updateEmptyView();
            });
        }
    }

    @Override
    public void onCmdError(int cmdId, ClientErrorMsg errmsg) {
        runOnUiThread(() -> {
            if (cmdId == mListCmdId && progressUserAccounts != null) {
                progressUserAccounts.setVisibility(View.GONE);
                updateEmptyView();
            }
            if (errmsg != null && errmsg.szErrorMsg != null) {
                Toast.makeText(UserAccountsActivity.this, errmsg.szErrorMsg, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateOrAddAccount(UserAccount useraccount) {
        if (useraccount == null || useraccount.szUsername == null) return;
        int index = findAccountIndex(useraccount.szUsername);
        if (index >= 0) {
            mAccountsList.set(index, useraccount);
        } else {
            mAccountsList.add(useraccount);
        }
        sortAndNotify();
    }

    private void removeAccount(String username) {
        if (username == null) return;
        int index = findAccountIndex(username);
        if (index >= 0) {
            mAccountsList.remove(index);
            sortAndNotify();
        }
    }

    private int findAccountIndex(String username) {
        for (int i = 0; i < mAccountsList.size(); i++) {
            if (username.equalsIgnoreCase(mAccountsList.get(i).szUsername)) {
                return i;
            }
        }
        return -1;
    }

    private void sortAndNotify() {
        Collections.sort(mAccountsList, (a, b) -> a.szUsername.compareToIgnoreCase(b.szUsername));
        mAdapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (progressUserAccounts != null && progressUserAccounts.getVisibility() == View.VISIBLE) {
            return;
        }
        if (mAccountsList.isEmpty()) {
            textEmptyAccounts.setVisibility(View.VISIBLE);
            listUserAccounts.setVisibility(View.GONE);
        } else {
            textEmptyAccounts.setVisibility(View.GONE);
            listUserAccounts.setVisibility(View.VISIBLE);
        }
    }

    private class UserAccountAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return mAccountsList.size();
        }

        @Override
        public UserAccount getItem(int position) {
            return mAccountsList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            if (convertView == null) {
                convertView = LayoutInflater.from(UserAccountsActivity.this)
                        .inflate(R.layout.item_user_account, parent, false);
                holder = new ViewHolder();
                holder.imageIcon = convertView.findViewById(R.id.image_account_icon);
                holder.textUsername = convertView.findViewById(R.id.text_account_username);
                holder.textType = convertView.findViewById(R.id.text_account_type);
                holder.textNote = convertView.findViewById(R.id.text_account_note);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            UserAccount account = getItem(position);
            holder.textUsername.setText(account.szUsername);

            if ((account.uUserType & UserType.USERTYPE_ADMIN) != 0) {
                holder.textType.setText(R.string.user_type_admin);
                holder.imageIcon.setImageResource(R.drawable.man_orange);
            } else if ((account.uUserType & UserType.USERTYPE_DEFAULT) != 0) {
                holder.textType.setText(R.string.user_type_default);
                holder.imageIcon.setImageResource(R.drawable.man_blue);
            } else {
                holder.textType.setText(R.string.user_type_none);
                holder.imageIcon.setImageResource(R.drawable.neutral_blue);
            }

            if (account.szNote != null && !account.szNote.trim().isEmpty()) {
                holder.textNote.setVisibility(View.VISIBLE);
                holder.textNote.setText(account.szNote);
            } else {
                holder.textNote.setVisibility(View.GONE);
            }

            return convertView;
        }
    }

    private static class ViewHolder {
        ImageView imageIcon;
        TextView textUsername;
        TextView textType;
        TextView textNote;
    }
}
