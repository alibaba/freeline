package com.antfortune.android.sample;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.widget.Toast;

/**
 * Created by huangyong on 16/7/25.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_4);
        Toast.makeText(this, "freeline toast", Toast.LENGTH_SHORT).show();
    }
}
