package com.moko.mkscannergw.fragment;

import android.os.Bundle;
import android.text.InputFilter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.moko.mkscannergw.base.BaseActivity;
import com.moko.mkscannergw.databinding.FragmentUserDeviceBinding;

import androidx.fragment.app.Fragment;

public class UserDeviceFragment extends Fragment {
    private final String FILTER_ASCII = "[ -~]*";
    private static final String TAG = UserDeviceFragment.class.getSimpleName();
    private FragmentUserDeviceBinding mBind;


    private BaseActivity activity;
    private String username;
    private String password;

    public UserDeviceFragment() {
    }

    public static UserDeviceFragment newInstance() {
        UserDeviceFragment fragment = new UserDeviceFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate: ");
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView: ");
        mBind = FragmentUserDeviceBinding.inflate(inflater, container, false);
        activity = (BaseActivity) getActivity();
        InputFilter filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }

            return null;
        };
        mBind.etMqttUsername.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), filter});
        mBind.etMqttPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(256), filter});
        mBind.etMqttUsername.setText(username);
        mBind.etMqttPassword.setText(password);
        return mBind.getRoot();
    }

    @Override
    public void onResume() {
        Log.i(TAG, "onResume: ");
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.i(TAG, "onPause: ");
        super.onPause();
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy: ");
        super.onDestroy();
    }

    public void setUserName(String username) {
        this.username = username;
    }

    public void setUserName() {
        mBind.etMqttUsername.setText(username);
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setPassword() {
        mBind.etMqttPassword.setText(password);
    }

    public String getUsername() {
        String username = mBind.etMqttUsername.getText().toString();
        return username;
    }

    public String getPassword() {
        String password = mBind.etMqttPassword.getText().toString();
        return password;
    }
}
