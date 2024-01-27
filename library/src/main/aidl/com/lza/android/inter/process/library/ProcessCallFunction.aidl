package com.lza.android.inter.process.library;

import com.lza.android.inter.process.library.bridge.parameter.BridgeParameter;

interface ProcessCallFunction {

    void invoke(inout BridgeParameter parameter);
}