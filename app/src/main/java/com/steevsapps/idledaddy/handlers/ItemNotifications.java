package com.steevsapps.idledaddy.handlers;

import com.steevsapps.idledaddy.handlers.callbacks.ItemNotificationsCallback;

import in.dragonbra.javasteam.base.ClientMsgProtobuf;
import in.dragonbra.javasteam.base.IPacketMsg;
import in.dragonbra.javasteam.enums.EMsg;
import in.dragonbra.javasteam.handlers.ClientMsgHandler;
import in.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2;

/**
 * This handler lets Idle Daddy listen for new item notifications (so it can detect a card drop)
 */
public class ItemNotifications extends ClientMsgHandler {

    @Override
    public void handleMsg(IPacketMsg packetMsg) {
        if (packetMsg == null) {
            throw new IllegalArgumentException("packetMsg is null");
        }

        switch (packetMsg.getMsgType()) {
            case ClientItemAnnouncements:
                handleItemNotification(packetMsg);
                break;
        }
    }

    private void handleItemNotification(IPacketMsg packetMsg) {
        final ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientItemAnnouncements.Builder> itemNotification;
        itemNotification = new ClientMsgProtobuf<>(SteammessagesClientserver2.CMsgClientItemAnnouncements.class, packetMsg);
        getClient().postCallback(new ItemNotificationsCallback(itemNotification.getTargetJobID(), itemNotification.getBody()));
    }

    public void requestItemNotifications() {
        final ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientRequestItemAnnouncements.Builder> request;
        request = new ClientMsgProtobuf<>(SteammessagesClientserver2.CMsgClientRequestItemAnnouncements.class, EMsg.ClientRequestItemAnnouncements);
        getClient().send(request);
    }
}
