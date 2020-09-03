package com.example.inspectiondiagnosisapp;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;

import com.example.inspectiondiagnosisapp.env.ImageUpload;
import com.example.inspectiondiagnosisapp.env.Logger;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import okhttp3.Response;


public class ThirdActivity extends AppCompatActivity{

    private static final Logger LOGGER = new Logger();

    TextView tv1, tv2, tv3, tv4, tv5, tv6, tv7, tv8, tv9, tv10, tv11, tv12;
    TextView prob1, prob2, prob3, prob4, prob5, prob6, prob7, prob8, prob9, prob10, prob11, prob12;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.third_activity);
        Bundle bb = this.getIntent().getExtras();
        String tongueClsResults = bb.getString("results");

        tv1 = (TextView)findViewById(R.id.tv1);
        prob1 = (TextView)findViewById(R.id.prob1);

        tv2 = (TextView)findViewById(R.id.tv2);
        prob2 = (TextView)findViewById(R.id.prob2);

        tv3 = (TextView)findViewById(R.id.tv3);
        prob3 = (TextView)findViewById(R.id.prob3);

        tv4 = (TextView)findViewById(R.id.tv4);
        prob4 = (TextView)findViewById(R.id.prob4);

        tv5 = (TextView)findViewById(R.id.tv5);
        prob5 = (TextView)findViewById(R.id.prob5);

        tv6 = (TextView)findViewById(R.id.tv6);
        prob6 = (TextView)findViewById(R.id.prob6);

        tv7 = (TextView)findViewById(R.id.tv7);
        prob7 = (TextView)findViewById(R.id.prob7);

        tv8 = (TextView)findViewById(R.id.tv8);
        prob8 = (TextView)findViewById(R.id.prob8);

        tv9 = (TextView)findViewById(R.id.tv9);
        prob9 = (TextView)findViewById(R.id.prob9);

        tv10 = (TextView)findViewById(R.id.tv10);
        prob10 = (TextView)findViewById(R.id.prob10);

        tv11 = (TextView)findViewById(R.id.tv11);
        prob11 = (TextView)findViewById(R.id.prob11);

        tv12 = (TextView)findViewById(R.id.tv12);
        prob12 = (TextView)findViewById(R.id.prob12);

        if (tongueClsResults == null){
            Toast.makeText(getApplicationContext(),
                    "本机舌像保存出错",
                    Toast.LENGTH_LONG).show();
        }
        else{
            try {
                JSONObject jsonResultsObject = new JSONObject(tongueClsResults);
//            JSONArray jsonResultsArray = jsonResultsObject.getJSONArray("preds");

                tv1.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("tongue_color").get("name").toString()
                        .replace("舌质", ""));
                prob1.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("tongue_color").get("prob").toString());

                tv2.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("rear_fur_color").get("name").toString()
                        .replace("舌根", ""));
                prob2.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("rear_fur_color").get("prob").toString());

                tv3.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("rear_fur_thickness").get("name").toString()
                        .replace("舌根", ""));
                prob3.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("rear_fur_thickness").get("prob").toString());

                tv4.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("middle_fur_color").get("name").toString()
                        .replace("舌中", ""));
                prob4.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("middle_fur_color").get("prob").toString());

                tv5.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("middle_fur_thickness").get("name").toString()
                        .replace("舌中", ""));
                prob5.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("middle_fur_thickness").get("prob").toString());

                if (jsonResultsObject.getJSONObject("preds").getJSONObject("petechiae").get("name").toString()
                        .replace("舌质", "").contains("无"))
                    tv6.setText("无");
                else
                    tv6.setText("有");
                prob6.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("petechiae").get("prob").toString());

                if (jsonResultsObject.getJSONObject("preds").getJSONObject("ecchymosis").get("name").toString()
                        .replace("舌质", "").contains("无"))
                    tv7.setText("无");
                else
                    tv7.setText("有");
                prob7.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("ecchymosis").get("prob").toString());

                if (jsonResultsObject.getJSONObject("preds").getJSONObject("teeth_print").get("name").toString().contains("无"))
                    tv8.setText("无");
                else
                    tv8.setText("有");
                prob8.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("teeth_print").get("prob").toString());

                if (jsonResultsObject.getJSONObject("preds").getJSONObject("fissured_tongue").get("name").toString().contains("无"))
                    tv9.setText("无");
                else
                    tv9.setText("有");
                prob9.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("fissured_tongue").get("prob").toString());

                if (jsonResultsObject.getJSONObject("preds").getJSONObject("curdy_and_greasy_fur").get("name").toString().contains("无"))
                    tv10.setText("无");
                else
                    tv10.setText("有");
                prob10.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("curdy_and_greasy_fur").get("prob").toString());

                if (jsonResultsObject.getJSONObject("preds").getJSONObject("eroded_fur").get("name").toString().contains("无"))
                    tv11.setText("无");
                else
                    tv11.setText("有");
                prob11.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("eroded_fur").get("prob").toString());

                if (jsonResultsObject.getJSONObject("preds").getJSONObject("red_point_tongue").get("name").toString().contains("无"))
                    tv12.setText("无");
                else
                    tv12.setText("有");
                prob12.setText(jsonResultsObject.getJSONObject("preds").getJSONObject("red_point_tongue").get("prob").toString());

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
}
