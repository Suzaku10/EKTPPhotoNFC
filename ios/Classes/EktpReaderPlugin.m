#import "EktpReaderPlugin.h"
#if __has_include(<ektp_reader/ektp_reader-Swift.h>)
#import <ektp_reader/ektp_reader-Swift.h>
#else
// Support project import fallback if the generated compatibility header
// is not copied when this plugin is created as a library.
// https://forums.swift.org/t/swift-static-libraries-dont-copy-generated-objective-c-header/19816
#import "ektp_reader-Swift.h"
#endif

@implementation EktpReaderPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftEktpReaderPlugin registerWithRegistrar:registrar];
}
@end
