package com.example.inspectiondiagnosisapp;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class PermissionUtil {
    private static final String[] permissions = new String[]{
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    public static final int ROBOT_PERMISSION_CODE = 10000;

    public static void setActivePermissions(Activity activity) {
        boolean isAllGranted = checkPermissionAllGranted(activity, permissions);

        if (isAllGranted) {
            return;
        }
        // 一次请求多个权限, 如果其他有权限是已经授予的将会自动忽略掉
        ActivityCompat.requestPermissions(activity, permissions, ROBOT_PERMISSION_CODE);
    }

    /**
     * 检查是否拥有指定的所有权限
     */
    private static boolean checkPermissionAllGranted(Activity activity, String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                // 只要有一个权限没有被授予, 则直接返回 false
                return false;
            }
        }
        return true;
    }

}
