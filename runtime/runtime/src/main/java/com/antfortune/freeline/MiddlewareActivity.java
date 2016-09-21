package com.antfortune.freeline;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;

public class MiddlewareActivity extends Activity {
    private static final String TAG = "Freeline.MiddlewareAct";
    private static final Handler HANDLER = new Handler(Looper.getMainLooper());
    private static final long RESET_WAIT = 1000L;
    private long createTime;
    private boolean ready;
    private int back;
    private final Runnable reset = new Runnable() {
        public void run() {
            Log.d(TAG, "kill process: " + Process.myPid());
            Process.killProcess(Process.myPid());
        }

    };

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setGravity(17);
        tv.setText("building increment app...");
        setContentView(tv);
        this.createTime = SystemClock.uptimeMillis();

        this.ready = getIntent().getBooleanExtra("reset", false);
        if (this.ready) {
            reset();
        }
    }


    protected void onDestroy() {
        HANDLER.removeCallbacks(this.reset);
        super.onDestroy();
    }


    public void reset() {
        this.ready = true;
        HANDLER.removeCallbacks(this.reset);
        long d = SystemClock.uptimeMillis() - this.createTime;
        if (d > RESET_WAIT) {
            HANDLER.postDelayed(this.reset, 100L);
        } else {
            HANDLER.postDelayed(this.reset, RESET_WAIT - d);
        }
    }


    public void onBackPressed() {
        if (this.back++ > 0) {
            if (this.ready) {
                this.reset.run();
            }
            super.onBackPressed();
        }
    }

}
