package com.taehoon.cameraproject;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

public class MainViewModel extends AndroidViewModel {

    public MainViewModel(@NonNull Application application) {
        super(application);
    }

    public MutableLiveData<String> deviceInfo;

    public void initViewModel() {
        deviceInfo = new MutableLiveData<>();
    }
}
