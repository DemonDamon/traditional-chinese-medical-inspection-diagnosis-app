package com.example.inspectiondiagnosisapp.env;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImageUpload {

    public static String postUrl = "http://183.6.50.74:25228/predict";
    private static final MediaType MEDIA_TYPE_JPEG = MediaType.parse("image/jpeg");
    private static final OkHttpClient client = new OkHttpClient();


    public static Response send(File f) {
        final File file = f;
        //子线程需要做的工作
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", file.getName(),
                        RequestBody.create(MEDIA_TYPE_JPEG, file))
                .build();
        //设置为自己的ip地址
        Request request = new Request.Builder()
                .header("Content-Type", "application/json")
                .url(postUrl)
                .post(requestBody)
                .build();
        try {
            Response response = client.newCall(request).execute();
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

//            System.out.println(response.body().string());
            return response;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}

