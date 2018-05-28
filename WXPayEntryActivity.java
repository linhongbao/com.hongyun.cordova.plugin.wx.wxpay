package com.iyanbian.app.wxapi;

import android.content.Intent;
import android.os.Bundle;

import com.tencent.mm.opensdk.constants.ConstantsAPI;
import com.tencent.mm.opensdk.modelbase.BaseReq;
import com.tencent.mm.opensdk.modelbase.BaseResp;
import com.tencent.mm.opensdk.openapi.IWXAPIEventHandler;

import android.app.Activity;
import android.util.Log;
/**
 * Created by Administrator on 2017/8/2.
 */

import com.hongyun.cordova.plugin.wx.wxpay.Wxpay;

import org.apache.cordova.PluginResult;
import org.json.JSONObject;

public class WXPayEntryActivity extends Activity implements IWXAPIEventHandler {

    private static final String LOG_TAG = WXPayEntryActivity.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Wxpay.api.handleIntent(getIntent(), this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        Wxpay.api.handleIntent(intent, this);
    }

    @Override
    public void onReq(BaseReq req) {
        finish();
    }

    @Override
    public void onResp(BaseResp resp) {
        if (resp.getType() == ConstantsAPI.COMMAND_PAY_BY_WX) {
            JSONObject json = new JSONObject();
            try {
                if (resp.errStr != null && resp.errStr.length() >= 0) {
                    json.put("errStr", resp.errStr);
                } else {
                    json.put("errStr", "");
                }
                json.put("errCode", resp.errCode);
            } catch (Exception e) {
                Log.e(LOG_TAG, e.getMessage());
            }

            PluginResult result = null;
            if (0 == resp.errCode) {
                result = new PluginResult(PluginResult.Status.OK, json);
            } else {
                result = new PluginResult(PluginResult.Status.ERROR, json);
            }
            result.setKeepCallback(true);
            Wxpay._callbackContext.sendPluginResult(result);
        }
        finish();
    }
}
