package com.moko.hyprosgw.activity;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.SeekBar;

import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.hyprosgw.AppConstants;
import com.moko.hyprosgw.R;
import com.moko.hyprosgw.activity.filter.DuplicateDataFilterActivity;
import com.moko.hyprosgw.activity.filter.FilterAdvNameActivity;
import com.moko.hyprosgw.activity.filter.FilterMacAddressActivity;
import com.moko.hyprosgw.activity.filter.FilterRawDataSwitchActivity;
import com.moko.hyprosgw.base.BaseActivity;
import com.moko.hyprosgw.databinding.ActivityScannerUploadOptionBinding;
import com.moko.hyprosgw.dialog.BottomDialog;
import com.moko.hyprosgw.entity.MQTTConfig;
import com.moko.hyprosgw.entity.MokoDevice;
import com.moko.hyprosgw.utils.SPUtiles;
import com.moko.hyprosgw.utils.ToastUtils;
import com.moko.support.scannergw.MQTTConstants;
import com.moko.support.scannergw.MQTTSupport;
import com.moko.support.scannergw.entity.FilterPHY;
import com.moko.support.scannergw.entity.FilterRSSI;
import com.moko.support.scannergw.entity.FilterRelationship;
import com.moko.support.scannergw.entity.MsgConfigResult;
import com.moko.support.scannergw.entity.MsgDeviceInfo;
import com.moko.support.scannergw.entity.MsgReadResult;
import com.moko.support.scannergw.event.DeviceOnlineEvent;
import com.moko.support.scannergw.event.MQTTMessageArrivedEvent;
import com.moko.support.scannergw.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;
import java.util.ArrayList;

public class ScannerUploadOptionActivity extends BaseActivity<ActivityScannerUploadOptionBinding> implements SeekBar.OnSeekBarChangeListener {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    public Handler mHandler;
    private ArrayList<String> mPHYValues;
    private int mPHYSelected;
    private ArrayList<String> mRelationshipValues;
    private int mRelationshipSelected;

    @Override
    protected void onCreate() {
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mBind.tvName.setText(mMokoDevice.nickName);
        mBind.sbRssiFilter.setOnSeekBarChangeListener(this);
        mHandler = new Handler(Looper.getMainLooper());
        mPHYValues = new ArrayList<>();
        mPHYValues.add("1M PHY(V4.2)");
        mPHYValues.add("1M PHY(V5.0)");
        mPHYValues.add("1M PHY(V4.2) & 1M PHY(V5.0)");
        mPHYValues.add("Coded PHY(V5.0)");
        mRelationshipValues = new ArrayList<>();
        mRelationshipValues.add("Null");
        mRelationshipValues.add("Only MAC");
        mRelationshipValues.add("Only ADV Name");
        mRelationshipValues.add("Only RAW DATA");
        mRelationshipValues.add("ADV name&Raw data");
        mRelationshipValues.add("MAC&ADV name&Raw data");
        mRelationshipValues.add("ADV name | Raw data");
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getFilterRSSI();
    }

