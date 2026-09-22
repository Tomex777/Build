package com.tomex.securerenderlab;

import android.os.ParcelFileDescriptor;

interface ISecureCaptureService {
    ParcelFileDescriptor captureSecureDisplay();
    String getLastStatus();
    int getServiceUid();
}
