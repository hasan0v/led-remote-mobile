package az.kickdl.app.screens;

import android.content.Context;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

import az.kickdl.app.R;
import az.kickdl.app.net.CfBridge;
import az.kickdl.app.net.Http;
import az.kickdl.app.net.KickApi;

final class Errors {
    private Errors() {}

    static boolean isChallenge(Exception e) { return e instanceof CfBridge.ChallengeException; }

    static String message(Context c, Exception e) {
        if (e instanceof CfBridge.ChallengeException) return c.getString(R.string.err_challenge);
        if (e instanceof KickApi.NotFoundException) return c.getString(R.string.err_not_found);
        if (e instanceof UnknownHostException || e instanceof SocketTimeoutException || e instanceof java.net.ConnectException)
            return c.getString(R.string.err_network);
        if (e instanceof Http.HttpException) {
            int code = ((Http.HttpException) e).code;
            if (code == 429) return c.getString(R.string.err_rate);
            if (code >= 500) return c.getString(R.string.err_server);
            if (code == 403) return c.getString(R.string.err_forbidden);
        }
        String m = e.getMessage();
        if (m != null && m.toLowerCase().contains("timeout")) return c.getString(R.string.err_network);
        return m == null ? c.getString(R.string.err_unknown) : m;
    }
}
