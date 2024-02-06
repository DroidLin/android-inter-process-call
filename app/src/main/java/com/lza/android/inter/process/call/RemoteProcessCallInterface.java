package com.lza.android.inter.process.call;

import androidx.annotation.Nullable;

import com.lza.android.inter.process.annotation.RemoteProcessInterface;

/**
 * @author liuzhongao
 * @since 2024/1/17 16:56
 */
@RemoteProcessInterface
public interface RemoteProcessCallInterface {

    long getCurrentTimeStamp();

    @Nullable
    String getString();

    default void startService() {}
}
