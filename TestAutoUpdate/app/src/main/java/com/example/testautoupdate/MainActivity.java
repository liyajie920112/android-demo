package com.example.testautoupdate;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.FileCallBack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "liyajie";
    Button btn;
    private float pro = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn = findViewById(R.id.test);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String version = getCurrentVersion();
                btn.setText(version);
                checkUpdate(new CheckUpdateCallback<CheckUpdateModel>() {
                    @Override
                    public void onNewVersion(CheckUpdateModel model) {
                        Toast.makeText(MainActivity.this, "发现新版本", Toast.LENGTH_SHORT).show();
                        AlertDialog.Builder dialog = new AlertDialog.Builder(MainActivity.this);
                        dialog.setTitle("更新提示");
                        if (isWifi()) {
                            dialog.setMessage("发现新版本:" + model.getBuildVersion());
                        } else {
                            double size = Double.parseDouble(model.getBuildFileSize()) / (1024 * 1024);
                            dialog.setMessage("发现新版本 :" + model.getBuildVersion() + " 当前为移动网络, 立即更新将消耗移动流量" + String.format("%.2f", size) + "M");
                        }
                        dialog.setCancelable(false);
                        if (!model.getNeedForceUpdate()) {
                            dialog.setPositiveButton("取消", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            });
                        }
                        dialog.setNegativeButton("立即更新", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                                // 下载安装文件
                                downloadApk(model);
                            }
                        });
                        AlertDialog d = dialog.show();
                    }

                    @Override
                    public void onNone() {
                        Toast.makeText(MainActivity.this, "已经是最新版本", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(MainActivity.this, "检测失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        initPermision();
    }

    private static String[] permissions = new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA
    };

    private void initPermision() {
        List<String> list = new ArrayList<>();
        for (String permission : permissions) {
            int checkPermission = ContextCompat.checkSelfPermission(this, permission);
            if (checkPermission == PackageManager.PERMISSION_DENIED) {
                list.add(permission);
            }
        }
        String[] d = list.toArray(new String[list.size()]);
        if (d.length > 0) {
            ActivityCompat.requestPermissions(this, d, 100);
        }
    }

    ProgressDialog bar;

    private ProgressDialog createProgressDialog() {
        if (bar == null) {
            bar = new ProgressDialog(this);
            bar.setMax(100);
            bar.setCanceledOnTouchOutside(false);
            bar.setMessage("下载中...");
            bar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            return bar;
        }
        return bar;
    }

    private void downloadApk(CheckUpdateModel model) {
        // 执行下载操作, 并下载完成后安装
        String downloadUrl = model.getDownloadURL();
        String downloadPath = Environment.DIRECTORY_DCIM + File.separator + "/download/";
        File dir = getExternalFilesDir(downloadPath);
        if (!dir.exists()) {
            dir.mkdir();
        }
        String realDownloadPath = dir.getPath();
        String fileName = getPackageName() + "-" + model.getBuildVersion() + ".apk";
        String tempFileName = "bak-" + fileName;
        // 如果临时文件已经存在, 则直接删除临时文件
        File tempFile = new File(dir.getPath() + File.separator + tempFileName);
        if (tempFile.exists()) {
            tempFile.delete();
        }
        File apk = new File(dir.getPath() + File.separator + fileName);
        if (apk.exists()) {
            // 如果已经存在, 则直接安装
            installApk(apk);
            return;
        }
        // 显示进度条
        createProgressDialog().show();
        OkHttpUtils.get().url(downloadUrl).build().execute(new FileCallBack(realDownloadPath, tempFileName) {
            @Override
            public void onError(Call call, Exception e, int id) {
                createProgressDialog().dismiss();
                // 下载失败后删除临时文件
                File oldFile = new File(dir.getPath() + File.separator + tempFileName);
                if (oldFile.exists()) {
                    oldFile.delete();
                }
                Toast.makeText(MainActivity.this, "下载失败", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onResponse(File response, int id) {
                createProgressDialog().setMessage("下载完成");
                createProgressDialog().dismiss();
                // 下载完成后, 重命名文件
                File oldFile = new File(dir.getPath() + File.separator + tempFileName);
                File newFile = new File(dir.getPath() + File.separator + fileName);
                oldFile.renameTo(newFile);
                // 安装
                installApk(newFile);
            }

            @Override
            public void inProgress(float progress, long total, int id) {
                if (progress > pro) {
                    createProgressDialog().setProgress((int) (progress * 100));
                    pro = progress;
                }
            }
        });
    }

    void installApk(File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Uri apkUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        } else {
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri = Uri.fromFile(file);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
        }
        startActivity(intent);
    }

    void checkUpdate(CheckUpdateCallback cb) {
        String checkUpdateUrl = "https://www.pgyer.com/apiv2/app/check";
        String apiKey = getMetaData("PGY_API_KEY");
        String appKey = getMetaData("PGY_APP_KEY");
        OkHttpUtils.post()
                .url(checkUpdateUrl)
                .addParams("_api_key", apiKey)
                .addParams("appKey", appKey)
                .build()
                .execute(new JsonCallback<Result<CheckUpdateModel>>() {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        Toast.makeText(MainActivity.this, "失败", Toast.LENGTH_SHORT).show();
                        cb.onError(e);
                    }

                    @Override
                    public void onResponse(Result<CheckUpdateModel> response, int id) {
                        CheckUpdateModel checkUpdateModel = response.getData();
                        String remoteVersion = checkUpdateModel.getBuildVersion();
                        String curVersion = getCurrentVersion();
                        int r = compareVersion(remoteVersion, curVersion); // 0:版本一致, -1: 当前版本高, 1: 远程版本高
                        if (r == 0) {
                            cb.onNone();
                        } else if (r == 1) {
                            cb.onNewVersion(checkUpdateModel);
                        }
                    }
                });
    }

    public String getCurrentVersion() {
        // 获取当前版本号
        PackageManager packageManager = getPackageManager();
        try {
            PackageInfo info = packageManager.getPackageInfo(getBaseContext().getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return "";
        }
    }

    public String getMetaData(String key) {
        try {
            ApplicationInfo info = getPackageManager().getApplicationInfo(getBaseContext().getPackageName(), PackageManager.GET_META_DATA);
            return info.metaData.getString(key);
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    public static int compareVersion(String version1, String version2) {
        if (version1.equals(version2)) {
            return 0;
        }
        String[] version1Array = version1.split("\\.");
        String[] version2Array = version2.split("\\.");

        int index = 0;
        // 获取最小长度值
        int minLen = Math.min(version1Array.length, version2Array.length);
        int diff = 0;
        // 循环判断每位的大小
        while (index < minLen
                && (diff = Integer.parseInt(version1Array[index])
                - Integer.parseInt(version2Array[index])) == 0) {
            index++;
        }
        if (diff == 0) {
            // 如果位数不一致，比较多余位数
            for (int i = index; i < version1Array.length; i++) {
                if (Integer.parseInt(version1Array[i]) > 0) {
                    return 1;
                }
            }

            for (int i = index; i < version2Array.length; i++) {
                if (Integer.parseInt(version2Array[i]) > 0) {
                    return -1;
                }
            }
            return 0;
        } else {
            return diff > 0 ? 1 : -1;
        }
    }

    /**
     * 判断是否是wifi
     *
     * @return
     */
    public boolean isWifi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetInfo = connectivityManager.getActiveNetworkInfo();
        if (activeNetInfo != null && activeNetInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return true;
        }
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        String p = "";
        for (int i = 0; i < permissions.length; i++) {
            p += permissions[i] + "-";
        }
        String s = "";
        for (int i = 0; i < grantResults.length; i++) {
            s += grantResults[i] + "   ";
        }
        Log.d(TAG, "onRequestPermissionsResult: " + requestCode + "-" + p + "=" + s);
    }
}