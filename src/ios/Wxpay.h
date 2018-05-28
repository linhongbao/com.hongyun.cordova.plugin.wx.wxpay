#import <Cordova/CDV.h>
#import "WXApi.h"

@interface Wxpay:CDVPlugin <WXApiDelegate>

@property (nonatomic, strong) NSString *currentCallbackId;
@property (nonatomic, strong) NSString *app_id;
@property (nonatomic, strong) NSString *MCH_ID;
@property (nonatomic, strong) NSString *WX_API;
@property (nonatomic, strong) NSString *SIGN_SERVER;
@property (nonatomic, strong) NSString *BODY;
@property (nonatomic, strong) NSString *TOTAL;
@property (nonatomic, strong) NSString *ORDER;
@property (nonatomic, strong) NSString *NOTIFY;
@property (nonatomic, strong) NSDictionary *step1Dict;
@property (nonatomic, strong) NSDictionary *step3Dict;
- (void)pay:(CDVInvokedUrlCommand *)command;
@end