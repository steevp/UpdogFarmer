package com.steevsapps.idledaddy.handlers;

import uk.co.thomasc.steamkit.base.generated.SteammessagesClientserver2;
import uk.co.thomasc.steamkit.base.generated.steamlanguage.EResult;
import uk.co.thomasc.steamkit.steam3.steamclient.callbackmgr.CallbackMsg;
import uk.co.thomasc.steamkit.types.JobID;

public class FreeLicenseCallback extends CallbackMsg {
    private JobID jobId;
    private EResult result;
    private int[] grantedApps;
    private int[] grantedPackages;

    FreeLicenseCallback(JobID jobID, SteammessagesClientserver2.CMsgClientRequestFreeLicenseResponse msg) {
        this.jobId = jobID;
        result = EResult.f(msg.eresult);
        grantedApps = msg.grantedAppids;
        grantedPackages = msg.grantedPackageids;
    }

    public JobID getJobId() {
        return jobId;
    }

    public EResult getResult() {
        return result;
    }

    public int[] getGrantedApps() {
        return grantedApps;
    }

    public int[] getGrantedPackages() {
        return grantedPackages;
    }
}
