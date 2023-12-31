package com.moko.support.scannergw.task;

import com.moko.ble.lib.task.OrderTask;
import com.moko.ble.lib.utils.MokoUtils;
import com.moko.support.scannergw.MokoSupport;
import com.moko.support.scannergw.entity.OrderCHAR;
import com.moko.support.scannergw.entity.ParamsKeyEnum;
import com.moko.support.scannergw.entity.ParamsLongKeyEnum;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

import androidx.annotation.IntRange;

public class ParamsTask extends OrderTask {
    public byte[] data;

    public ParamsTask() {
        super(OrderCHAR.CHAR_PARAMS, OrderTask.RESPONSE_TYPE_WRITE);
    }

    @Override
    public byte[] assemble() {
        return data;
    }

    public void setData(ParamsKeyEnum key) {
        createGetConfigData(key.getParamsKey());
    }

    public void setData(ParamsLongKeyEnum key) {
        switch (key) {
            case KEY_MQTT_USERNAME:
            case KEY_MQTT_PASSWORD:
                createGetLongConfigData(key.getParamsKey());
                break;
        }
    }

    private void createGetLongConfigData(int paramsKey) {
        data = new byte[]{
                (byte) 0xEE,
                (byte) 0x00,
                (byte) paramsKey,
                (byte) 0x00
        };
    }

