package com.steevsapps.idledaddy.handlers;

import uk.co.thomasc.steamkit.base.ClientMsgProtobuf;
import uk.co.thomasc.steamkit.base.IPacketMsg;
import uk.co.thomasc.steamkit.base.generated.SteammessagesClientserver2;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EMsg;
import uk.co.thomasc.steamkit.steam3.handlers.ClientMsgHandler;

/**
 * TODO: Move this to SteamKit
 */
public class FreeLicense extends ClientMsgHandler {
    private int gameId;

    @Override
    public void handleMsg(IPacketMsg packetMsg) {
        switch (packetMsg.getMsgType()) {
            case ClientRequestFreeLicenseResponse:
                handleFreeLicense(packetMsg);
                break;
        }
    }

    private void handleFreeLicense(IPacketMsg packetMsg) {
        synchronized (this) {
            final ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientRequestFreeLicenseResponse> grantedLicenses;
            grantedLicenses = new ClientMsgProtobuf<>(SteammessagesClientserver2.CMsgClientRequestFreeLicenseResponse.class, packetMsg);
            final FreeLicenseCallback callback = new FreeLicenseCallback(grantedLicenses.getTargetJobID(), grantedLicenses.getBody(), gameId);
            getClient().postCallback(callback);
        }
    }

    public void requestFreeLicense(int appId) {
        synchronized (this) {
            gameId = appId;
            final ClientMsgProtobuf<SteammessagesClientserver2.CMsgClientRequestFreeLicense> request;
            request = new ClientMsgProtobuf<>(SteammessagesClientserver2.CMsgClientRequestFreeLicense.class,
                    EMsg.ClientRequestFreeLicense);
            request.setSourceJobID(getClient().getNextJobID());
            request.getBody().appids = new int[]{appId};
            getClient().send(request);
        }
    }
}
