package com.hongyun.cordova.plugin.wx.wxpay;

import android.content.Context;
import android.util.Log;
import android.util.Xml;

import com.tencent.mm.opensdk.modelpay.PayReq;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONException;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Created by Administrator on 2017/8/1.
 */

public class WxpayOld extends CordovaPlugin {

    private String TAG = WxpayOld.class.getName();

    //这个平台在微信开发者平台查看
    public static  String APP_ID ="";
    //商户号 微信分配的公众账号ID
    private  String MCH_ID;
    //API密钥，在商户平台设置
    private  String API_KEY;
    //微信支付步骤1下单先提交到微信后台
    private  String WX_API;
    //商品描述
    private  String BODY;
    //商品价格
    private  String TOTAL;
    //订单id
    private String ORDER;
    //回调地址
    private String NOTIFY;

    private Context m_context;

    public static  IWXAPI api = null;

    public static  CallbackContext _callbackContext = null;

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        m_context = this.cordova.getActivity().getApplicationContext();
    }


    @Override
    public boolean execute(String action, CordovaArgs args, CallbackContext callbackContext) throws JSONException {
        //这个回调,只是代表择步骤1成功,开始调用步骤2
        //步骤2成功才是真正的购买成功
        _callbackContext = callbackContext;
        JSONObject jo = args.getJSONObject(0);

        //不在初始函数中注册,因为appID,没有传递过来
        //应该写个注册,但是太懒了
        APP_ID  = jo.getString("APP_ID");
        MCH_ID  = jo.getString("MCH_ID");
        API_KEY = jo.getString("API_KEY");
        WX_API  = jo.getString("WX_API");


        if(api == null) {
            api = WXAPIFactory.createWXAPI(this.cordova.getActivity(), APP_ID);
        }
        if (action.equals("pay")) {
            BODY    = jo.getString("BODY");
            TOTAL   = jo.getString("TOTAL");
            ORDER   = jo.getString("ORDER");
            NOTIFY  = jo.getString("NOTIFY");
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    runThread();
                }
            });
        }
        return true;
    }

    private void runThread(){
        //步骤1,给微信后台提交
        String entity = getProductArgs();
        byte[] buf=Util.httpPost(WX_API, entity);

        //步骤2,调经回调结果
        String content = new String(buf);
        Map<String,String> xml =decodeXml(content);
        String value = xml.get("return_code");
        //通信标识码
        if(!value.equals("SUCCESS")) {
            _callbackContext.error(xml.get("return_msg"));
            return;
        }
        value = "";
        value = xml.get("prepay_id");
        if(value == null || value.equals("")){
            _callbackContext.error(xml.toString());
            return;
        }
        PayReq payReq = genPayReq(xml);
        sendPayReq(payReq);
    }
    private void sendPayReq(PayReq req) {
        //看来可以每次注册
        api.registerApp(APP_ID);
        api.sendReq(req);
    }

    private long genTimeStamp() {
        return System.currentTimeMillis() / 1000;
    }

    private PayReq genPayReq(Map<String, String> result) {

        PayReq req = new PayReq();
        req.appId = APP_ID;
        req.partnerId = MCH_ID;
        req.prepayId = result.get("prepay_id");
        req.packageValue = "prepay_id=" + result.get("prepay_id");
        req.nonceStr = getNonceStr();
        req.timeStamp = String.valueOf(genTimeStamp());

        Map<String, String> keyValues = new HashMap<String, String>();
        keyValues.put("appid",req.appId);
        keyValues.put("noncestr",req.nonceStr);
        keyValues.put("package",req.packageValue);
        keyValues.put("partnerid",req.partnerId);
        keyValues.put("prepayid",req.prepayId);
        keyValues.put("timestamp",req.timeStamp);

        req.sign = getPackageSign(keyValues);
        return req;
    }

    public Map<String, String> decodeXml(String content) {

        try {
            Map<String, String> xml = new HashMap<String, String>();
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(content));
            int event = parser.getEventType();
            while (event != XmlPullParser.END_DOCUMENT) {

                String nodeName = parser.getName();
                switch (event) {
                    case XmlPullParser.START_DOCUMENT:

                        break;
                    case XmlPullParser.START_TAG:

                        if ("xml".equals(nodeName) == false) {
                            //实例化student对象
                            xml.put(nodeName, parser.nextText());
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                }
                event = parser.next();
            }

            return xml;
        } catch (Exception e) {
            Log.e(TAG, "decodeXml" + e.toString());
        }
        return null;

    }

    private String getProductArgs() {

        StringBuffer xml = new StringBuffer();
        String xmlString = "";
        Map<String, String> keyValues = new HashMap<String, String>();

        try {
            xml.append("<xml>");
            //应用ID
            keyValues.put("appid",APP_ID);
            //商品描述
            keyValues.put("body", BODY);
            //商户号
            keyValues.put("mch_id", MCH_ID);
            //随机字符串
            keyValues.put("nonce_str", getNonceStr());
            //设置回调地址
            keyValues.put("notify_url", NOTIFY);
            //商户订单号
            keyValues.put("out_trade_no",ORDER);
            //总金额
            keyValues.put("total_fee", TOTAL);
            //交易类型
            keyValues.put("trade_type", "APP");
            //签名
            String sign = getPackageSign(keyValues);

            keyValues.put("sign", sign);

            xmlString = toXml(keyValues);

        } catch (Exception e) {
            Log.e("Exception", "getProductArgs: " + e.getMessage());
        }

        return xmlString;


    }

    //测试用的订单编号
    public String getOrderNum() {
        Calendar c = Calendar.getInstance();
        String date = "" +
                +c.get(Calendar.YEAR)
                + c.get(Calendar.MONTH)
                + c.get(Calendar.DAY_OF_MONTH)
                + c.get(Calendar.HOUR_OF_DAY)
                + c.get(Calendar.MINUTE)
                + c.get(Calendar.SECOND)
                + c.get(Calendar.MILLISECOND);

        return date.toString();
    }

    //生成随机号，防重发
    private String getNonceStr() {
        // TODO Auto-generated method stub
        Random random = new Random();
        return getMessageDigest(String.valueOf(random.nextInt(10000)).getBytes());
    }

    private String buildKeyValue(String key, String value, boolean isEncode) {
        StringBuilder sb = new StringBuilder();
        sb.append(key);
        sb.append("=");
        if (isEncode) {
            try {
                sb.append(URLEncoder.encode(value, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                sb.append(value);
            }
        } else {
            sb.append(value);
        }
        return sb.toString();
    }

    private String getPackageSign(Map<String, String> map) {
        List<String> keys = new ArrayList<String>(map.keySet());
        // key排序
        Collections.sort(keys);

        StringBuilder authInfo = new StringBuilder();
        for (int i = 0; i < keys.size() - 1; i++) {
            String key = keys.get(i);
            String value = map.get(key);
            authInfo.append(buildKeyValue(key, value, false));
            authInfo.append("&");
        }

        String tailKey = keys.get(keys.size() - 1);
        String tailValue = map.get(tailKey);
        authInfo.append(buildKeyValue(tailKey, tailValue, false));
        authInfo.append("&");
        authInfo.append(buildKeyValue("key", API_KEY, false));

        String packageSign = getMessageDigest(authInfo.toString().getBytes()).toUpperCase();

        return packageSign;
    }

    private String toXml(Map<String, String> map) {
        StringBuilder sb = new StringBuilder();
        List<String> keys = new ArrayList<String>(map.keySet());
        Collections.sort(keys);
        sb.append("<xml>");
        String key = "";
        String value = "";
        for (int i = 0; i < keys.size(); i++) {
            key = keys.get(i);
            value = map.get(key);
            sb.append("<" + key + ">");
            sb.append(value);
            sb.append("</" + key + ">");
        }
        sb.append("</xml>");

        Log.e("Simon", ">>>>" + sb.toString());
        String result = sb.toString();
        return result;
    }

    private String getMessageDigest(byte[] buffer) {
        char hexDigits[] = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
        try {
            MessageDigest mdTemp = MessageDigest.getInstance("MD5");
            mdTemp.update(buffer);
            byte[] md = mdTemp.digest();
            int j = md.length;
            char str[] = new char[j * 2];
            int k = 0;
            for (int i = 0; i < j; i++) {
                byte byte0 = md[i];
                str[k++] = hexDigits[byte0 >>> 4 & 0xf];
                str[k++] = hexDigits[byte0 & 0xf];
            }
            return new String(str);
        } catch (Exception e) {
            return null;
        }
    }
}
