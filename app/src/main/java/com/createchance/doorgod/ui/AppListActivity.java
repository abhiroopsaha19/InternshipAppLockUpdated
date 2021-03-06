package com.createchance.doorgod.ui;

import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.NavigationView;
import android.support.design.widget.Snackbar;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.createchance.doorgod.R;
import com.createchance.doorgod.adapter.AppAdapter;
import com.createchance.doorgod.adapter.AppInfo;
import com.createchance.doorgod.service.DoorGodService;
import com.createchance.doorgod.util.AppListForegroundEvent;
import com.createchance.doorgod.util.LogUtil;

import org.greenrobot.eventbus.EventBus;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.view.View;
import android.widget.EditText;
import android.view.View.OnClickListener;

import java.util.ArrayList;

public class AppListActivity extends AppCompatActivity {
    Button button;
   private EditText etSearch;
    private static final String TAG = "AppListActivity";

    public static final int CODE_REQUEST_PERMISSION = 100;
    public static final int CODE_START_SETTINGS = 101;

    private DrawerLayout drawerLayout;

    private NavigationView navigationView;

    private FloatingActionButton doneBtn;

    private AppAdapter mProtectedAppAdapter;
    private AppAdapter mUnprotectedAppAdapter;

    private HomeKeyWatcher mHomeKeyWatcher;

    private DoorGodService.ServiceBinder mService;

    private SharedPreferences mPrefs;
    private static final String LOCK_ENROLL_STATUS = "com.createchance.doorgod.LOCK_ENROLL_STATUS";
    private static final String LOCK_ENROLLED = "ENROLLED";

    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = (DoorGodService.ServiceBinder) service;

