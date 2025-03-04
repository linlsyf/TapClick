package com.lgh.advertising.going.myactivity;

import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.lgh.advertising.going.R;
import com.lgh.advertising.going.myclass.MyApplication;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!MyApplication.myAppConfig.isVip) {
            View noVip = findViewById(R.id.no_vip);
            if (noVip == null) return;
            noVip.setVisibility(View.VISIBLE);
        }
    }
}
