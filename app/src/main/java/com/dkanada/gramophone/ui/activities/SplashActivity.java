package com.dkanada.gramophone.ui.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.dkanada.gramophone.App;
import com.dkanada.gramophone.R;
import com.dkanada.gramophone.helper.NetworkConnectionHelper;
import com.dkanada.gramophone.ui.activities.base.AbsBaseActivity;
import com.kabouzeid.appthemehelper.ThemeStore;

import org.jellyfin.apiclient.interaction.AndroidCredentialProvider;
import org.jellyfin.apiclient.interaction.ConnectionResult;
import org.jellyfin.apiclient.interaction.EmptyResponse;
import org.jellyfin.apiclient.interaction.Response;
import org.jellyfin.apiclient.interaction.VolleyHttpClient;
import org.jellyfin.apiclient.interaction.connectionmanager.ConnectionManager;
import org.jellyfin.apiclient.interaction.http.IAsyncHttpClient;
import org.jellyfin.apiclient.logging.AndroidLogger;
import org.jellyfin.apiclient.model.apiclient.ConnectionState;
import org.jellyfin.apiclient.model.logging.ILogger;
import org.jellyfin.apiclient.model.serialization.GsonJsonSerializer;
import org.jellyfin.apiclient.model.serialization.IJsonSerializer;

import butterknife.BindView;
import butterknife.ButterKnife;

public class SplashActivity extends AbsBaseActivity implements View.OnClickListener {
    public static final String TAG = SplashActivity.class.getSimpleName();

    public AndroidCredentialProvider credentialProvider;
    public ConnectionManager connectionManager;

    @BindView(R.id.splash_logo)
    ImageView splashLogo;
    @BindView(R.id.retry_connection)
    Button retryConnection;
    @BindView(R.id.text_area)
    LinearLayout textArea;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        setDrawUnderStatusbar();
        ButterKnife.bind(this);

        setStatusbarColorAuto();
        setNavigationbarColorAuto();
        setTaskDescriptionColorAuto();

        setUpViews();

        IJsonSerializer jsonSerializer = new GsonJsonSerializer();
        ILogger logger = new AndroidLogger(TAG);
        IAsyncHttpClient httpClient = new VolleyHttpClient(logger, this);

        credentialProvider = new AndroidCredentialProvider(jsonSerializer, this, logger);
        connectionManager = App.getConnectionManager(this, jsonSerializer, logger, httpClient);

        if (detectBatteryOptimization()) {
            showBatteryOptimizationDialog();
        } else {
            tryConnect();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        overridePendingTransition(0, 0);
    }

    private void setUpViews() {
        int primaryColor = ThemeStore.primaryColor(this);

        retryConnection.setBackgroundColor(primaryColor);

        setUpOnClickListeners();
    }

    private void setUpOnClickListeners() {
        retryConnection.setOnClickListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == retryConnection) {
            tryConnect();
        }
    }

    public void tryConnect() {
        if (NetworkConnectionHelper.checkNetworkConnection(this)) {
            login();
        } else {
            splashLogo.setVisibility(View.GONE);
            textArea.setVisibility(View.VISIBLE);
        }
    }

    private boolean detectBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String packageName = getPackageName();
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            return !pm.isIgnoringBatteryOptimizations(packageName);
        }
        return false;
    }

    private void showBatteryOptimizationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(SplashActivity.this);
        builder.setMessage(R.string.action_disable_battery_optimizations_message)
                .setTitle(R.string.action_disable_battery_optimizations_title)
                .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        login();
                    }
                })
                .setPositiveButton(R.string.action_go_to_optimization_settings, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        login();
                        openPowerSettings(SplashActivity.this);
                    }
                })
                .show();
    }

    private void openPowerSettings(Context context) {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
        context.startActivity(intent);
    }

    public void login() {
        if (credentialProvider.GetCredentials().getServers().size() == 0) {
            Intent intent = new Intent(this, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        } else {
            final Context context = this;
            connectionManager.Connect(credentialProvider.GetCredentials().getServers().get(0), new Response<ConnectionResult>() {
                @Override
                public void onResponse(ConnectionResult result) {
                    if (result.getState() != ConnectionState.SignedIn) {
                        connectionManager.DeleteServer(credentialProvider.GetCredentials().getServers().get(0).getId(), new EmptyResponse());

                        Intent intent = new Intent(context, LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(intent);
                    } else {
                        App.setApiClient(result.getApiClient());

                        Intent intent = new Intent(context, MainActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(intent);
                    }
                }
            });
        }
    }
}