    @Override
    protected ActivityScannerUploadOptionBinding getViewBinding() {
        return ActivityScannerUploadOptionBinding.inflate(getLayoutInflater());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
        final String topic = event.getTopic();
        final String message = event.getMessage();
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
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_RSSI) {
            Type type = new TypeToken<MsgReadResult<FilterRSSI>>() {
            }.getType();
            MsgReadResult<FilterRSSI> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            final int rssi = result.data.rssi;
            int progress = rssi + 127;
            mBind.sbRssiFilter.setProgress(progress);
            mBind.tvRssiFilterValue.setText(String.format("%ddBm", rssi));
            mBind.tvRssiFilterTips.setText(getString(R.string.rssi_filter, rssi));
            getFilterPHY();
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_PHY) {
            Type type = new TypeToken<MsgReadResult<FilterPHY>>() {
            }.getType();
            MsgReadResult<FilterPHY> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            final int phy = result.data.phy;
            mPHYSelected = phy;
            mBind.tvFilterByPhy.setText(mPHYValues.get(phy));
            getFilterRelationship();
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_RELATIONSHIP) {
            Type type = new TypeToken<MsgReadResult<FilterRelationship>>() {
            }.getType();
            MsgReadResult<FilterRelationship> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            final int rule = result.data.rule;
            mRelationshipSelected = rule;
            mBind.tvFilterRelationship.setText(mRelationshipValues.get(rule));
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_RSSI) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            if (result.result_code != 0) return;
            setFilterPHY();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_PHY) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            if (result.result_code != 0) return;
            setFilterRelationship();
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_RELATIONSHIP) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            if (result.result_code == 0) {
                ToastUtils.showToast(this, "Set up succeed");
            } else {
                ToastUtils.showToast(this, "Set up failed");
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onDeviceOnlineEvent(DeviceOnlineEvent event) {
        String deviceId = event.getDeviceId();
        if (!mMokoDevice.deviceId.equals(deviceId)) return;
        boolean online = event.isOnline();
        if (!online) finish();
    }

    public void back(View view) {
        finish();
    }

    private void getFilterRSSI() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadFilterRSSI(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.READ_MSG_ID_FILTER_RSSI, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setFilterRSSI() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        FilterRSSI filterRSSI = new FilterRSSI();
        filterRSSI.rssi = mBind.sbRssiFilter.getProgress() - 127;
        String message = MQTTMessageAssembler.assembleWriteFilterRSSI(deviceInfo, filterRSSI);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_FILTER_RSSI, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getFilterPHY() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadFilterPHY(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.READ_MSG_ID_FILTER_PHY, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setFilterPHY() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        FilterPHY filterPHY = new FilterPHY();
        filterPHY.phy = mPHYSelected;
        String message = MQTTMessageAssembler.assembleWriteFilterPHY(deviceInfo, filterPHY);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_FILTER_PHY, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getFilterRelationship() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadFilterRelationship(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.READ_MSG_ID_FILTER_RELATIONSHIP, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setFilterRelationship() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        FilterRelationship relationship = new FilterRelationship();
        relationship.rule = mRelationshipSelected;
        String message = MQTTMessageAssembler.assembleWriteFilterRelationship(deviceInfo, relationship);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_FILTER_RELATIONSHIP, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private String getTopic() {
        return TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
    }

    public void onFilterByPHY(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mPHYValues, mPHYSelected);
        dialog.setListener(value -> {
            mPHYSelected = value;
            mBind.tvFilterByPhy.setText(mPHYValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onFilterRelationship(View view) {
        if (isWindowLocked()) return;
        BottomDialog dialog = new BottomDialog();
        dialog.setDatas(mRelationshipValues, mRelationshipSelected);
        dialog.setListener(value -> {
            mRelationshipSelected = value;
            mBind.tvFilterRelationship.setText(mRelationshipValues.get(value));
        });
        dialog.show(getSupportFragmentManager());
    }

    public void onDuplicateDataFilter(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        Intent i = new Intent(this, DuplicateDataFilterActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onUploadDataOption(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        Intent i = new Intent(this, UploadDataOptionActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onFilterByMac(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        Intent i = new Intent(this, FilterMacAddressActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onFilterByName(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        Intent i = new Intent(this, FilterAdvNameActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onFilterByRawData(View view) {
        if (isWindowLocked()) return;
        if (!MQTTSupport.getInstance().isConnected()) {
            ToastUtils.showToast(this, R.string.network_error);
            return;
        }
        if (!mMokoDevice.isOnline) {
            ToastUtils.showToast(this, R.string.device_offline);
            return;
        }
        Intent i = new Intent(this, FilterRawDataSwitchActivity.class);
        i.putExtra(AppConstants.EXTRA_KEY_DEVICE, mMokoDevice);
        startActivity(i);
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setFilterRSSI();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == AppConstants.REQUEST_CODE_FILTER_CONDITION) {
            showLoadingProgressDialog();
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                finish();
            }, 30 * 1000);
            getFilterRSSI();
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        int rssi = progress - 127;
        mBind.tvRssiFilterValue.setText(String.format("%ddBm", rssi));
        mBind.tvRssiFilterTips.setText(getString(R.string.rssi_filter, rssi));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
