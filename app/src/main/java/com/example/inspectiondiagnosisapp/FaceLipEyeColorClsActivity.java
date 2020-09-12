package com.example.inspectiondiagnosisapp;

import android.content.Context;
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
import android.hardware.camera2.CameraCharacteristics;
import android.media.ImageReader.OnImageAvailableListener;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

import com.example.inspectiondiagnosisapp.env.ImageUpload;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;

import com.example.inspectiondiagnosisapp.env.Logger;
import com.example.inspectiondiagnosisapp.tflite.SimilarityClassifier;
import com.example.inspectiondiagnosisapp.customview.OverlayView;
import com.example.inspectiondiagnosisapp.customview.OverlayView.DrawCallback;
import com.example.inspectiondiagnosisapp.env.BorderedText;
import com.example.inspectiondiagnosisapp.env.ImageUtils;
import com.example.inspectiondiagnosisapp.tflite.TFLiteFaceDetectionAPIModel;
import com.example.inspectiondiagnosisapp.tracking.MultiBoxTrackerFace;

import okhttp3.Response;


/**
 * An activity that uses a TensorFlowMultiBoxDetector and ObjectTracker to detect and then track
 * objects.
 */
public class FaceLipEyeColorClsActivity extends CameraFaceActivity implements OnImageAvailableListener {
    private static final Logger LOGGER = new Logger();

    // MobileFaceNet
    private static final int TF_OD_API_INPUT_SIZE = 300; //112

    private static final DetectorMode MODE = DetectorMode.TF_OD_API;
    // Minimum detection confidence to track a detection.
    private static final boolean MAINTAIN_ASPECT = false;

    private static final Size DESIRED_PREVIEW_SIZE = new Size(1920, 1080);

    private static final boolean SAVE_PREVIEW_BITMAP = false;
    private static final float TEXT_SIZE_DIP = 10;
    OverlayView trackingOverlay;
    private Integer sensorOrientation;

    private Bitmap rgbFrameBitmap = null;
    private Bitmap croppedBitmap = null;

    private boolean computingDetection = false;

    private long timestamp = 0;

    private Matrix frameToCropTransform;
    private Matrix cropToFrameTransform;

    private MultiBoxTrackerFace tracker;

    private BorderedText borderedText;

    // Face detector
    private FaceDetector faceDetector;

    // here the preview image is drawn in portrait way
    private Bitmap portraitBmp = null;
    // here the face is cropped and drawn
    private Bitmap faceBmp = null;

    //private HashMap<String, Classifier.Recognition> knownFaces = new HashMap<>();

    // 保存的第i张照片
    private int savePicId = 0;
    // 最多保存照片数量
    private static final int maxSaveImgNum = 5;
    // 记录清晰度序列的数组
    public double imgVarNum[] = new double[maxSaveImgNum];
    // 记录文件大小不为0的图像清晰度数组
    public double imgNotNoneSaved[] = new double[maxSaveImgNum];

    public File mostClearImgFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Real-time contour detection of multiple faces
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setContourMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .build();


        FaceDetector detector = FaceDetection.getClient(options);

