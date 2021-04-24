package com.example.testautoupdate;

public abstract class CheckUpdateCallback<T> {

    // 有新版本
    public void onNewVersion(T t) {}

    // 没有变化
    public void onNone() {}

    public void onError(Exception e) {}
}
