package com.moko.hyprosgw.activity.filter;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.hyprosgw.AppConstants;
import com.moko.hyprosgw.base.BaseActivity;
import com.moko.hyprosgw.databinding.ActivityBeaconTypeFilterBinding;
import com.moko.hyprosgw.entity.MQTTConfig;
import com.moko.hyprosgw.entity.MokoDevice;
import com.moko.hyprosgw.utils.SPUtiles;
import com.moko.hyprosgw.utils.ToastUtils;
import com.moko.support.scannergw.MQTTConstants;
import com.moko.support.scannergw.MQTTSupport;
import com.moko.support.scannergw.entity.MsgConfigResult;
import com.moko.support.scannergw.entity.MsgDeviceInfo;
import com.moko.support.scannergw.entity.MsgReadResult;
import com.moko.support.scannergw.entity.TypeFilter;
import com.moko.support.scannergw.event.DeviceOnlineEvent;
import com.moko.support.scannergw.event.MQTTMessageArrivedEvent;
import com.moko.support.scannergw.handler.MQTTMessageAssembler;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.lang.reflect.Type;

public class BeaconTypeFilterActivity extends BaseActivity<ActivityBeaconTypeFilterBinding> {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    public Handler mHandler;

    @Override
    protected void onCreate() {
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        mHandler = new Handler(Looper.getMainLooper());
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getBeaconTypeFilter();
    }

    @Override
    protected ActivityBeaconTypeFilterBinding getViewBinding() {
        return ActivityBeaconTypeFilterBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.READ_MSG_ID_BEACON_TYPE_FILTER) {
            Type type = new TypeToken<MsgReadResult<TypeFilter>>() {
            }.getType();
            MsgReadResult<TypeFilter> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.cbTypeIbeacon.setChecked(result.data.ibeacon == 1);
            mBind.cbTypeEddystoneUid.setChecked(result.data.eddystone_uid == 1);
            mBind.cbTypeEddystoneUrl.setChecked(result.data.eddystone_url == 1);
            mBind.cbTypeEddystoneTlm.setChecked(result.data.eddystone_tlm == 1);
            mBind.cbTypeMkibeacon.setChecked(result.data.MK_iBeacon == 1);
            mBind.cbTypeMkibeaconAcc.setChecked(result.data.MK_ACC == 1);
            mBind.cbTypeBxpAcc.setChecked(result.data.BXP_ACC == 1);
            mBind.cbTypeBxpTh.setChecked(result.data.BXP_TH == 1);
            mBind.cbTypeUnknown.setChecked(result.data.unknown == 1);
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_BEACON_TYPE_FILTER) {
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

    private void setBeaconTypeFilter() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        TypeFilter typeFilter = new TypeFilter();
        typeFilter.ibeacon = mBind.cbTypeIbeacon.isChecked() ? 1 : 0;
        typeFilter.eddystone_uid = mBind.cbTypeEddystoneUid.isChecked() ? 1 : 0;
        typeFilter.eddystone_url = mBind.cbTypeEddystoneUrl.isChecked() ? 1 : 0;
        typeFilter.eddystone_tlm = mBind.cbTypeEddystoneTlm.isChecked() ? 1 : 0;
        typeFilter.MK_iBeacon = mBind.cbTypeMkibeacon.isChecked() ? 1 : 0;
        typeFilter.MK_ACC = mBind.cbTypeMkibeaconAcc.isChecked() ? 1 : 0;
        typeFilter.BXP_ACC = mBind.cbTypeBxpAcc.isChecked() ? 1 : 0;
        typeFilter.BXP_TH = mBind.cbTypeBxpTh.isChecked() ? 1 : 0;
        typeFilter.unknown = mBind.cbTypeUnknown.isChecked() ? 1 : 0;
        String message = MQTTMessageAssembler.assembleWriteBeaconTypeFilter(deviceInfo, typeFilter);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_BEACON_TYPE_FILTER, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getBeaconTypeFilter() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadBeaconTypeFilter(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.READ_MSG_ID_BEACON_TYPE_FILTER, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private String getTopic() {
        return TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        showLoadingProgressDialog();
        setBeaconTypeFilter();
    }
}
