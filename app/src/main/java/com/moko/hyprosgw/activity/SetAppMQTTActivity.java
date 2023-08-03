package com.moko.hyprosgw.activity;

import android.content.Intent;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.widget.RadioGroup;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.elvishew.xlog.XLog;
import com.google.gson.Gson;
import com.moko.hyprosgw.AppConstants;
import com.moko.hyprosgw.R;
import com.moko.hyprosgw.adapter.MQTTFragmentAdapter;
import com.moko.hyprosgw.base.BaseActivity;
import com.moko.hyprosgw.databinding.ActivityMqttAppBinding;
import com.moko.hyprosgw.dialog.AlertMessageDialog;
import com.moko.hyprosgw.entity.MQTTConfig;
import com.moko.hyprosgw.fragment.GeneralFragment;
import com.moko.hyprosgw.fragment.SSLFragment;
import com.moko.hyprosgw.fragment.UserFragment;
import com.moko.hyprosgw.utils.SPUtiles;
import com.moko.hyprosgw.utils.ToastUtils;
import com.moko.support.scannergw.MQTTSupport;
import com.moko.support.scannergw.event.MQTTConnectionCompleteEvent;
import com.moko.support.scannergw.event.MQTTConnectionFailureEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.UUID;

public class SetAppMQTTActivity extends BaseActivity<ActivityMqttAppBinding> implements RadioGroup.OnCheckedChangeListener {
    private final String FILTER_ASCII = "[ -~]*";
    private GeneralFragment generalFragment;
    private UserFragment userFragment;
    private SSLFragment sslFragment;
    private ArrayList<Fragment> fragments;
    private MQTTConfig mqttConfig;

