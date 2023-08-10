package com.moko.hyprosgw.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.elvishew.xlog.XLog;
import com.github.lzyzsd.circleprogress.DonutProgress;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.ble.lib.MokoConstants;
import com.moko.ble.lib.event.ConnectStatusEvent;
import com.moko.ble.lib.event.OrderTaskResponseEvent;
import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.task.OrderTaskResponse;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.hyprosgw.AppConstants;
import com.moko.hyprosgw.R;
import com.moko.hyprosgw.adapter.MQTTFragmentAdapter;
import com.moko.hyprosgw.base.BaseActivity;
import com.moko.hyprosgw.databinding.ActivityMqttDeviceBinding;
import com.moko.hyprosgw.db.DBTools;
import com.moko.hyprosgw.dialog.BottomDialog;
import com.moko.hyprosgw.dialog.CustomDialog;
import com.moko.hyprosgw.entity.MQTTConfig;
import com.moko.hyprosgw.entity.MokoDevice;
import com.moko.hyprosgw.fragment.GeneralDeviceFragment;
import com.moko.hyprosgw.fragment.SSLDeviceFragment;
import com.moko.hyprosgw.fragment.UserDeviceFragment;
import com.moko.hyprosgw.utils.SPUtiles;
import com.moko.hyprosgw.utils.ToastUtils;
import com.moko.support.scannergw.MQTTConstants;
import com.moko.support.scannergw.MQTTSupport;
import com.moko.support.scannergw.MokoSupport;
import com.moko.support.scannergw.OrderTaskAssembler;
import com.moko.support.scannergw.entity.MsgNotify;
import com.moko.support.scannergw.entity.OrderCHAR;
import com.moko.support.scannergw.entity.ParamsKeyEnum;
import com.moko.support.scannergw.entity.ParamsLongKeyEnum;
import com.moko.support.scannergw.event.MQTTMessageArrivedEvent;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;

public class SetDeviceMQTTActivity extends BaseActivity<ActivityMqttDeviceBinding> implements RadioGroup.OnCheckedChangeListener {
    private final String FILTER_ASCII = "[ -~]*";

    private GeneralDeviceFragment generalFragment;
    private UserDeviceFragment userFragment;
    private SSLDeviceFragment sslFragment;
    private ArrayList<Fragment> fragments;

    private MQTTConfig mqttAppConfig;
    private MQTTConfig mqttDeviceConfig = new MQTTConfig();

    private ArrayList<String> mTimeZones;
    private int mSelectedTimeZone;
    private String mWifiSSID;
    private String mWifiPassword;
    private String mSelectedDeviceName;
    private String mSelectedDeviceMac;
    private int mSelectedDeviceType;
    private boolean savedParamsError;
    private CustomDialog mqttConnDialog;
    private DonutProgress donutProgress;
    private boolean isSettingSuccess;
    private boolean isDeviceConnectSuccess;
    private Handler mHandler;
    private InputFilter filter;

