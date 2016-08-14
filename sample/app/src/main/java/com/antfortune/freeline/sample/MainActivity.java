package com.antfortune.freeline.sample;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import com.antfortune.freeline.sample.payment.PaymentUtils;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PaymentUtils.showMessage(this);
    }
}