    @Override
    protected void onCreate() {
        String MQTTConfigStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        if (TextUtils.isEmpty(MQTTConfigStr)) {
            mqttConfig = new MQTTConfig();
            mqttConfig.host = "atk-locatoren.kisabt.uke.de";
            mqttConfig.port = "1883";
            mqttConfig.clientId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } else {
            Gson gson = new Gson();
            mqttConfig = gson.fromJson(MQTTConfigStr, MQTTConfig.class);
        }
        InputFilter filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }
            return null;
        };
        mBind.etMqttHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etMqttClientId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        createFragment();
        initData();
        MQTTFragmentAdapter adapter = new MQTTFragmentAdapter(this);
        adapter.setFragmentList(fragments);
        mBind.vpMqtt.setAdapter(adapter);
        mBind.vpMqtt.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                if (position == 0) {
                    mBind.rbGeneral.setChecked(true);
                } else if (position == 1) {
                    mBind.rbUser.setChecked(true);
                } else if (position == 2) {
                    mBind.rbSsl.setChecked(true);
                }
            }
        });
        mBind.vpMqtt.setOffscreenPageLimit(3);
        mBind.rgMqtt.setOnCheckedChangeListener(this);
    }

    @Override
    protected ActivityMqttAppBinding getViewBinding() {
        return ActivityMqttAppBinding.inflate(getLayoutInflater());
    }

    private void createFragment() {
        fragments = new ArrayList<>();
        generalFragment = GeneralFragment.newInstance();
        userFragment = UserFragment.newInstance();
        sslFragment = SSLFragment.newInstance();
        fragments.add(generalFragment);
        fragments.add(userFragment);
        fragments.add(sslFragment);
    }

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 10)
    public void onMQTTConnectionCompleteEvent(MQTTConnectionCompleteEvent event) {
        EventBus.getDefault().cancelEventDelivery(event);
        String mqttConfigStr = new Gson().toJson(mqttConfig, MQTTConfig.class);
        SPUtiles.setStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, mqttConfigStr);
        ToastUtils.showToast(SetAppMQTTActivity.this, getString(R.string.success));
        dismissLoadingProgressDialog();
        Intent intent = new Intent();
        intent.putExtra(AppConstants.EXTRA_KEY_MQTT_CONFIG_APP, mqttConfigStr);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTConnectionFailureEvent(MQTTConnectionFailureEvent event) {
        ToastUtils.showToast(SetAppMQTTActivity.this, getString(R.string.mqtt_connect_failed));
        dismissLoadingProgressDialog();
        finish();
    }

    private void initData() {
        mBind.etMqttHost.setText(mqttConfig.host);
        mBind.etMqttPort.setText(mqttConfig.port);
        mBind.etMqttClientId.setText(mqttConfig.clientId);
        generalFragment.setCleanSession(mqttConfig.cleanSession);
        generalFragment.setQos(mqttConfig.qos);
        generalFragment.setKeepAlive(mqttConfig.keepAlive);
        userFragment.setUserName(mqttConfig.username);
        userFragment.setPassword(mqttConfig.password);
        sslFragment.setConnectMode(mqttConfig.connectMode);
        sslFragment.setCAPath(mqttConfig.caPath);
        sslFragment.setClientKeyPath(mqttConfig.clientKeyPath);
        sslFragment.setClientCertPath(mqttConfig.clientCertPath);
    }

    public void back(View view) {
        back();
    }

    @Override
    public void onBackPressed() {
        back();
    }

    private void back() {
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setMessage("Please confirm whether to save the modified parameters?");
        dialog.setConfirm("YES");
        dialog.setCancel("NO");
        dialog.setOnAlertConfirmListener(() -> onSave(null));
        dialog.setOnAlertCancelListener(this::finish);
        dialog.show(getSupportFragmentManager());
    }

    @Override
    public void onCheckedChanged(RadioGroup group, @IdRes int checkedId) {
        if (checkedId == R.id.rb_general) {
            mBind.vpMqtt.setCurrentItem(0);
        } else if (checkedId == R.id.rb_user) {
            mBind.vpMqtt.setCurrentItem(1);
        } else if (checkedId == R.id.rb_ssl) {
            mBind.vpMqtt.setCurrentItem(2);
        }
    }

    public void onSave(View view) {
        String host = mBind.etMqttHost.getText().toString().replaceAll(" ", "");
        String port = mBind.etMqttPort.getText().toString();
        String clientId = mBind.etMqttClientId.getText().toString().replaceAll(" ", "");

        if (TextUtils.isEmpty(host)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_host));
            return;
        }
        if (TextUtils.isEmpty(port)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port_empty));
            return;
        }
        if (Integer.parseInt(port) > 65535 || Integer.parseInt(port) < 1) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_port));
            return;
        }
        if (TextUtils.isEmpty(clientId)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_client_id_empty));
            return;
        }
        if (!generalFragment.isValid() || !sslFragment.isValid()) return;
        mqttConfig.host = host;
        mqttConfig.port = port;
        mqttConfig.clientId = clientId;
        mqttConfig.cleanSession = generalFragment.isCleanSession();
        mqttConfig.qos = generalFragment.getQos();
        mqttConfig.keepAlive = generalFragment.getKeepAlive();
        mqttConfig.keepAlive = generalFragment.getKeepAlive();
        mqttConfig.username = userFragment.getUsername();
        mqttConfig.password = userFragment.getPassword();
        mqttConfig.connectMode = sslFragment.getConnectMode();
        mqttConfig.caPath = sslFragment.getCaPath();
        mqttConfig.clientKeyPath = sslFragment.getClientKeyPath();
        mqttConfig.clientCertPath = sslFragment.getClientCertPath();

        String mqttConfigStr = new Gson().toJson(mqttConfig, MQTTConfig.class);
        MQTTSupport.getInstance().disconnectMqtt();
        showLoadingProgressDialog();
        mBind.etMqttHost.postDelayed(() -> {
            try {
                MQTTSupport.getInstance().connectMqtt(mqttConfigStr);
            } catch (FileNotFoundException e) {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "The SSL certificates path is invalid, please select a valid file path and save it.");
                // 读取stacktrace信息
                final Writer result = new StringWriter();
                final PrintWriter printWriter = new PrintWriter(result);
                e.printStackTrace(printWriter);
                StringBuilder errorReport = new StringBuilder();
                errorReport.append(result.toString());
                XLog.e(errorReport.toString());
            }
        }, 2000);
    }

    public void selectCertificate(View view) {
        if (isWindowLocked()) return;
        sslFragment.selectCertificate();
    }

    public void selectCAFile(View view) {
        if (isWindowLocked()) return;
        sslFragment.selectCAFile();
    }

    public void selectKeyFile(View view) {
        if (isWindowLocked()) return;
        sslFragment.selectKeyFile();
    }

    public void selectCertFile(View view) {
        if (isWindowLocked()) return;
        sslFragment.selectCertFile();
    }
}
