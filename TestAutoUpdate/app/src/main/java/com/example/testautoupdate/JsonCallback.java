package com.example.testautoupdate;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.zhy.http.okhttp.callback.Callback;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import okhttp3.Response;

public abstract class JsonCallback<T> extends Callback<T>  {
    @Override
    public T parseNetworkResponse(Response response, int id) throws Exception {
        Type genType = getClass().getGenericSuperclass();
        Type t = ((ParameterizedType)genType).getActualTypeArguments()[0];
        String str = response.body().string();
        T result = JSON.parseObject(str, t);
        return result;
    }
}