    @Override
    protected void onCreate() {
        String MQTTConfigStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        mqttAppConfig = new Gson().fromJson(MQTTConfigStr, MQTTConfig.class);
        mSelectedDeviceName = getIntent().getStringExtra(AppConstants.EXTRA_KEY_SELECTED_DEVICE_NAME);
        mSelectedDeviceMac = getIntent().getStringExtra(AppConstants.EXTRA_KEY_SELECTED_DEVICE_MAC);
        mSelectedDeviceType = getIntent().getIntExtra(AppConstants.EXTRA_KEY_SELECTED_DEVICE_TYPE, 0);
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }
            return null;
        };
        mHandler = new Handler(Looper.getMainLooper());
        mBind.etMqttHost.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etMqttClientId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        mBind.etMqttSubscribeTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        mBind.etMqttPublishTopic.setFilters(new InputFilter[]{new InputFilter.LengthFilter(128), filter});
        mBind.etDeviceId.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), filter});
        mBind.etNtpUrl.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        createFragment();
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
        mTimeZones = new ArrayList<>();
        for (int i = -24; i <= 28; i++) {
            if (i < 0) {
                if (i % 2 == 0) {
                    int j = Math.abs(i / 2);
                    mTimeZones.add(String.format("UTC-%02d:00", j));
                } else {
                    int j = Math.abs((i + 1) / 2);
                    mTimeZones.add(String.format("UTC-%02d:30", j));
                }
            } else if (i == 0) {
                mTimeZones.add("UTC");
            } else {
                if (i % 2 == 0) {
                    mTimeZones.add(String.format("UTC+%02d:00", i / 2));
                } else {
                    mTimeZones.add(String.format("UTC+%02d:30", (i - 1) / 2));
                }
            }
        }
        getDefaultInfo();
    }

    private void getDefaultInfo() {
        showLoadingProgressDialog();
        ArrayList<OrderTask> orderTasks = new ArrayList<>();
        orderTasks.add(OrderTaskAssembler.getMQTTHost());
        orderTasks.add(OrderTaskAssembler.getMQTTPort());
        orderTasks.add(OrderTaskAssembler.getMQTTClientId());
        orderTasks.add(OrderTaskAssembler.getMQTTSubscribeTopic());
        orderTasks.add(OrderTaskAssembler.getMQTTPublishTopic());
        orderTasks.add(OrderTaskAssembler.getMQTTCleanSession());
        orderTasks.add(OrderTaskAssembler.getMQTTQos());
        orderTasks.add(OrderTaskAssembler.getMQTTKeepAlive());
        orderTasks.add(OrderTaskAssembler.getMQTTUsername());
        orderTasks.add(OrderTaskAssembler.getMQTTPassword());
        orderTasks.add(OrderTaskAssembler.getMQTTConnectMode());
        orderTasks.add(OrderTaskAssembler.getMQTTDeviceId());
        orderTasks.add(OrderTaskAssembler.getMQTTNtpUrl());
        orderTasks.add(OrderTaskAssembler.getTimezone());
        MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
    }

    @Override
    protected ActivityMqttDeviceBinding getViewBinding() {
        return ActivityMqttDeviceBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.POSTING, priority = 100)
    public void onConnectStatusEvent(ConnectStatusEvent event) {
        String action = event.getAction();
        if (MokoConstants.ACTION_DISCONNECTED.equals(action)) {
            if (isSettingSuccess) {
                EventBus.getDefault().cancelEventDelivery(event);
                return;
            }
            runOnUiThread(() -> {
                dismissLoadingProgressDialog();
                finish();
            });
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onOrderTaskResponseEvent(OrderTaskResponseEvent event) {
        final String action = event.getAction();
        if (MokoConstants.ACTION_ORDER_FINISH.equals(action)) {
            dismissLoadingProgressDialog();
        }
        if (MokoConstants.ACTION_ORDER_RESULT.equals(action)) {
            OrderTaskResponse response = event.getResponse();
            OrderCHAR orderCHAR = (OrderCHAR) response.orderCHAR;
            int responseType = response.responseType;
            byte[] value = response.responseValue;
            if (orderCHAR == OrderCHAR.CHAR_PARAMS) {
                if (value.length >= 4) {
                    int header = value[0] & 0xFF;// 0xED
                    int flag = value[1] & 0xFF;// read or write
                    int cmd = value[2] & 0xFF;
                    if (header == 0xEE) {
                        ParamsLongKeyEnum configKeyEnum = ParamsLongKeyEnum.fromParamKey(cmd);
                        if (configKeyEnum == null) {
                            return;
                        }
                        if (flag == 0x01) {
                            // write
                            int result = value[4] & 0xFF;
                            switch (configKeyEnum) {
                                case KEY_MQTT_USERNAME:
                                case KEY_MQTT_PASSWORD:
                                case KEY_MQTT_CA:
                                case KEY_MQTT_CLIENT_KEY:
                                case KEY_MQTT_CLIENT_CERT:
                                    if (result != 1) {
                                        savedParamsError = true;
                                    }
                                    break;
                            }
                        }
                        if (flag == 0x00) {
                            int length = MokoUtils.toInt(Arrays.copyOfRange(value, 3, 5));
                            // read
                            switch (configKeyEnum) {
                                case KEY_MQTT_USERNAME:
                                    mqttDeviceConfig.username = new String(Arrays.copyOfRange(value, 5, 5 + length));
                                    userFragment.setUserName(mqttDeviceConfig.username);
                                    userFragment.setUserName();
                                    break;
                                case KEY_MQTT_PASSWORD:
                                    mqttDeviceConfig.password = new String(Arrays.copyOfRange(value, 5, 5 + length));
                                    userFragment.setPassword(mqttDeviceConfig.password);
                                    userFragment.setPassword();
                                    break;
                            }
                        }
                    }
                    if (header == 0xED) {
                        ParamsKeyEnum configKeyEnum = ParamsKeyEnum.fromParamKey(cmd);
                        if (configKeyEnum == null) return;
                        int length = value[3] & 0xFF;
                        if (flag == 0x01) {
                            // write
                            int result = value[4] & 0xFF;
                            switch (configKeyEnum) {
                                case KEY_MQTT_HOST:
                                case KEY_MQTT_PORT:
                                case KEY_MQTT_CLIENT_ID:
                                case KEY_MQTT_SUBSCRIBE_TOPIC:
                                case KEY_MQTT_PUBLISH_TOPIC:
                                case KEY_MQTT_CLEAN_SESSION:
                                case KEY_MQTT_QOS:
                                case KEY_MQTT_KEEP_ALIVE:
                                case KEY_WIFI_SSID:
                                case KEY_WIFI_PASSWORD:
                                case KEY_MQTT_DEVICE_ID:
                                case KEY_NTP_URL:
                                case KEY_NTP_TIME_ZONE:
                                case KEY_NTP_TIME_ZONE_PRO:
                                case KEY_MQTT_CONNECT_MODE:
                                case KEY_CHANNEL_DOMAIN:
                                    if (result != 1) {
                                        savedParamsError = true;
                                    }
                                    break;
                                case KEY_EXIT_CONFIG_MODE:
                                    if (result != 1) {
                                        savedParamsError = true;
                                    }
                                    if (savedParamsError) {
                                        ToastUtils.showToast(this, "Opps！Save failed. Please check the input characters and try again.");
                                    } else {
                                        isSettingSuccess = true;
                                        showConnMqttDialog();
                                        subscribeTopic();
                                    }
                                    break;
                            }
                        }
                        if (flag == 0x00) {
                            if (length == 0) return;
                            // read
                            switch (configKeyEnum) {
                                case KEY_MQTT_CONNECT_MODE:
                                    mqttDeviceConfig.connectMode = value[4];
                                    sslFragment.setConnectMode(mqttDeviceConfig.connectMode);
                                    sslFragment.setConnectMode();
                                    break;
                                case KEY_MQTT_HOST:
                                    mqttDeviceConfig.host = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                    mBind.etMqttHost.setText(mqttDeviceConfig.host);
                                    mBind.etMqttHost.setSelection(mBind.etMqttHost.getText().length());
                                    break;
                                case KEY_MQTT_PORT:
                                    mqttDeviceConfig.port = String.valueOf(MokoUtils.toInt(Arrays.copyOfRange(value, 4, 4 + length)));
                                    mBind.etMqttPort.setText(mqttDeviceConfig.port);
                                    mBind.etMqttPort.setSelection(mBind.etMqttPort.getText().length());
                                    break;
                                case KEY_MQTT_CLEAN_SESSION:
                                    mqttDeviceConfig.cleanSession = value[4] == 1;
                                    generalFragment.setCleanSession(mqttDeviceConfig.cleanSession);
                                    generalFragment.setCleanSession();
                                    break;
                                case KEY_MQTT_KEEP_ALIVE:
                                    mqttDeviceConfig.keepAlive = value[4] & 0xFF;
                                    generalFragment.setKeepAlive(mqttDeviceConfig.keepAlive);
                                    generalFragment.setKeepAlive();
                                    break;
                                case KEY_MQTT_QOS:
                                    mqttDeviceConfig.qos = value[4] & 0xFF;
                                    generalFragment.setQos(mqttDeviceConfig.qos);
                                    generalFragment.setQos();
                                    break;
                                case KEY_MQTT_CLIENT_ID:
                                    mqttDeviceConfig.clientId = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                    mBind.etMqttClientId.setText(mqttDeviceConfig.clientId);
                                    mBind.etMqttClientId.setSelection(mBind.etMqttClientId.getText().length());
                                    break;
                                case KEY_MQTT_DEVICE_ID:
                                    mqttDeviceConfig.deviceId = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                    mBind.etDeviceId.setText(mqttDeviceConfig.deviceId);
                                    mBind.etDeviceId.setSelection(mBind.etDeviceId.getText().length());
                                    break;
                                case KEY_MQTT_SUBSCRIBE_TOPIC:
                                    mqttDeviceConfig.topicSubscribe = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                    mBind.etMqttSubscribeTopic.setText(mqttDeviceConfig.topicSubscribe);
                                    mBind.etMqttSubscribeTopic.setSelection(mBind.etMqttSubscribeTopic.getText().length());
                                    break;
                                case KEY_MQTT_PUBLISH_TOPIC:
                                    mqttDeviceConfig.topicPublish = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                    mBind.etMqttPublishTopic.setText(mqttDeviceConfig.topicPublish);
                                    mBind.etMqttPublishTopic.setSelection(mBind.etMqttPublishTopic.getText().length());
                                    break;
                                case KEY_DEVICE_NAME:
                                    mSelectedDeviceName = new String(Arrays.copyOfRange(value, 4, 4 + length));
                                    break;
                                case KEY_DEVICE_MAC:
                                    String mac = MokoUtils.bytesToHexString(Arrays.copyOfRange(value, 4, 4 + length));
                                    mSelectedDeviceMac = mac.toUpperCase();
                                    break;

                                case KEY_NTP_URL:
                                    mqttDeviceConfig.ntpUrl = new String(Arrays.copyOfRange(value, 4, value.length));
                                    mBind.etNtpUrl.setText(mqttDeviceConfig.ntpUrl);
                                    mBind.etNtpUrl.setSelection(mBind.etNtpUrl.getText().length());
                                    break;

                                case KEY_NTP_TIME_ZONE_PRO:
                                    mSelectedTimeZone = mqttDeviceConfig.timeZone = value[4] + 24;
                                    mBind.tvTimeZone.setText(mTimeZones.get(mSelectedTimeZone));
                                    break;
                            }
                        }
                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        final String topic = event.getTopic();
        final String message = event.getMessage();
        if (TextUtils.isEmpty(topic) || isDeviceConnectSuccess) return;
        if (TextUtils.isEmpty(message)) return;
        int msg_id;
        try {
            JsonObject object = new Gson().fromJson(message, JsonObject.class);
            JsonElement element = object.get("msg_id");
            msg_id = element.getAsInt();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (msg_id != MQTTConstants.NOTIFY_MSG_ID_NETWORKING_STATUS) return;
        Type type = new TypeToken<MsgNotify<Object>>() {
        }.getType();
        MsgNotify<Object> msgNotify = new Gson().fromJson(message, type);
        final String deviceId = msgNotify.device_info.device_id;
        if (!mqttDeviceConfig.deviceId.equals(deviceId)) return;
        if (donutProgress == null) return;
        if (!isDeviceConnectSuccess) {
            isDeviceConnectSuccess = true;
            donutProgress.setProgress(100);
            donutProgress.setText(100 + "%");
            // 关闭进度条弹框，保存数据，跳转修改设备名称页面
            mBind.etMqttHost.postDelayed(() -> {
                dismissConnMqttDialog();
                MokoDevice mokoDevice = DBTools.getInstance(SetDeviceMQTTActivity.this).selectDeviceByMac(mSelectedDeviceMac);
                String mqttConfigStr = new Gson().toJson(mqttDeviceConfig, MQTTConfig.class);
                if (mokoDevice == null) {
                    mokoDevice = new MokoDevice();
                    mokoDevice.name = mSelectedDeviceName;
                    mokoDevice.nickName = mSelectedDeviceName;
                    mokoDevice.mac = mSelectedDeviceMac;
                    mokoDevice.mqttInfo = mqttConfigStr;
                    mokoDevice.topicSubscribe = mqttDeviceConfig.topicSubscribe;
                    mokoDevice.topicPublish = mqttDeviceConfig.topicPublish;
                    mokoDevice.deviceId = mqttDeviceConfig.deviceId;
                    mokoDevice.deviceType = mSelectedDeviceType;
                    DBTools.getInstance(SetDeviceMQTTActivity.this).insertDevice(mokoDevice);
                } else {
                    mokoDevice.name = mSelectedDeviceName;
                    mokoDevice.mac = mSelectedDeviceMac;
                    mokoDevice.mqttInfo = mqttConfigStr;
                    mokoDevice.topicSubscribe = mqttDeviceConfig.topicSubscribe;
                    mokoDevice.topicPublish = mqttDeviceConfig.topicPublish;
                    mokoDevice.deviceId = mqttDeviceConfig.deviceId;
                    mokoDevice.deviceType = mSelectedDeviceType;
                    DBTools.getInstance(SetDeviceMQTTActivity.this).updateDevice(mokoDevice);
                }
                Intent modifyIntent = new Intent(SetDeviceMQTTActivity.this, ModifyNameActivity.class);
                modifyIntent.putExtra(AppConstants.EXTRA_KEY_DEVICE, mokoDevice);
                startActivity(modifyIntent);
            }, 1000);
        }
    }

    private void createFragment() {
        fragments = new ArrayList<>();
        generalFragment = GeneralDeviceFragment.newInstance();
        userFragment = UserDeviceFragment.newInstance();
        sslFragment = SSLDeviceFragment.newInstance();
        fragments.add(generalFragment);
        fragments.add(userFragment);
        fragments.add(sslFragment);
    }

    public void back(View view) {
        back();
    }

    @Override
    public void onBackPressed() {
        back();
    }

    private void back() {
        MokoSupport.getInstance().disConnectBle();
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
        String deviceId = mBind.etDeviceId.getText().toString().replaceAll(" ", "");
        String topicSubscribe = mBind.etMqttSubscribeTopic.getText().toString();
        String topicPublish = mBind.etMqttPublishTopic.getText().toString();
        String ntpUrl = mBind.etNtpUrl.getText().toString().replaceAll(" ", "");

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
        if (TextUtils.isEmpty(topicSubscribe)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_subscribe));
            return;
        }
        if (TextUtils.isEmpty(topicPublish)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_topic_publish));
            return;
        }
        if (TextUtils.isEmpty(deviceId)) {
            ToastUtils.showToast(this, getString(R.string.mqtt_verify_device_id_empty));
            return;
        }
        if (!generalFragment.isValid() || !sslFragment.isValid()) return;
        mqttDeviceConfig.host = host;
        mqttDeviceConfig.port = port;
        mqttDeviceConfig.clientId = clientId;
        mqttDeviceConfig.cleanSession = generalFragment.isCleanSession();
        mqttDeviceConfig.qos = generalFragment.getQos();
        mqttDeviceConfig.keepAlive = generalFragment.getKeepAlive();
        mqttDeviceConfig.keepAlive = generalFragment.getKeepAlive();
        mqttDeviceConfig.topicSubscribe = topicSubscribe;
        mqttDeviceConfig.topicPublish = topicPublish;
        mqttDeviceConfig.username = userFragment.getUsername();
        mqttDeviceConfig.password = userFragment.getPassword();
        mqttDeviceConfig.connectMode = sslFragment.getmConnectMode();
        mqttDeviceConfig.caPath = sslFragment.getCaPath();
        mqttDeviceConfig.clientKeyPath = sslFragment.getClientKeyPath();
        mqttDeviceConfig.clientCertPath = sslFragment.getClientCertPath();
        mqttDeviceConfig.deviceId = deviceId;
        mqttDeviceConfig.ntpUrl = ntpUrl;
        mqttDeviceConfig.timeZone = mSelectedTimeZone - 24;

        if (!mqttDeviceConfig.topicPublish.isEmpty() && !mqttDeviceConfig.topicSubscribe.isEmpty()
                && mqttDeviceConfig.topicPublish.equals(mqttDeviceConfig.topicSubscribe)) {
            ToastUtils.showToast(this, "Subscribed and published topic can't be same !");
            return;
        }
        if ("{device_name}/{device_id}/app_to_device".equals(mqttDeviceConfig.topicSubscribe)) {
            mqttDeviceConfig.topicSubscribe = String.format("%s/%s/app_to_device", mSelectedDeviceName, deviceId);
        }
        if ("{device_name}/{device_id}/device_to_app".equals(mqttDeviceConfig.topicPublish)) {
            mqttDeviceConfig.topicPublish = String.format("%s/%s/device_to_app", mSelectedDeviceName, deviceId);
        }
        showWifiInputDialog();
    }

    private void showWifiInputDialog() {
        View wifiInputView = LayoutInflater.from(this).inflate(R.layout.wifi_input_content, mBind.getRoot(), false);
        final EditText etSSID = wifiInputView.findViewById(R.id.et_ssid);
        final EditText etPassword = wifiInputView.findViewById(R.id.et_password);
        etSSID.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32), filter});
        etPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(64), filter});
        etSSID.setText("uke-tec-1");
        etSSID.setSelection(etSSID.getText().length());
        etPassword.setText("$uke4loc&2track#dev23");
        etPassword.setSelection(etPassword.getText().length());
        CustomDialog dialog = new CustomDialog.Builder(this)
                .setContentView(wifiInputView)
                .setPositiveButton(R.string.cancel, (dialog1, which) -> dialog1.dismiss())
                .setNegativeButton(R.string.confirm, (dialog12, which) -> {
                    mWifiSSID = etSSID.getText().toString();
                    // 获取WIFI后，连接成功后发给设备
                    if (TextUtils.isEmpty(mWifiSSID)) {
                        ToastUtils.showToast(SetDeviceMQTTActivity.this, getString(R.string.wifi_verify_empty));
                        return;
                    }
                    dialog12.dismiss();
                    mWifiPassword = etPassword.getText().toString();
                    setMQTTDeviceConfig();
                }).create();
        dialog.show();
    }

    private void setMQTTDeviceConfig() {
        try {
            showLoadingProgressDialog();
            ArrayList<OrderTask> orderTasks = new ArrayList<>();
            orderTasks.add(OrderTaskAssembler.getDeviceMac());
            orderTasks.add(OrderTaskAssembler.getDeviceName());
            orderTasks.add(OrderTaskAssembler.setMqttHost(mqttDeviceConfig.host));
            orderTasks.add(OrderTaskAssembler.setMqttPort(Integer.parseInt(mqttDeviceConfig.port)));
            orderTasks.add(OrderTaskAssembler.setMqttClientId(mqttDeviceConfig.clientId));
            orderTasks.add(OrderTaskAssembler.setMqttCleanSession(mqttDeviceConfig.cleanSession ? 1 : 0));
            orderTasks.add(OrderTaskAssembler.setMqttQos(mqttDeviceConfig.qos));
            orderTasks.add(OrderTaskAssembler.setMqttKeepAlive(mqttDeviceConfig.keepAlive));
            orderTasks.add(OrderTaskAssembler.setWifiSSID(mWifiSSID));
            orderTasks.add(OrderTaskAssembler.setWifiPassword(mWifiPassword));
            orderTasks.add(OrderTaskAssembler.setMqttDeivceId(mqttDeviceConfig.deviceId));
            orderTasks.add(OrderTaskAssembler.setMqttPublishTopic(mqttDeviceConfig.topicPublish));
            orderTasks.add(OrderTaskAssembler.setMqttSubscribeTopic(mqttDeviceConfig.topicSubscribe));
            if (!TextUtils.isEmpty(mqttDeviceConfig.username)) {
                orderTasks.add(OrderTaskAssembler.setMqttUserName(mqttDeviceConfig.username));
            }
            if (!TextUtils.isEmpty(mqttDeviceConfig.password)) {
                orderTasks.add(OrderTaskAssembler.setMqttPassword(mqttDeviceConfig.password));
            }
            orderTasks.add(OrderTaskAssembler.setMqttConnectMode(mqttDeviceConfig.connectMode));
            if (mqttDeviceConfig.connectMode == 2) {
                File file = new File(mqttDeviceConfig.caPath);
                orderTasks.add(OrderTaskAssembler.setCA(file));
            } else if (mqttDeviceConfig.connectMode == 3) {
                File caFile = new File(mqttDeviceConfig.caPath);
                orderTasks.add(OrderTaskAssembler.setCA(caFile));
                File clientKeyFile = new File(mqttDeviceConfig.clientKeyPath);
                orderTasks.add(OrderTaskAssembler.setClientKey(clientKeyFile));
                File clientCertFile = new File(mqttDeviceConfig.clientCertPath);
                orderTasks.add(OrderTaskAssembler.setClientCert(clientCertFile));
            }
            if (!TextUtils.isEmpty(mqttDeviceConfig.ntpUrl)) {
                orderTasks.add(OrderTaskAssembler.setNTPUrl(mqttDeviceConfig.ntpUrl));
            }
            orderTasks.add(OrderTaskAssembler.setNTPTimezonePro(mqttDeviceConfig.timeZone));
            orderTasks.add(OrderTaskAssembler.exitConfigMode());
            MokoSupport.getInstance().sendOrder(orderTasks.toArray(new OrderTask[]{}));
        } catch (Exception e) {
            ToastUtils.showToast(this, "File is missing");
        }
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

    public void selectTimeZone(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mTimeZones, mSelectedTimeZone);
        dialog.setListener(value -> {
            mSelectedTimeZone = value;
            mBind.tvTimeZone.setText(mTimeZones.get(mSelectedTimeZone));
        });
        dialog.show(getSupportFragmentManager());
    }

    private int progress;

    private void showConnMqttDialog() {
        isDeviceConnectSuccess = false;
        View view = LayoutInflater.from(this).inflate(R.layout.mqtt_conn_content, mBind.getRoot(), false);
        donutProgress = view.findViewById(R.id.dp_progress);
        mqttConnDialog = new CustomDialog.Builder(this)
                .setContentView(view)
                .create();
        mqttConnDialog.setCancelable(false);
        mqttConnDialog.show();
        new Thread(() -> {
            progress = 0;
            while (progress <= 100 && !isDeviceConnectSuccess) {
                runOnUiThread(() -> {
                    donutProgress.setProgress(progress);
                    donutProgress.setText(progress + "%");
                });
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                progress++;
            }
        }).start();
        mHandler.postDelayed(() -> {
            if (!isDeviceConnectSuccess) {
                isDeviceConnectSuccess = true;
                isSettingSuccess = false;
                dismissConnMqttDialog();
                ToastUtils.showToast(SetDeviceMQTTActivity.this, getString(R.string.mqtt_connecting_timeout));
                finish();
            }
        }, 90 * 1000);
    }

    private void dismissConnMqttDialog() {
        if (mqttConnDialog != null && !isFinishing() && mqttConnDialog.isShowing()) {
            isDeviceConnectSuccess = true;
            isSettingSuccess = false;
            mqttConnDialog.dismiss();
            mHandler.removeMessages(0);
        }
    }

    private void subscribeTopic() {
        // 订阅
        try {
            if (TextUtils.isEmpty(mqttAppConfig.topicSubscribe)) {
                MQTTSupport.getInstance().subscribe(mqttDeviceConfig.topicPublish, mqttAppConfig.qos);
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}
