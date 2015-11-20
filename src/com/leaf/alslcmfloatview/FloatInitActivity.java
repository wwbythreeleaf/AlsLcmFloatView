package com.leaf.alslcmfloatview;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.view.Menu;

public class FloatInitActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_float_init);
        Intent intent = new Intent(FloatInitActivity.this, DataServer.class);  
        startService(intent); 
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_float_init, menu);
        return true;
    }

    
}