    private void createGetConfigData(int configKey) {
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x00,
                (byte) configKey,
                (byte) 0x00
        };
    }

    public void exitConfigMode() {
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_EXIT_CONFIG_MODE.getParamsKey(),
                (byte) 0x01,
                (byte) 0x01
        };
    }

    public void setWifiSSID(String SSID) {
        byte[] dataBytes = SSID.getBytes();
        int length = dataBytes.length;
        data = new byte[length + 4];
        data[0] = (byte) 0xED;
        data[1] = (byte) 0x01;
        data[2] = (byte) ParamsKeyEnum.KEY_WIFI_SSID.getParamsKey();
        data[3] = (byte) length;
        for (int i = 0; i < dataBytes.length; i++) {
            data[i + 4] = dataBytes[i];
        }
    }

    public void setWifiPassword(String password) {
        byte[] dataBytes = password.getBytes();
        int length = dataBytes.length;
        data = new byte[length + 4];
        data[0] = (byte) 0xED;
        data[1] = (byte) 0x01;
        data[2] = (byte) ParamsKeyEnum.KEY_WIFI_PASSWORD.getParamsKey();
        data[3] = (byte) length;
        for (int i = 0; i < dataBytes.length; i++) {
            data[i + 4] = dataBytes[i];
        }
    }

    public void setMqttConnectMode(@IntRange(from = 0, to = 3) int mode) {
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_MQTT_CONNECT_MODE.getParamsKey(),
                (byte) 0x01,
                (byte) mode
        };
    }

    public void setMqttHost(String host) {
        byte[] dataBytes = host.getBytes();
        int length = dataBytes.length;
        data = new byte[length + 4];
        data[0] = (byte) 0xED;
        data[1] = (byte) 0x01;
        data[2] = (byte) ParamsKeyEnum.KEY_MQTT_HOST.getParamsKey();
        data[3] = (byte) length;
        for (int i = 0; i < dataBytes.length; i++) {
            data[i + 4] = dataBytes[i];
        }
    }

    public void setMqttPort(@IntRange(from = 0, to = 65535) int port) {
        byte[] dataBytes = MokoUtils.toByteArray(port, 2);
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_MQTT_PORT.getParamsKey(),
                (byte) 0x02,
                dataBytes[0],
                dataBytes[1]
        };
    }

    public void setMqttCleanSession(@IntRange(from = 0, to = 1) int enable) {
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_MQTT_CLEAN_SESSION.getParamsKey(),
                (byte) 0x01,
                (byte) enable
        };
    }

    public void setMqttKeepAlive(@IntRange(from = 10, to = 120) int keepAlive) {
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_MQTT_KEEP_ALIVE.getParamsKey(),
                (byte) 0x01,
                (byte) keepAlive
        };
    }

    public void setMqttQos(@IntRange(from = 0, to = 2) int qos) {
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_MQTT_QOS.getParamsKey(),
                (byte) 0x01,
                (byte) qos
        };
    }

    public void setMqttClientId(String clientId) {
        byte[] dataBytes = clientId.getBytes();
        int length = dataBytes.length;
        data = new byte[length + 4];
        data[0] = (byte) 0xED;
        data[1] = (byte) 0x01;
        data[2] = (byte) ParamsKeyEnum.KEY_MQTT_CLIENT_ID.getParamsKey();
        data[3] = (byte) length;
        for (int i = 0; i < dataBytes.length; i++) {
            data[i + 4] = dataBytes[i];
        }
    }

    public void setMqttDeviceId(String deviceId) {
        byte[] dataBytes = deviceId.getBytes();
        int length = dataBytes.length;
        data = new byte[length + 4];
        data[0] = (byte) 0xED;
        data[1] = (byte) 0x01;
        data[2] = (byte) ParamsKeyEnum.KEY_MQTT_DEVICE_ID.getParamsKey();
        data[3] = (byte) length;
        for (int i = 0; i < dataBytes.length; i++) {
            data[i + 4] = dataBytes[i];
        }
    }

    public void setMqttSubscribeTopic(String topic) {
        byte[] dataBytes = topic.getBytes();
        int length = dataBytes.length;
        data = new byte[length + 4];
        data[0] = (byte) 0xED;
        data[1] = (byte) 0x01;
        data[2] = (byte) ParamsKeyEnum.KEY_MQTT_SUBSCRIBE_TOPIC.getParamsKey();
        data[3] = (byte) length;
        for (int i = 0; i < dataBytes.length; i++) {
            data[i + 4] = dataBytes[i];
        }
    }

    public void setMqttPublishTopic(String topic) {
        byte[] dataBytes = topic.getBytes();
        int length = dataBytes.length;
        data = new byte[length + 4];
        data[0] = (byte) 0xED;
        data[1] = (byte) 0x01;
        data[2] = (byte) ParamsKeyEnum.KEY_MQTT_PUBLISH_TOPIC.getParamsKey();
        data[3] = (byte) length;
        for (int i = 0; i < dataBytes.length; i++) {
            data[i + 4] = dataBytes[i];
        }
    }

    public void setNTPUrl(String url) {
        byte[] dataBytes = url.getBytes();
        int length = dataBytes.length;
        data = new byte[length + 4];
        data[0] = (byte) 0xED;
        data[1] = (byte) 0x01;
        data[2] = (byte) ParamsKeyEnum.KEY_NTP_URL.getParamsKey();
        data[3] = (byte) length;
        for (int i = 0; i < dataBytes.length; i++) {
            data[i + 4] = dataBytes[i];
        }
    }

    public void setNTPTimeZone(@IntRange(from = -12, to = 12) int timeZone) {
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_NTP_TIME_ZONE.getParamsKey(),
                (byte) 0x01,
                (byte) timeZone
        };
    }

    public void setNTPTimeZonePro(@IntRange(from = -24, to = 28) int timeZone) {
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_NTP_TIME_ZONE_PRO.getParamsKey(),
                (byte) 0x01,
                (byte) timeZone
        };
    }

    public void setChannelDomain(@IntRange(from = 0, to = 21) int channelDomain) {
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_CHANNEL_DOMAIN.getParamsKey(),
                (byte) 0x01,
                (byte) channelDomain
        };
    }

    public void setConnectionTimeout(@IntRange(from = 0, to = 1440) int timeout) {
        byte[] dataBytes = MokoUtils.toByteArray(timeout, 2);
        data = new byte[]{
                (byte) 0xED,
                (byte) 0x01,
                (byte) ParamsKeyEnum.KEY_CONNECTION_TIMEOUT.getParamsKey(),
                (byte) 0x02,
                dataBytes[0],
                dataBytes[1]
        };
    }

    public void setFile(ParamsLongKeyEnum key, File file) throws Exception {
        FileInputStream inputSteam = new FileInputStream(file);
        dataBytes = new byte[(int) file.length()];
        inputSteam.read(dataBytes);
        dataLength = dataBytes.length;
        if (dataLength % DATA_LENGTH_MAX > 0) {
            packetCount = dataLength / DATA_LENGTH_MAX + 1;
        } else {
            packetCount = dataLength / DATA_LENGTH_MAX;
        }
        remainPack = packetCount - 1;
        delayTime = DEFAULT_DELAY_TIME + 500 * packetCount;
        if (packetCount > 1) {
            data = new byte[DATA_LENGTH_MAX + 6];
            data[0] = (byte) 0xEE;
            data[1] = (byte) 0x01;
            data[2] = (byte) key.getParamsKey();
            data[3] = (byte) 0x01;
            data[4] = (byte) remainPack;
            data[5] = (byte) DATA_LENGTH_MAX;
            for (int i = 0; i < DATA_LENGTH_MAX; i++, dataOrigin++) {
                data[i + 6] = dataBytes[dataOrigin];
            }
        } else {
            data = new byte[dataLength + 6];
            data[0] = (byte) 0xEE;
            data[1] = (byte) 0x01;
            data[2] = (byte) key.getParamsKey();
            data[3] = (byte) 0x01;
            data[4] = (byte) remainPack;
            data[5] = (byte) dataLength;
            for (int i = 0; i < dataLength; i++) {
                data[i + 6] = dataBytes[i];
            }
        }
    }

    public void setLongChar(ParamsLongKeyEnum key, String character) {
        dataBytes = character.getBytes();
        dataLength = dataBytes.length;
        if (dataLength % DATA_LENGTH_MAX > 0) {
            packetCount = dataLength / DATA_LENGTH_MAX + 1;
        } else {
            packetCount = dataLength / DATA_LENGTH_MAX;
        }
        remainPack = packetCount - 1;
        delayTime = DEFAULT_DELAY_TIME + 500 * packetCount;
        if (packetCount > 1) {
            data = new byte[DATA_LENGTH_MAX + 6];
            data[0] = (byte) 0xEE;
            data[1] = (byte) 0x01;
            data[2] = (byte) key.getParamsKey();
            data[3] = (byte) 0x01;
            data[4] = (byte) remainPack;
            data[5] = (byte) DATA_LENGTH_MAX;
            for (int i = 0; i < DATA_LENGTH_MAX; i++, dataOrigin++) {
                data[i + 6] = dataBytes[dataOrigin];
            }
        } else {
            data = new byte[dataLength + 6];
            data[0] = (byte) 0xEE;
            data[1] = (byte) 0x01;
            data[2] = (byte) key.getParamsKey();
            data[3] = (byte) 0x01;
            data[4] = (byte) remainPack;
            data[5] = (byte) dataLength;
            for (int i = 0; i < dataLength; i++) {
                data[i + 6] = dataBytes[i];
            }
        }
    }

    private int packetCount;
    private int remainPack;
    private int dataLength;
    private int dataOrigin;
    private byte[] dataBytes;
    private String dataBytesStr = "";
    private static final int DATA_LENGTH_MAX = 238;

    @Override
    public boolean parseValue(byte[] value) {
        final int header = value[0] & 0xFF;
        final int flag = value[1] & 0xFF;
        if (header == 0xED)
            return true;
        if (flag == 0x01) {
            final int cmd = value[2] & 0xFF;
            final int result = value[4] & 0xFF;
            if (result == 1) {
                remainPack--;
                if (remainPack >= 0) {
                    assembleRemainData(cmd);
                    return false;
                }
                return true;
            }
        } else {
            final int cmd = value[2] & 0xFF;
            final int remainPack = value[4] & 0xFF;
            final int length = value[5] & 0xFF;
            if (remainPack > 0) {
                byte[] remainBytes = Arrays.copyOfRange(value, 6, 6 + length);
                dataBytesStr += MokoUtils.bytesToHexString(remainBytes);
            } else {
                if (length == 0) {
                    data = new byte[5];
                    data[0] = (byte) 0xEE;
                    data[1] = (byte) 0x00;
                    data[2] = (byte) cmd;
                    data[3] = 0;
                    data[4] = 0;
                    response.responseValue = data;
                    orderStatus = ORDER_STATUS_SUCCESS;
                    MokoSupport.getInstance().pollTask();
                    MokoSupport.getInstance().executeTask();
                    MokoSupport.getInstance().orderResult(response);
                    return false;
                }
                byte[] remainBytes = Arrays.copyOfRange(value, 6, 6 + length);
                dataBytesStr += MokoUtils.bytesToHexString(remainBytes);
                dataBytes = MokoUtils.hex2bytes(dataBytesStr);
                dataLength = dataBytes.length;
                byte[] dataLengthBytes = MokoUtils.toByteArray(dataLength, 2);
                data = new byte[dataLength + 5];
                data[0] = (byte) 0xEE;
                data[1] = (byte) 0x00;
                data[2] = (byte) cmd;
                data[3] = dataLengthBytes[0];
                data[4] = dataLengthBytes[1];
                for (int i = 0; i < dataLength; i++) {
                    data[i + 5] = dataBytes[i];
                }
                response.responseValue = data;
                orderStatus = ORDER_STATUS_SUCCESS;
                MokoSupport.getInstance().pollTask();
                MokoSupport.getInstance().executeTask();
                MokoSupport.getInstance().orderResult(response);
                dataBytesStr = "";
            }
        }
        return false;
    }

    private void assembleRemainData(int cmd) {
        int length = dataLength - dataOrigin;
        if (length > DATA_LENGTH_MAX) {
            data = new byte[DATA_LENGTH_MAX + 6];
            data[0] = (byte) 0xEE;
            data[1] = (byte) 0x01;
            data[2] = (byte) cmd;
            data[3] = (byte) 0x00;
            data[4] = (byte) remainPack;
            data[5] = (byte) DATA_LENGTH_MAX;
            for (int i = 0; i < DATA_LENGTH_MAX; i++, dataOrigin++) {
                data[i + 6] = dataBytes[dataOrigin];
            }
        } else {
            data = new byte[length + 6];
            data[0] = (byte) 0xEE;
            data[1] = (byte) 0x01;
            data[2] = (byte) cmd;
            data[3] = (byte) 0x00;
            data[4] = (byte) remainPack;
            data[5] = (byte) length;
            for (int i = 0; i < length; i++, dataOrigin++) {
                data[i + 6] = dataBytes[dataOrigin];
            }
        }
        MokoSupport.getInstance().sendDirectOrder(this);
    }
}