            if (mService != null) {
                final RecyclerView protectedRecyclerView = (RecyclerView) findViewById(R.id.protected_app_list_view);
                protectedRecyclerView.setLayoutManager(new LinearLayoutManager(AppListActivity.this));
                mProtectedAppAdapter = new AppAdapter(AppAdapter.TYPE_PROTECTED, mService.getProtectedAppList(), new AppAdapter.OnClickCallback() {
                    @Override
                    public void onClick(AppInfo info) {
                        mService.markToUnprotect(info);
                        mUnprotectedAppAdapter.notifyDataSetChanged();
                        mProtectedAppAdapter.notifyDataSetChanged();
                    }
                });
                protectedRecyclerView.setAdapter(mProtectedAppAdapter);

                RecyclerView unprotectedRecyclerView = (RecyclerView) findViewById(R.id.unprotected_app_list_view);

                unprotectedRecyclerView.setLayoutManager(new LinearLayoutManager(AppListActivity.this));

                mUnprotectedAppAdapter = new AppAdapter(AppAdapter.TYPE_UNPROTECTED, mService.getUnProtectedAppList(), new AppAdapter.OnClickCallback() {
                    @Override

                    public void onClick(AppInfo info) {
                        int size = mService.markToProtect(info);
                        mUnprotectedAppAdapter.notifyDataSetChanged();
                        mProtectedAppAdapter.notifyDataSetChanged();
                        protectedRecyclerView.smoothScrollToPosition(size);
                    }
                });
                unprotectedRecyclerView.setAdapter(mUnprotectedAppAdapter);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
        }
    };

    @Override

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        addListenerOnButton();
        // get prefs
        mPrefs = getSharedPreferences(LOCK_ENROLL_STATUS, MODE_PRIVATE);

        // bind to service
        Intent intent = new Intent(AppListActivity.this, DoorGodService.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        drawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_menu);
        }

        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                switch (item.getItemId()) {
                    case R.id.nav_settings:
                        // do settings.
                        Intent intent = new Intent(AppListActivity.this, SettingsActivity.class);
                        startActivity(intent);
                        break;
                   case R.id.nav_about:
                       Intent intento = new Intent(AppListActivity.this, VaultActivity.class);
                       startActivity(intento);
                       break;
                    default:
                        break;
                }

                drawerLayout.closeDrawers();
                return true;
            }
        });

        doneBtn = (FloatingActionButton) findViewById(R.id.fab_done);
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (mService.isProtectedAppListChanged()) {
                    mService.saveProtectList();
                    Snackbar.make(view, getString(R.string.snack_info_saved), Snackbar.LENGTH_LONG)
                            .show();
                } else {
                    Snackbar.make(view, getString(R.string.snack_info_no_change), Snackbar.LENGTH_LONG)
                            .show();
                }
            }
        });

        // check if we have PACKAGE_USAGE_STATS permission.
        if (!checkIfGetPermission()) {
            showPermissionRequestDialog();
        }

        // watch for home key press event.
        mHomeKeyWatcher = new HomeKeyWatcher(this);
        mHomeKeyWatcher.setOnHomePressedListener(new HomeKeyWatcher.OnHomePressedListener() {
            @Override
            public void onHomePressed() {
                if (mService.isProtectedAppListChanged()) {
                    Toast.makeText(AppListActivity.this,
                            R.string.toast_info_config_not_saved, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onHomeLongPressed() {
                // do nothing for now.
            }
        });
        mHomeKeyWatcher.startWatch();
    }

    public void addListenerOnButton() {

        final Context context = this;

        button = (Button) findViewById(R.id.button2);

        button.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View arg0) {

                Intent intent = new Intent(context, VaultActivity.class);
                startActivity(intent);

            }

        });

    }
    @Override
    protected void onStart() {
        super.onStart();

        if (!isLockEnrolled()) {
            Toast.makeText(AppListActivity.this,
                    R.string.first_start_info, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(AppListActivity.this, SettingsActivity.class);
            startActivityForResult(intent, CODE_START_SETTINGS);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        EventBus.getDefault().post(new AppListForegroundEvent(true));
    }

    @Override
    protected void onStop() {
        LogUtil.d(TAG, "onStop");

        super.onStop();

        EventBus.getDefault().post(new AppListForegroundEvent(false));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                drawerLayout.openDrawer(GravityCompat.START);
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    protected void onDestroy() {
        LogUtil.d(TAG, "onDestroy");

        super.onDestroy();

        unbindService(mConnection);

        mHomeKeyWatcher.stopWatch();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case CODE_REQUEST_PERMISSION:
                if (!checkIfGetPermission()) {
                    Toast.makeText(AppListActivity.this,
                            R.string.toast_info_request_permission_failed, Toast.LENGTH_SHORT).show();
                }
                break;
            case CODE_START_SETTINGS:
                if (!isLockEnrolled()) {
                    finish();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mService.isProtectedAppListChanged() && (keyCode == KeyEvent.KEYCODE_BACK)) {
            showConfigChangedDialog();

            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private void showConfigChangedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_warning_white_48dp)
                .setTitle(R.string.dialog_title_warning)
                .setCancelable(false)
                .setMessage(R.string.dialog_content_config_changed)
                .setPositiveButton(R.string.dialog_action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // User choose to discard changes, so we just quit.
                        mService.discardProtectListSettings();
                        dialog.dismiss();
                        finish();
                    }
                })
                .setNegativeButton(R.string.dialog_action_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        builder.create().show();
    }

    private void showPermissionRequestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_warning_white_48dp)
                .setTitle(R.string.dialog_title_warning)
                .setCancelable(false)
                .setMessage(R.string.dialog_content_request_permission)
                .setPositiveButton(R.string.dialog_action_yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                        AppListActivity.this.startActivityForResult(intent, CODE_REQUEST_PERMISSION);
                    }
                })
                .setNegativeButton(R.string.dialog_action_no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(AppListActivity.this,
                                R.string.toast_info_request_permission_failed, Toast.LENGTH_SHORT).show();
                    }
                });
        builder.create().show();
    }

    private boolean checkIfGetPermission() {
        AppOpsManager appOps = (AppOpsManager) this
                .getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), this.getPackageName());
        return (mode == AppOpsManager.MODE_ALLOWED);
    }

    private boolean isLockEnrolled() {
        return mPrefs.getBoolean(LOCK_ENROLLED, false);
    }
}
