package com.antfortune.freeline.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.widget.Toast;
import com.antfortune.freeline.sample.payment.PaymentUtils;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        PaymentUtils.showMessage(this);

        TextView textView = findViewById(R.id.text);

        // test lambda method
        textView.setOnClickListener(view ->
                Toast.makeText(MainActivity.this, "click from lambda", Toast.LENGTH_SHORT).show()
        );
    }

}
