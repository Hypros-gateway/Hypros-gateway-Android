package com.moko.hyprosgw.activity.filter;

import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.moko.hyprosgw.AppConstants;
import com.moko.hyprosgw.R;
import com.moko.hyprosgw.base.BaseActivity;
import com.moko.hyprosgw.databinding.ActivityFilterAdvNameBinding;
import com.moko.hyprosgw.dialog.AlertMessageDialog;
import com.moko.hyprosgw.entity.MQTTConfig;
import com.moko.hyprosgw.entity.MokoDevice;
import com.moko.hyprosgw.utils.SPUtiles;
import com.moko.hyprosgw.utils.ToastUtils;
import com.moko.support.scannergw.MQTTConstants;
import com.moko.support.scannergw.MQTTSupport;
import com.moko.support.scannergw.entity.FilterType;
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
import java.util.List;

public class FilterAdvNameActivity extends BaseActivity<ActivityFilterAdvNameBinding> {
    private final String FILTER_ASCII = "[ -~]*";
    private MokoDevice mMokoDevice;
    private MQTTConfig appMqttConfig;
    public Handler mHandler;
    private List<String> filterAdvName;
    private InputFilter filter;

    @Override
    protected void onCreate() {
        String mqttConfigAppStr = SPUtiles.getStringValue(this, AppConstants.SP_KEY_MQTT_CONFIG_APP, "");
        appMqttConfig = new Gson().fromJson(mqttConfigAppStr, MQTTConfig.class);
        mMokoDevice = (MokoDevice) getIntent().getSerializableExtra(AppConstants.EXTRA_KEY_DEVICE);
        filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }
            return null;
        };
        mHandler = new Handler(Looper.getMainLooper());
        showLoadingProgressDialog();
        mHandler.postDelayed(() -> {
            dismissLoadingProgressDialog();
            finish();
        }, 30 * 1000);
        getFilterAdvName();
    }

    @Override
    protected ActivityFilterAdvNameBinding getViewBinding() {
        return ActivityFilterAdvNameBinding.inflate(getLayoutInflater());
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
        if (msg_id == MQTTConstants.READ_MSG_ID_FILTER_ADV_NAME) {
            Type type = new TypeToken<MsgReadResult<FilterType>>() {
            }.getType();
            MsgReadResult<FilterType> result = new Gson().fromJson(message, type);
            if (!mMokoDevice.deviceId.equals(result.device_info.device_id)) return;
            dismissLoadingProgressDialog();
            mHandler.removeMessages(0);
            mBind.cbPreciseMatch.setChecked(result.data.precise == 1);
            mBind.cbReverseFilter.setChecked(result.data.reverse == 1);
            int number = result.data.array_num;
            if (number == 0) {
                filterAdvName = new ArrayList<>();
            } else {
                filterAdvName = result.data.rule;
                if (filterAdvName.size() > 0) {
                    for (int i = 0, l = filterAdvName.size(); i < l; i++) {
                        String advName = filterAdvName.get(i);
                        View v = LayoutInflater.from(FilterAdvNameActivity.this).inflate(R.layout.item_adv_name_filter, mBind.llDavName, false);
                        TextView title = v.findViewById(R.id.tv_adv_name_title);
                        EditText etAdvName = v.findViewById(R.id.et_adv_name);
                        etAdvName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20), filter});
                        title.setText(String.format("ADV Name%d", i + 1));
                        etAdvName.setText(advName);
                        mBind.llDavName.addView(v);
                    }
                }
            }

        }
        if (msg_id == MQTTConstants.CONFIG_MSG_ID_FILTER_ADV_NAME) {
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

    private void getFilterAdvName() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;
        String message = MQTTMessageAssembler.assembleReadFilterAdvName(deviceInfo);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.READ_MSG_ID_FILTER_ADV_NAME, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void onBack(View view) {
        finish();
    }

    public void onSave(View view) {
        if (isWindowLocked()) return;
        if (isValid()) {
            mHandler.postDelayed(() -> {
                dismissLoadingProgressDialog();
                ToastUtils.showToast(this, "Set up failed");
            }, 30 * 1000);
            showLoadingProgressDialog();
            saveParams();
        }
    }

    public void onAdd(View view) {
        if (isWindowLocked()) return;
        int count = mBind.llDavName.getChildCount();
        if (count > 9) {
            ToastUtils.showToast(this, "You can set up to 10 filters!");
            return;
        }
        View v = LayoutInflater.from(this).inflate(R.layout.item_adv_name_filter, mBind.llDavName, false);
        TextView title = v.findViewById(R.id.tv_adv_name_title);
        title.setText(String.format("ADV Name%d", count + 1));
        EditText etAdvName = v.findViewById(R.id.et_adv_name);
        etAdvName.setFilters(new InputFilter[]{new InputFilter.LengthFilter(20), filter});
        mBind.llDavName.addView(v);
    }

    public void onDel(View view) {
        if (isWindowLocked()) return;
        final int c = mBind.llDavName.getChildCount();
        if (c == 0) {
            ToastUtils.showToast(this, "There are currently no filters to delete");
            return;
        }
        AlertMessageDialog dialog = new AlertMessageDialog();
        dialog.setTitle("Warning");
        dialog.setMessage("Please confirm whether to delete it, if yes, the last option will be deleted!");
        dialog.setOnAlertConfirmListener(() -> {
            int count = mBind.llDavName.getChildCount();
            if (count > 0) {
                mBind.llDavName.removeViewAt(count - 1);
            }
        });
        dialog.show(getSupportFragmentManager());
    }

    private void saveParams() {
        MsgDeviceInfo deviceInfo = new MsgDeviceInfo();
        deviceInfo.device_id = mMokoDevice.deviceId;
        deviceInfo.mac = mMokoDevice.mac;

        FilterType filterType = new FilterType();
        filterType.precise = mBind.cbPreciseMatch.isChecked() ? 1 : 0;
        filterType.reverse = mBind.cbReverseFilter.isChecked() ? 1 : 0;
        filterType.array_num = filterAdvName.size();
        filterType.rule = filterAdvName;

        String message = MQTTMessageAssembler.assembleWriteFilterAdvName(deviceInfo, filterType);
        try {
            MQTTSupport.getInstance().publish(getTopic(), message, MQTTConstants.CONFIG_MSG_ID_FILTER_ADV_NAME, appMqttConfig.qos);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private String getTopic() {
        return TextUtils.isEmpty(appMqttConfig.topicPublish) ? mMokoDevice.topicSubscribe : appMqttConfig.topicPublish;
    }

    private boolean isValid() {
        final int c = mBind.llDavName.getChildCount();
        if (c > 0) {
            // 发送设置的过滤RawData
            int count = mBind.llDavName.getChildCount();
            if (count == 0) {
                ToastUtils.showToast(this, "Para Error");
                return false;
            }
            filterAdvName.clear();
            for (int i = 0; i < count; i++) {
                View v = mBind.llDavName.getChildAt(i);
                EditText etAdvName = v.findViewById(R.id.et_adv_name);
                final String advName = etAdvName.getText().toString();
                if (TextUtils.isEmpty(advName)) {
                    ToastUtils.showToast(this, "Para Error");
                    return false;
                }
                filterAdvName.add(advName);
            }
        } else {
            filterAdvName = new ArrayList<>();
        }
        return true;
    }
}
