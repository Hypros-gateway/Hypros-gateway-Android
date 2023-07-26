package com.moko.hyprosgw.dialog;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;

import com.moko.hyprosgw.R;
import com.moko.hyprosgw.databinding.DialogPasswordBinding;
import com.moko.hyprosgw.utils.ToastUtils;

public class PasswordDialog extends MokoBaseDialog<DialogPasswordBinding> {
    public static final String TAG = PasswordDialog.class.getSimpleName();
    private final String FILTER_ASCII = "[ -~]*";
    private String password;

    @Override
    protected DialogPasswordBinding getViewBind(LayoutInflater inflater, ViewGroup container) {
        return DialogPasswordBinding.inflate(inflater, container, false);
    }

    @Override
    protected void onCreateView() {
        InputFilter filter = (source, start, end, dest, dstart, dend) -> {
            if (!(source + "").matches(FILTER_ASCII)) {
                return "";
            }
            return null;
        };
        mBind.etPassword.setFilters(new InputFilter[]{new InputFilter.LengthFilter(16), filter});
        mBind.etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                int length = s.toString().length();
                mBind.tvPasswordEnsure.setEnabled(length >= 8);
            }
        });
        if (!TextUtils.isEmpty(password)) {
            mBind.etPassword.setText(password);
            mBind.etPassword.setSelection(password.length());
        }
        mBind.tvPasswordCancel.setOnClickListener(v -> {
            dismiss();
            if (passwordClickListener != null) {
                passwordClickListener.onDismiss();
            }
        });
        mBind.tvPasswordEnsure.setOnClickListener(v -> {
            dismiss();
            String password = mBind.etPassword.getText().toString();
            if (TextUtils.isEmpty(password)) {
                ToastUtils.showToast(getContext(), getContext().getString(R.string.password_null));
                return;
            }
//                if (password.length() != 8) {
//                    ToastUtils.showToast(getContext(), getContext().getString(R.string.main_password_length));
//                    return;
//                }
            if (passwordClickListener != null)
                passwordClickListener.onEnsureClicked(mBind.etPassword.getText().toString());
        });
        mBind.etPassword.postDelayed(() -> {
            //设置可获得焦点
            mBind.etPassword.setFocusable(true);
            mBind.etPassword.setFocusableInTouchMode(true);
            //请求获得焦点
            mBind.etPassword.requestFocus();
            //调用系统输入法
            InputMethodManager inputManager = (InputMethodManager) mBind.etPassword
                    .getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            inputManager.showSoftInput(mBind.etPassword, 0);
        }, 200);
    }

    @Override
    public int getDialogStyle() {
        return R.style.CenterDialog;
    }

    @Override
    public int getGravity() {
        return Gravity.CENTER;
    }

    @Override
    public String getFragmentTag() {
        return TAG;
    }

    @Override
    public float getDimAmount() {
        return 0.7f;
    }

    @Override
    public boolean getCancelOutside() {
        return false;
    }

    @Override
    public boolean getCancellable() {
        return true;
    }

    private PasswordClickListener passwordClickListener;

    public void setOnPasswordClicked(PasswordClickListener passwordClickListener) {
        this.passwordClickListener = passwordClickListener;
    }

    public interface PasswordClickListener {

        void onEnsureClicked(String password);

        void onDismiss();
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
