package com.lza.android.inter.process.call;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

/**
 * @author liuzhongao
 * @since 2024/1/17 16:56
 */
@Keep
public interface RemoteProcessCallInterface {

    long getCurrentTimeStamp();

    @Nullable
    String getString();
}
