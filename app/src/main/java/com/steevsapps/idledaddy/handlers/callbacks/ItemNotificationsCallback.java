package com.steevsapps.idledaddy.handlers.callbacks;

import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg;
import in.dragonbra.javasteam.types.JobID;

public class ItemNotificationsCallback extends CallbackMsg {
    private final int count;

    public ItemNotificationsCallback(JobID jobID, SteammessagesClientserver2.CMsgClientItemAnnouncements.Builder msg) {
        setJobID(jobID);
        count = msg.getCountNewItems();
    }

    public int getCount() {
        return count;
    }
}