        faceDetector = detector;

    }


    @Override
    public void onPreviewSizeChosen(final Size size, final int rotation) {
        final float textSizePx =
                TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP, getResources().getDisplayMetrics());
        borderedText = new BorderedText(textSizePx);
        borderedText.setTypeface(Typeface.MONOSPACE);

        tracker = new MultiBoxTrackerFace(this);

        previewWidth = size.getWidth();
        previewHeight = size.getHeight();

        sensorOrientation = rotation - getScreenOrientation();
        LOGGER.i("Camera orientation relative to screen canvas: %d", sensorOrientation);

        LOGGER.i("Initializing at size %dx%d", previewWidth, previewHeight);
        rgbFrameBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Config.ARGB_8888);


        int targetW, targetH;
        if (sensorOrientation == 90 || sensorOrientation == 270) {
            targetH = previewWidth;
            targetW = previewHeight;
        }
        else {
            targetW = previewWidth;
            targetH = previewHeight;
        }
        int cropW = (int) (targetW / 2.0);
        int cropH = (int) (targetH / 2.0);

        croppedBitmap = Bitmap.createBitmap(cropW, cropH, Config.ARGB_8888);

        portraitBmp = Bitmap.createBitmap(targetW, targetH, Config.ARGB_8888);
        faceBmp = Bitmap.createBitmap(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, Config.ARGB_8888);

        frameToCropTransform =
                ImageUtils.getTransformationMatrix(
                        previewWidth, previewHeight,
                        cropW, cropH,
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
    }


    @Override
    protected void processImage() {
        ++timestamp;
        final long currTimestamp = timestamp;
        trackingOverlay.postInvalidate();

        // No mutex needed as this method is not reentrant.
        if (computingDetection) {
            readyForNextImage();
            return;
        }
        computingDetection = true;

        LOGGER.i("Preparing image " + currTimestamp + " for detection in bg thread.");

        rgbFrameBitmap.setPixels(getRgbBytes(), 0, previewWidth, 0, 0, previewWidth, previewHeight);

        readyForNextImage();

        final Canvas canvas = new Canvas(croppedBitmap);
        canvas.drawBitmap(rgbFrameBitmap, frameToCropTransform, null);
//        // For examining the actual TF input.
//        if (SAVE_PREVIEW_BITMAP) {
//            ImageUtils.saveBitmap(croppedBitmap);
//        }

        InputImage image = InputImage.fromBitmap(croppedBitmap, 0);
        faceDetector
                .process(image)
                .addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                    @Override
                    public void onSuccess(List<Face> faces) {
                        if (faces.size() == 0) {
                            updateResults(currTimestamp, new LinkedList<>());
                            return;
                        }
                        runInBackground(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            onFacesDetected(currTimestamp, faces);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                    }

                });


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

    // Face Processing
    private Matrix createTransform(
            final int srcWidth,
            final int srcHeight,
            final int dstWidth,
            final int dstHeight,
            final int applyRotation) {

        Matrix matrix = new Matrix();
        if (applyRotation != 0) {
            if (applyRotation % 90 != 0) {
                LOGGER.w("Rotation of %d % 90 != 0", applyRotation);
            }

            // Translate so center of image is at origin.
            matrix.postTranslate(-srcWidth / 2.0f, -srcHeight / 2.0f);

            // Rotate around origin.
            matrix.postRotate(applyRotation);
        }

        if (applyRotation != 0) {

            // Translate back from origin centered reference to destination frame.
            matrix.postTranslate(dstWidth / 2.0f, dstHeight / 2.0f);
        }

        return matrix;

    }


    private void updateResults(long currTimestamp, final List<SimilarityClassifier.Recognition> mappedRecognitions) {

        tracker.trackResults(mappedRecognitions, currTimestamp);
        trackingOverlay.postInvalidate();
        computingDetection = false;
        //adding = false;

    }

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

    private void onFacesDetected(long currTimestamp, List<Face> faces) throws IOException {
        Context context = FaceLipEyeColorClsActivity.this;
        final String root = context.getExternalFilesDir(null) + "/face";
        final File myDir = new File(root);
        myDir.mkdirs();

        // 如果检测到人脸就跳转到这里执行
        final Paint paint = new Paint();
        paint.setColor(Color.RED);
        paint.setStyle(Style.STROKE);
        paint.setStrokeWidth(2.0f);

        final List<SimilarityClassifier.Recognition> mappedRecognitions =
                new LinkedList<>();

        // Note this can be done only once
        int sourceW = rgbFrameBitmap.getWidth();
        int sourceH = rgbFrameBitmap.getHeight();
        int targetW = portraitBmp.getWidth();
        int targetH = portraitBmp.getHeight();
        Matrix transform = createTransform(
                sourceW,
                sourceH,
                targetW,
                targetH,
                sensorOrientation);
        final Canvas cv = new Canvas(portraitBmp);

        // draws the original image in portrait mode.
        cv.drawBitmap(rgbFrameBitmap, transform, null);

        final Canvas cvFace = new Canvas(faceBmp);

//        boolean saved = false;

        for (Face face : faces) {

            LOGGER.i("FACE" + face.toString());
            LOGGER.i("Running detection on face " + currTimestamp);

            final RectF boundingBox = new RectF(face.getBoundingBox());

            if (boundingBox != null) {

                // maps crop coordinates to original
                cropToFrameTransform.mapRect(boundingBox);

                // maps original coordinates to portrait coordinates
                RectF faceBB = new RectF(boundingBox);
                transform.mapRect(faceBB);

                // translates portrait to origin and scales to fit input inference size
                //cv.drawRect(faceBB, paint);
                float sx = ((float) TF_OD_API_INPUT_SIZE) / faceBB.width();
                float sy = ((float) TF_OD_API_INPUT_SIZE) / faceBB.height();
                Matrix matrix = new Matrix();
                matrix.postTranslate(-faceBB.left, -faceBB.top);
                matrix.postScale(sx, sy);

                cvFace.drawBitmap(portraitBmp, matrix, null); //保存在faceBmp变量中


                if (getCameraFacing() == CameraCharacteristics.LENS_FACING_FRONT) {

                    // camera is frontal so the image is flipped horizontally
                    // flips horizontally
                    Matrix flip = new Matrix();
                    if (sensorOrientation == 90 || sensorOrientation == 270) {
                        flip.postScale(1, -1, previewWidth / 2.0f, previewHeight / 2.0f);
                    }
                    else {
                        flip.postScale(-1, 1, previewWidth / 2.0f, previewHeight / 2.0f);
                    }
                    //flip.postScale(1, -1, targetW / 2.0f, targetH / 2.0f);
                    flip.mapRect(boundingBox);

                }

                final SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition(
                        "0", "", -1f, boundingBox);

                if (boundingBox != null){
                    double[] imageVar = getImageVar(faceBmp);
                    LOGGER.i("imageVar = " + imageVar[0]);
                    if (imageVar[0] > 6){
                        if (savePicId < maxSaveImgNum){
                            imgVarNum[savePicId] = imageVar[0];
                            savePicId += 1;

                            final String fname = Double.toString(imageVar[0]) + ".jpeg";
                            final File file = new File(myDir, fname);

                            final FileOutputStream out = new FileOutputStream(file);
                            faceBmp.compress(Bitmap.CompressFormat.JPEG, 100, out);//通过io流的方式来压缩保存图片

                            out.flush();
                            out.close();
                            LOGGER.i("保存人脸图像成功!");
                        }
                        else{
                            Intent intent = new Intent();
                            intent.setClass(FaceLipEyeColorClsActivity.this, DetectorActivity.class);
                            Arrays.sort(imgVarNum);
                            System.out.println(Arrays.toString(imgVarNum));

                            savePicId = 0;
                            for (int i = 0; i < imgVarNum.length; i++){
                                File tmpFile = new File(context.getExternalFilesDir(null) + "/face/",
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
                                FaceLipEyeColorClsActivity.this.startActivity(intent);
                            }else{
                                mostClearImgFile = new File(context.getExternalFilesDir(null) + "/face/",
                                        imgNotNoneSaved[savePicId-1] + ".jpeg");
                                System.out.println(mostClearImgFile);
                                Response response = ImageUpload.send(mostClearImgFile); // TODO:上传人脸照片到面诊接口

                                try {
                                    //  System.out.println(response.body().string());
                                    // 在前面加这句话会报错，因为response.body().string()只能调用一次，这样下面就是null
                                    Bundle bundle = new Bundle();
                                    bundle.putString("results", response.body().string());
                                    intent.putExtras(bundle);

                                    for (File file: new File(context.getExternalFilesDir(null) + "/face/").listFiles()){
                                        // 删除文件夹下，除了mostClearImgFile以外的所有舌像
                                        if(!file.equals(mostClearImgFile)){
                                            file.delete();
                                        }
//                                        // 删除所有舌象
//                                        file.delete();
                                    }
                                    finish();
                                    FaceLipEyeColorClsActivity.this.startActivity(intent);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                }

                result.setColor(Color.BLUE);
                result.setLocation(boundingBox);
                mappedRecognitions.add(result);

            }


        }

        updateResults(currTimestamp, mappedRecognitions);


    }


}
