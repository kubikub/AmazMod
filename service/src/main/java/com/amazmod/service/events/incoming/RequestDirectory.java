package com.amazmod.service.events.incoming;

import com.huami.watch.transport.DataBundle;

public class RequestDirectory {

    private DataBundle dataBundle;

    public RequestDirectory(DataBundle dataBundle) {
        this.dataBundle = dataBundle;
    }

    public DataBundle getDataBundle() {
        return dataBundle;
    }
}
