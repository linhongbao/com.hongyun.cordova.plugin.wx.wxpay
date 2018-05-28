#import "Wxpay.h"
#import "XMLReader.h"
@implementation Wxpay

- (void)pluginInitialize {
}
- (void)prepareForExec:(CDVInvokedUrlCommand *)command {
    [WXApi registerApp:self.app_id];
    self.currentCallbackId = command.callbackId;
    if (![WXApi isWXAppInstalled]) {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"未安装微信"];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        [self endForExec];
        return;
    }
}

- (NSDictionary *)checkArgs:(CDVInvokedUrlCommand *)command {
    NSDictionary *params = [command.arguments objectAtIndex:0];
    if (!params) {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"参数错误"] callbackId:command.callbackId];
        [self endForExec];
        return nil;
    }
    return params;
}

-(void)endForExec {
    self.currentCallbackId = nil;
}

//预付款订单的时候,生成随机字符串
-(NSString*)getNonceStr{
    NSDate* dat = [NSDate dateWithTimeIntervalSinceNow:0];
    NSTimeInterval a=[dat timeIntervalSince1970]*1000;
    NSString *timeString = [NSString stringWithFormat:@"%f", a];
    return timeString;
}
//调用微信支付api的时候需要
-(NSString*)getTimeStamp{
    
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.dateFormat = @"YYYY-MM-dd HH:mm:ss";
    NSDate *date = [[NSDate alloc] init];
    NSInteger numtime = date.timeIntervalSince1970;
    NSString *numtimestr = [NSString stringWithFormat:@"%ld",numtime];
    return numtimestr;
}
//第一步骤 产生提交预订单签名
-(NSString *)getServerSign{

    self.step1Dict = [NSMutableDictionary dictionary];

    [self.step1Dict setValue:self.app_id forKey:@"appid"];
    [self.step1Dict setValue:self.BODY   forKey:@"body"];
    [self.step1Dict setValue:self.MCH_ID forKey:@"mch_id"];
    [self.step1Dict setValue:[self getNonceStr] forKey:@"nonce_str"];
    [self.step1Dict setValue:self.NOTIFY forKey:@"notify_url"];
    [self.step1Dict setValue:self.ORDER  forKey:@"out_trade_no"];
    [self.step1Dict setValue:self.TOTAL  forKey:@"total_fee"];
    [self.step1Dict setValue:@"APP"  forKey:@"trade_type"];

    NSString * ServerSign = [self postxml:self.SIGN_SERVER postbd:self.step1Dict];
    return ServerSign;
}
//第二步骤 提交到预订单到微信
-(NSString*)getPrepareOrder:(NSString*)serverSign{
    
    [self.step1Dict setValue:serverSign forKey:@"sign"];
    NSString * result =[self postxml:self.WX_API postbd:self.step1Dict];
    NSError * error  =nil;
    NSDictionary *tmp;
    NSDictionary *dic;
    tmp = [XMLReader dictionaryForXMLString:result error:&error];
    
    NSDictionary * return_code;
    NSDictionary * prepare_order;
    dic = [tmp objectForKey:@"xml"];
    return_code = [dic objectForKey:@"return_code"];
    
    if([[return_code valueForKey:@"text"] isEqual:@"SUCCESS"]){
        prepare_order = [dic objectForKey:@"prepay_id"];
    }
    
    NSString * prepare_ordert;
    
    if(prepare_order == nil){
         [self errorAndExit:[[dic objectForKey:@"err_code_des"] valueForKey:@"text"]];
    }else{
         prepare_ordert =[prepare_order valueForKey:@"text"];
    }
    return prepare_ordert;
}
//第三步骤对预订单的签名
-(NSString*)getPrepareOrderSign:(NSString*)prepay_id{
    NSString * sign;
    
    //这个值必须是这个
    NSString * package = [NSString stringWithFormat:@"Sign=WXpay"];
    
    self.step3Dict = [NSMutableDictionary dictionary];
    [self.step3Dict setValue:self.app_id          forKey:@"appid"];
    [self.step3Dict setValue:[self getNonceStr]   forKey:@"noncestr"];
    [self.step3Dict setValue:package              forKey:@"package"];
    [self.step3Dict setValue:self.MCH_ID          forKey:@"partnerid"];
    [self.step3Dict setValue:prepay_id            forKey:@"prepayid"];
    [self.step3Dict setValue:[self getTimeStamp]  forKey:@"timestamp"];
    
    sign = [self postxml:self.SIGN_SERVER postbd:self.step3Dict];
    return sign;
}

//第四步骤吊起支付api
-(void)callWXPay:(NSString*)prepaySign{
    PayReq *req   = [[PayReq alloc] init];
    req.partnerId = [self.step3Dict objectForKey:@"partnerid"];
    req.prepayId  = [self.step3Dict objectForKey:@"prepayid"];
    req.nonceStr  = [self.step3Dict objectForKey:@"noncestr"];
    req.timeStamp = [[self.step3Dict objectForKey:@"timestamp"]intValue];
    req.package   = [self.step3Dict objectForKey:@"package"];
    req.sign      = prepaySign;
    [WXApi sendReq:req];
}


