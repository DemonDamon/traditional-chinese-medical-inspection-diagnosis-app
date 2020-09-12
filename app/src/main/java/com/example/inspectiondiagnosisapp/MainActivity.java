package com.example.inspectiondiagnosisapp;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.media.FaceDetector;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import org.opencv.features2d.BOWTrainer;

public class MainActivity extends AppCompatActivity{
    ImageView img_inspect, img_qa;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select);

        img_inspect = (ImageView)findViewById(R.id.img_inspect);
        img_qa = (ImageView)findViewById(R.id.img_qa);

        img_inspect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, FaceLipEyeColorClsActivity.class);
                MainActivity.this.startActivity(intent);
                // startActivity(new Intent(this, FaceLipEyeColorClsActivity.class)); // 上面三条语句对比下面一条语句
            }
        });

        img_qa.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, TCMChatbotActivity.class);
                MainActivity.this.startActivity(intent);
                // startActivity(new Intent(this, DetectorActivity.class));
            }
        });
    }
}


