package com.moko.hyprosgw.activity;

import android.annotation.SuppressLint;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.SeekBar;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.hyprosgw.AppConstants;
import com.moko.hyprosgw.R;
import com.moko.hyprosgw.base.BaseActivity;
import com.moko.hyprosgw.databinding.ActivityAdvertiseIbeaconBinding;
import com.moko.hyprosgw.entity.MQTTConfig;
import com.moko.hyprosgw.entity.MokoDevice;
import com.moko.hyprosgw.utils.SPUtiles;
import com.moko.hyprosgw.utils.ToastUtils;
import com.moko.support.scannergw.MQTTConstants;
import com.moko.support.scannergw.MQTTSupport;
import com.moko.support.scannergw.entity.IBeaconAdvParams;
import com.moko.support.scannergw.entity.IBeaconEnable;
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

/**
 * @author: jun.liu
 * @date: 2023/7/26 19:44
 * @des:
 */
public class AdvertiseIBeaconActivity extends BaseActivity<ActivityAdvertiseIbeaconBinding> implements SeekBar.OnSeekBarChangeListener {
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;

    public Handler mHandler;
    private final int[] txPowerArray = {-40, -20, -8, -4, 0, 4, 8};

    @Override
    protected ActivityAdvertiseIbeaconBinding getViewBinding() {
        return ActivityAdvertiseIbeaconBinding.inflate(getLayoutInflater());
    }

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
        getBeaconEnable();
        mBind.cbAdvertiseBeacon.setOnCheckedChangeListener((buttonView, isChecked) -> mBind.group.setVisibility(isChecked ? View.VISIBLE : View.GONE));
        mBind.sbRssi.setOnSeekBarChangeListener(this);
        mBind.sbTxPower.setOnSeekBarChangeListener(this);
    }

    private void getBeaconEnable() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadBeaconEnable(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.READ_MSG_ID_BEACON_ENABLE, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setBeaconEnable() {
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            ToastUtils.showToast(this, "Set up failed");
        }, 30 * 1000);
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        IBeaconEnable beaconEnable = new IBeaconEnable();
        beaconEnable.ibeacon_enable = mBind.cbAdvertiseBeacon.isChecked() ? 1 : 0;
        String message = MQTTMessageAssembler.assembleWriteBeaconEnable(deviceInfo, beaconEnable);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_BEACON_ENABLE, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void setBeaconAdvParams() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        IBeaconAdvParams advParams = new IBeaconAdvParams();
        advParams.major = Integer.parseInt(mBind.etMajor.getText().toString());
        advParams.minor = Integer.parseInt(mBind.etMinor.getText().toString());
        advParams.uuid = mBind.etUuid.getText().toString();
        advParams.adv_interval = Integer.parseInt(mBind.etAdvInterval.getText().toString());
        advParams.rssi_1m = mBind.sbRssi.getProgress() - 100;
        advParams.tx_power = txPowerArray[mBind.sbTxPower.getProgress()];
        String message = MQTTMessageAssembler.assembleWriteBeaconAdvParams(deviceInfo, advParams);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_BEACON_ADV_PARAMS, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void getBeaconAdvParams() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadBeaconAdvParams(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.READ_MSG_ID_BEACON_ADV_PARAMS, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("SetTextI18n")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMQTTMessageArrivedEvent(MQTTMessageArrivedEvent event) {
        // 更新所有设备的网络状态
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
        if (msg_id == MQTTConstants.READ_MSG_ID_BEACON_ENABLE) {
            Type type = new TypeToken<MsgReadResult<IBeaconEnable>>() {
            }.getType();
            MsgReadResult<IBeaconEnable> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            int enable = result.data.ibeacon_enable;
            mBind.cbAdvertiseBeacon.setChecked(enable == 1);
            mBind.group.setVisibility(enable == 1 ? View.VISIBLE : View.GONE);
            getBeaconAdvParams();
        }
        if (msg_id == MQTTConstants.READ_MSG_ID_BEACON_ADV_PARAMS) {
            Type type = new TypeToken<MsgReadResult<IBeaconAdvParams>>() {
            }.getType();
            MsgReadResult<IBeaconAdvParams> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.etMajor.setText(String.valueOf(result.data.major));
            mBind.etMajor.setSelection(mBind.etMajor.getText().length());
            mBind.etMinor.setText(String.valueOf(result.data.minor));
            mBind.etMinor.setSelection(mBind.etMinor.getText().length());
            mBind.etUuid.setText(result.data.uuid);
            mBind.etUuid.setSelection(mBind.etUuid.getText().length());
            mBind.etAdvInterval.setText(String.valueOf(result.data.adv_interval));
            mBind.etAdvInterval.setSelection(mBind.etAdvInterval.getText().length());
            int rssi = result.data.rssi_1m;
            int progress = rssi + 100;
            mBind.sbRssi.setProgress(progress);
            mBind.tvSbRssiVal.setText(rssi + "dBm");
            int txPower = result.data.tx_power;
            mBind.sbTxPower.setProgress(getProgress(txPower));
            mBind.tvSbTxPowerVal.setText(txPower + "dBm");
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_BEACON_ENABLE) {
            Type type = new TypeToken<MsgConfigResult>() {
            }.getType();
            MsgConfigResult result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            if (result.result_code == 0) {
                if (mBind.cbAdvertiseBeacon.isChecked()) {
                    setBeaconAdvParams();
                } else {
                    ToastUtils.showToast(this, "Set up succeed");
                    dismissLoadingProgressDialog();
                    mHandler.removeMessages(0);
                }
            } else {
                dismissLoadingProgressDialog();
                mHandler.removeMessages(0);
                ToastUtils.showToast(this, "Set up failed");
            }
        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_BEACON_ADV_PARAMS) {
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

    private int getProgress(int progress) {
        int index = 0;
        for (int i = 0; i < txPowerArray.length; i++) {
            if (progress == txPowerArray[i]) {
                index = i;
                break;
            }
        }
        return index;
    }

    private String getTopic() {
        String appTopic;
        if (TextUtils.isEmpty(appMqttConfig.topicPublish)) {
            appTopic = mMokoDevice.topicSubscribe;
        } else {
            appTopic = appMqttConfig.topicPublish;
        }
        return appTopic;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        int id = seekBar.getId();
        if (id == R.id.sbRssi) {
            int rssi = progress - 100;
            mBind.tvSbRssiVal.setText(rssi + "dBm");
        } else if (id == R.id.sbTxPower) {
            int txPower = txPowerArray[progress];
            mBind.tvSbTxPowerVal.setText(txPower + "dBm");
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    public void onSave(View view) {
        if (mBind.cbAdvertiseBeacon.isChecked()) {
            //校验参数
            if (!isValid()) {
                ToastUtils.showToast(this, "param error");
                return;
            }
        }
        setBeaconEnable();
    }

    private boolean isValid() {
        if (TextUtils.isEmpty(mBind.etMajor.getText())) return false;
        if (Integer.parseInt(mBind.etMajor.getText().toString()) > 65535) return false;
        if (TextUtils.isEmpty(mBind.etMinor.getText())) return false;
        if (Integer.parseInt(mBind.etMinor.getText().toString()) > 65535) return false;
        if (TextUtils.isEmpty(mBind.etUuid.getText()) || mBind.etUuid.getText().toString().length() != 32)
            return false;
        if (TextUtils.isEmpty(mBind.etAdvInterval.getText())) return false;
        int interval = Integer.parseInt(mBind.etAdvInterval.getText().toString());
        return interval >= 1 && interval <= 100;
    }
}
