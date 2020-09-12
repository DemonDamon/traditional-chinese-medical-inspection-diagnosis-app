package com.example.inspectiondiagnosisapp;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.widget.Toast;
import android.content.Context;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.io.File;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;

import com.example.inspectiondiagnosisapp.env.Logger;
import com.example.inspectiondiagnosisapp.tflite.Classifier;
import com.example.inspectiondiagnosisapp.customview.OverlayView;
import com.example.inspectiondiagnosisapp.customview.OverlayView.DrawCallback;
import com.example.inspectiondiagnosisapp.env.BorderedText;
import com.example.inspectiondiagnosisapp.env.ImageUpload;
import com.example.inspectiondiagnosisapp.env.ImageUtils;
import com.example.inspectiondiagnosisapp.tflite.TFLiteObjectDetectionAPIModel;
import com.example.inspectiondiagnosisapp.tracking.MultiBoxTracker;

import java.io.File;
import java.io.IOException;

import okhttp3.Response;

import static java.util.Arrays.*;


public class DetectorActivity extends CameraActivity implements
        ImageReader.OnImageAvailableListener {

    private static final Logger LOGGER = new Logger();
    // 为SSD模型配置参数
    private static final int TF_OD_API_INPUT_SIZE = 300;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "tongue_detect_1.tflite";
    private static final String TF_OD_API_LABELS_FILE = "file:///android_asset/tonguelabelmap.txt";
    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // 最小的检测置信度
    private static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.98f; // 手机就0.999f，中医机器人就0.98f
    private static final boolean MAINTAIN_ASPECT = false;
    // 设置设备摄像头的像素，width在1440，height在1080时，裁截图片的大小在1M以内
    private static final Size DESIRED_PREVIEW_SIZE = new Size(1920, 1080); //手机landscape就是w=1080,h=1920;portrait就是w=1920,h=1080
    private static final boolean SAVE_PREVIEW_BITMAP = true;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Classifier detector;

    private Bitmap rgbFrameBitmap = null; // 手机拍摄的原图
    private Bitmap croppedBitmap = null;
    private Bitmap cropCopyBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTracker tracker;

    private BorderedText borderedText;

    // 保存的第i张照片
    private int savePicId = 0;
    // 最多保存照片数量
    private static final int maxSaveImgNum = 5;
    // 记录清晰度序列的数组
    public double imgVarNum[] = new double[maxSaveImgNum];
    // 记录文件大小不为0的图像清晰度数组
    public double imgNotNoneSaved[] = new double[maxSaveImgNum];

    public File mostClearImgFile;

    public static double[] getImageVar(Bitmap bitmap) {
        Mat src = new Mat();
        Mat temp = new Mat();
        Mat dst_1 = new Mat();
        Mat dst_2 = new Mat();
        Utils.bitmapToMat(bitmap, src);
        Imgproc.cvtColor(src, temp, Imgproc.COLOR_BGRA2BGR);
        Log.i("CV", "image type:" + (temp.type() == CvType.CV_8UC3));
        Imgproc.cvtColor(temp, dst_1, Imgproc.COLOR_BGR2GRAY);
        Imgproc.Laplacian(dst_1, dst_2, CvType.CV_8UC3);

        MatOfDouble means = new MatOfDouble();
        MatOfDouble stddevs = new MatOfDouble();
        Core.meanStdDev(dst_2, means, stddevs);
        double[] stddev = stddevs.toArray();
//    Utils.matToBitmap(dst, bitmap);
        return stddev;
    }

    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        LOGGER.i("step in onPreviewSizeChosen function");
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTracker(this);

        int cropSize = TF_OD_API_INPUT_SIZE; // 初始值为300

        try {
            detector =
                    TFLiteObjectDetectionAPIModel.create(
                            getAssets(),
                            TF_OD_API_MODEL_FILE,
                            TF_OD_API_LABELS_FILE,
                            TF_OD_API_INPUT_SIZE,
                            TF_OD_API_IS_QUANTIZED);
            cropSize = TF_OD_API_INPUT_SIZE;
        } catch (final IOException e) {
            e.printStackTrace();
            LOGGER.e(e, "Exception initializing classifier!");
            Toast toast =
                    Toast.makeText(
                            getApplicationContext(), "Classifier could not be initialized", Toast.LENGTH_SHORT);
            toast.show();
            finish();
        }

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();
        LOGGER.i("previewWidth:" + previewWidth + ", previewHeight:" + previewHeight);
//    sensorOrientation = rotation - getScreenOrientation();
        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("rotation:" + rotation + ", getScreenOrientation:" + getScreenOrientation());
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        croppedBitmap = Bitmap.createBitmap(cropSize, cropSize, Bitmap.Config.ARGB_8888); // 300*300的框，没有像素点

        // 将照相机获取的原始图片，转换为300*300的图片，用来作为模型预测的输入
        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropSize, cropSize,
                        sensorOrientation, MAINTAIN_ASPECT);

        cropToFrameTransform = new Matrix();
        frameToCropTransform.invert(cropToFrameTransform);

        trackingOverlay = (OverlayView) findViewById(R.id.tracking_overlay);
        trackingOverlay.addCallback(
                new DrawCallback() {
                    @Override
                    public void drawCallback(final Canvas canvas) {
                        tracker.draw(canvas);
                        if (isDebug()) {
                            tracker.drawDebug(canvas);
                        }
                    }
                });

        tracker.setFrameConfiguration(previewWidth, previewHeight, sensorOrientation);
        LOGGER.i("step out onPreviewSizeChosen function");
    }

    @Override
    protected void processImage() {
        LOGGER.i("step in processImage function...");
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate(); //不在UIThread中，要通知系统视图已更改时，用postInvalidate()

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;
        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap); // croppedBitmap作为canvas操作对象
        // 用特定的矩阵frameToCropTransform来画rgbFrameBitmap这个bitmap，即图像变换(或矩阵变换)
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null); // 将rgbFrameBitmap转换成300*300图像画到croppedBitmap对象中
        // For examining the actual TF input.
