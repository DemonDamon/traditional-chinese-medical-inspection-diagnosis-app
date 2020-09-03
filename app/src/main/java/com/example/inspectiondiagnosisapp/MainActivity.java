package com.example.inspectiondiagnosisapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.FaceDetector;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.opencv.features2d.BOWTrainer;

public class MainActivity extends AppCompatActivity{
    Button btn1, btn2;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_main);
        setContentView(R.layout.activity_login);

        btn1 = (Button)findViewById(R.id.btn1);
        btn2 = (Button)findViewById(R.id.btn2);

        btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, FaceLipEyeColorClsActivity.class);
                MainActivity.this.startActivity(intent);
                // startActivity(new Intent(this, FaceLipEyeColorClsActivity.class)); // 上面三条语句对比下面一条语句
            }
        });

        btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, DetectorActivity.class);
                MainActivity.this.startActivity(intent);
                // startActivity(new Intent(this, DetectorActivity.class));
            }
        });
    }
}
