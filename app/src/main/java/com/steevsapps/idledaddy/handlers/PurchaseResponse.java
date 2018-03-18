package com.steevsapps.idledaddy.handlers;

import com.steevsapps.idledaddy.handlers.callbacks.PurchaseResponseCallback;

import in.dragonbra.javasteam.base.ClientMsgProtobuf;
import in.dragonbra.javasteam.base.IPacketMsg;
import in.dragonbra.javasteam.handlers.ClientMsgHandler;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2;

public class PurchaseResponse extends ClientMsgHandler {
    @Override
    public void handleMsg(IPacketMsg packetMsg) {
        if (packetMsg == null) {
            throw new IllegalArgumentException("packetMsg is null");
        }

        switch (packetMsg.getMsgType()) {
            case ClientPurchaseResponse:
                handlePurchaseResponse(packetMsg);
                break;
        }
    }

    private void handlePurchaseResponse(IPacketMsg packetMsg) {
        final ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientPurchaseResponse.Builder> purchaseResponse;
        purchaseResponse = new ClientMsgProtobuf<>(SteammessagesClientserver2.CMsgClientPurchaseResponse.class, packetMsg);
        getClient().postCallback(new PurchaseResponseCallback(purchaseResponse.getTargetJobID(), purchaseResponse.getBody()));
    }
}