package com.steevsapps.idledaddy.handlers.callbacks;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import in.dragonbra.javasteam.enums.EPurchaseResultDetail;
import in.dragonbra.javasteam.enums.EResult;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2;
import in.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg;
import in.dragonbra.javasteam.types.JobID;
import in.dragonbra.javasteam.types.KeyValue;

public class PurchaseResponseCallback extends CallbackMsg {
    private final EResult result;
    private final EPurchaseResultDetail purchaseResultDetails;
    private final KeyValue purchaseReceiptInfo;

    public PurchaseResponseCallback(JobID jobID, SteammessagesClientserver2.CMsgClientPurchaseResponse.Builder msg) {
        setJobID(jobID);

        result = EResult.from(msg.getEresult());
        purchaseResultDetails = EPurchaseResultDetail.from(msg.getPurchaseResultDetails());
        purchaseReceiptInfo = new KeyValue();
        try {
            purchaseReceiptInfo.tryReadAsBinary(new ByteArrayInputStream(msg.getPurchaseReceiptInfo().toByteArray()));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public EResult getResult() {
        return result;
    }

    public EPurchaseResultDetail getPurchaseResultDetails() {
        return purchaseResultDetails;
    }

    public KeyValue getPurchaseReceiptInfo() {
        return purchaseReceiptInfo;
    }
}