//        if (SAVE_PREVIEW_BITMAP) {
//          ImageUtils.saveBitmap(croppedBitmap);
//        }

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        Context context = DetectorActivity.this;
                        LOGGER.i("Running detection on image " + currTimestamp);
                        final long startTime = SystemClock.uptimeMillis(); // 从开机到现在的毫秒数（手机睡眠的时间不包括在内）；
                        // 有时候执行到这里便跳到执行前面的trackingOverlay.postInvalidate()，画布更新重新执行
                        // CameraActivity.java中的onPreviewFrame函数
                        // 使得computingDetection参数一直为True，这样就无法继续下面的图像检测
                        final List<Classifier.Recognition> results = detector.recognizeImage(croppedBitmap);

                        cropCopyBitmap = Bitmap.createBitmap(croppedBitmap); // 完全复制croppedBitmap
                        final Canvas canvas = new Canvas(cropCopyBitmap);
                        final Paint paint = new Paint();
                        paint.setColor(Color.RED);
                        paint.setStyle(Paint.Style.STROKE); // 空心的矩形，只有描边
                        paint.setStrokeWidth(2.0f);

                        float minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                        switch (MODE) {
                            case TF_OD_API:
                                minimumConfidence = MINIMUM_CONFIDENCE_TF_OD_API;
                                break;
                        }

                        final List<Classifier.Recognition> mappedRecognitions =
                                new LinkedList<Classifier.Recognition>();

                        for (final Classifier.Recognition result : results) {
                            final RectF location = result.getLocation();
                            if (location != null && result.getConfidence() >= minimumConfidence) {
                                // 计算bitmap的清晰度系数imageVar[0]
                                double[] imageVar = getImageVar(rgbFrameBitmap);
                                LOGGER.i("imageVar = " + imageVar[0]);
                                // 满足两个条件保存图片到本地设备：
                                // 1.检测到舌体
                                // 2.舌体图像清晰度达到阈值要求(拉普拉斯清晰度阈值设定为5)
                                if (imageVar[0] > 5) {
                                    if (savePicId < maxSaveImgNum){
                                        imgVarNum[savePicId] = imageVar[0];
                                        savePicId += 1;

                                        canvas.drawRect(location, paint);

                                        // 将300*300的图片转回到照相机获取的原始图片大小
                                        cropToFrameTransform.mapRect(location);

                                        if (SAVE_PREVIEW_BITMAP) {

                                            File file = ImageUtils.saveBitmap(context, rgbFrameBitmap, location, imageVar[0], "tongue");
//                                            // 登陆后台请求舌像分类服务
//                                            final long startTime1 = SystemClock.uptimeMillis();
//                                            ImageUpload.send(file);
//                                            lastProcessingTimeMs1 = SystemClock.uptimeMillis() - startTime1;
//                                            LOGGER.i("成功请求舌像分类服务，请求响应的总时间是"+lastProcessingTimeMs1+"ms!");
                                        }

                                        // location = RectF(258.85806, 73.32022, 544.8867, 327.85156)
                                        // result = [0] tongue (100.0%) RectF(258.85806, 73.32022, 544.8867, 327.85156) = left, top, right, bottom
                                        // mappedRecognitions = [[0] tongue (100.0%) RectF(258.85806, 73.32022, 544.8867, 327.85156)]
                                        result.setLocation(location);
                                        mappedRecognitions.add(result);
                                    }
                                    else{
                                        Intent intent = new Intent();
                                        intent.setClass(DetectorActivity.this, ThirdActivity.class);
                                        Arrays.sort(imgVarNum);
                                        System.out.println(Arrays.toString(imgVarNum));

                                        savePicId = 0;
                                        for (int i = 0; i < imgVarNum.length; i++){
                                            File tmpFile = new File(context.getExternalFilesDir(null) + "/tongue/",
                                                    imgVarNum[i] + ".jpeg");
                                            if (tmpFile.length()==0){
                                                tmpFile.delete();
                                            }else{
                                                imgNotNoneSaved[savePicId] = imgVarNum[i];
                                                savePicId++;
                                            }
                                        }
                                        if (savePicId==0){
                                            Bundle bundle = new Bundle();
                                            bundle.putString("results", null);
                                            intent.putExtras(bundle);
                                            finish();
                                            DetectorActivity.this.startActivity(intent);
                                        }else{
                                            mostClearImgFile = new File(context.getExternalFilesDir(null) + "/tongue/",
                                                    imgNotNoneSaved[savePicId-1] + ".jpeg");
                                            System.out.println(mostClearImgFile);
                                            Response response = ImageUpload.send(mostClearImgFile);

                                            try {
                                                //  System.out.println(response.body().string());
                                                // 在前面加这句话会报错，因为response.body().string()只能调用一次，这样下面就是null
                                                Bundle bundle = new Bundle();
                                                bundle.putString("results", response.body().string());
                                                intent.putExtras(bundle);

                                                for (File file: new File(context.getExternalFilesDir(null) + "/tongue/").listFiles()){
                                                    // 删除文件夹下，除了mostClearImgFile以外的所有舌像
                                                    if(!file.equals(mostClearImgFile)){
                                                        file.delete();
                                                    }
//                                                    // 删除所有舌象
//                                                    file.delete();
                                                }
                                                finish();
                                                DetectorActivity.this.startActivity(intent);
                                            } catch (IOException e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        tracker.trackResults(mappedRecognitions, currTimestamp);
                        //每次这里就会回调MultiBoxTracker.java的draw函数，因为在138行加入了DrawCallback回调函数
                        //回调的是MultiBoxTracker的draw函数
                        //画面发生了更改，重新调用CameraActivity.java的onPreviewFrame函数
                        trackingOverlay.postInvalidate();

                        computingDetection = false;

                    }
                });
        LOGGER.i("step out processImage function...");
    }

    @Override
    protected int getLayoutId() {
        return R.layout.camera_connection_fragment_tracking;
    }

    @Override
    protected Size getDesiredPreviewFrameSize() {
        return DESIRED_PREVIEW_SIZE;
    }

    // Which detection model to use: by default uses Tensorflow Object Detection API frozen
    // checkpoints.
    private enum DetectorMode {
        TF_OD_API;
    }

    @Override
    protected void setUseNNAPI(final boolean isChecked) {
        runInBackground(() -> detector.setUseNNAPI(isChecked));
    }

    @Override
    protected void setNumThreads(final int numThreads) {
        runInBackground(() -> detector.setNumThreads(numThreads));
    }

}