-(void)payThread:(CDVInvokedUrlCommand *)command {
    NSDictionary *params = [self checkArgs:command];
    if (params == nil) {
        return;
    }
    self.app_id = [params objectForKey:@"APP_ID"];
    self.MCH_ID = [params objectForKey:@"MCH_ID"];
    self.WX_API = [params objectForKey:@"WX_API"];
    self.SIGN_SERVER = [params objectForKey:@"SIGN_SERVER"];
    self.BODY   = [params objectForKey:@"BODY"];
    self.TOTAL  = [params objectForKey:@"TOTAL"];
    self.ORDER  = [params objectForKey:@"ORDER"];
    self.NOTIFY = [params objectForKey:@"NOTIFY"];
    
    
    [self prepareForExec:command];
    
    //第一步骤，将提交过来的参数排序后提交给签名服务器
    NSString * serverSign = [self getServerSign];
    if([serverSign isEqual:@""]){
        [self errorAndExit:@"获取预付订单签名失败"];
    }else{
        //第二步骤提交预付款订单
        NSString * prepay_id = [self getPrepareOrder:serverSign];
        
        if([prepay_id isEqual:@""] || prepay_id == nil){
              [self errorAndExit:@"获取预付订单id失败"];
        }else{
            //第三步骤再次签名
            NSString * preSign = [self getPrepareOrderSign:prepay_id];
            if([preSign isEqual:@""]){
                 [self errorAndExit:@"获取API签名失败"];
            }else{
                //第四步骤
                [self callWXPay:preSign];
            }

        }
    }
    
}
- (void)pay:(CDVInvokedUrlCommand *)command {
    //cordova 主调入口
    [self.commandDelegate runInBackground:^{
        [self payThread:command];
    }];
    
}

-(void)errorAndExit:(NSString*)error{
    CDVPluginResult *result = nil;
    result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:error];
    [self.commandDelegate sendPluginResult:result callbackId:[self currentCallbackId]];
    [self endForExec];
}
-(void)onResp:(BaseResp *)resp {
    if ([resp isKindOfClass:[PayResp class]]) {
        CDVPluginResult *result = nil;
        PayResp *response = (PayResp *)resp;
        NSDictionary * dic =  [NSMutableDictionary dictionary];
        switch (response.errCode) {
            case WXSuccess:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
                break;
            default:
                [dic setValue:[NSString stringWithFormat:@"%d",response.errCode]forKey:@"errCode"];
                [dic setValue:response.errStr forKey:@"errStr"];
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsDictionary:dic];
                break;
        }
        [self.commandDelegate sendPluginResult:result callbackId:[self currentCallbackId]];
    }
    [self endForExec];
}
//这个函数是支付回调
-(void)handleOpenURL:(NSNotification *)notification {
    NSURL *url = [notification object];
    if ([url isKindOfClass:[NSURL class]] && [url.scheme isEqualToString:self.app_id]) {
        [WXApi handleOpenURL:url delegate:self];
        //之后走的是onResp
    }
}
-(NSString *) postxml:(NSString*)URLstr postbd:(NSDictionary*)postbd
{
    
    NSMutableURLRequest *request = [[NSMutableURLRequest alloc] init];
    [request setURL:[NSURL URLWithString:URLstr]];
    [request setHTTPMethod:@"POST"];//声明请求为POST请求
    //set headers
    NSString *contentType = [NSString stringWithFormat:@"text/xml"];//Content-Type数据类型设置xml类型
    [request addValue:contentType forHTTPHeaderField: @"Content-Type"];
    //create the body
    NSMutableData *postBody = [NSMutableData data];
    
    //这个提交的参数也是必须要按照ascii排序的
    NSMutableArray *numArray = [NSMutableArray arrayWithArray:postbd.allKeys];
    [numArray sortUsingComparator:^NSComparisonResult (NSString *str1, NSString *str2){
        return [str1 compare:str2];
    }];
    
    
    
    [postBody appendData:[[NSString stringWithFormat:@"<xml>"] dataUsingEncoding:NSUTF8StringEncoding]];
    for (NSString * key in numArray) {
        [postBody appendData:[[NSString stringWithFormat:@"<%@>%@</%@>",key,postbd[key],key] dataUsingEncoding:NSUTF8StringEncoding]];
    }
    [postBody appendData:[[NSString stringWithFormat:@"</xml>"] dataUsingEncoding:NSUTF8StringEncoding]];
    
    
    [request setHTTPBody:postBody];
    
    NSString *bodyStr = [[NSString alloc] initWithData:postBody  encoding:NSUTF8StringEncoding];
    NSLog(@"bodyStr: %@ ",bodyStr);
    
    //get response
    NSHTTPURLResponse* urlResponse = nil;
    NSError *error = [[NSError alloc] init];
    NSData *responseData = [NSURLConnection sendSynchronousRequest:request returningResponse:&urlResponse error:&error];
    NSString *result = [[NSString alloc] initWithData:responseData encoding:NSUTF8StringEncoding];
    NSLog(@"Response Code: %ld", (long)[urlResponse statusCode]);
    if ([urlResponse statusCode] >= 200 && [urlResponse statusCode] < 300) {
        NSLog(@"Response: %@", result);
        return result;
    }else{
        return @"";
    }
}
@end
