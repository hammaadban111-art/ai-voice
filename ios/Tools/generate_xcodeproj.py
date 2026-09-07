#!/usr/bin/env python3
"""Writes VoiceAppV4.xcodeproj/project.pbxproj.

The project file is generated rather than hand-maintained so adding a Swift file
is `git add` plus a re-run, not a merge conflict in a 900-line plist. Run it
from anywhere:

    python3 ios/Tools/generate_xcodeproj.py

It produces the same two targets XcodeGen would build from project.yml — the app
and the keyboard extension, with Shared/ compiled into both — so either tool can
regenerate the project.
"""
import hashlib
from pathlib import Path

IOS_ROOT = Path(__file__).resolve().parents[1]
PROJECT_DIR = IOS_ROOT / "VoiceAppV4.xcodeproj"

APP_NAME = "VoiceAppV4"
APP_PRODUCT = "Voice App V4.app"
EXT_NAME = "VoiceKeyboard"
EXT_PRODUCT = "VoiceKeyboard.appex"
APP_BUNDLE_ID = "com.hammaad.voiceappv4"
EXT_BUNDLE_ID = "com.hammaad.voiceappv4.keyboard"
MARKETING_VERSION = "4.1.3"
DEPLOYMENT_TARGET = "17.0"


def uid(*parts):
    """A stable 24-hex-character object id derived from what it names."""
    digest = hashlib.md5("::".join(parts).encode()).hexdigest()
    return digest[:24].upper()


def swift_files(folder):
    return sorted(p.name for p in (IOS_ROOT / folder).glob("*.swift"))


def main():
    shared = swift_files("Shared")
    app = swift_files("App")
    ext = swift_files("Keyboard")

    lines = []
    add = lines.append

    add("// !$*UTF8*$!")
    add("{")
    add("\tarchiveVersion = 1;")
    add("\tclasses = {")
    add("\t};")
    add("\tobjectVersion = 56;")
    add("\tobjects = {")

    # -- PBXBuildFile ------------------------------------------------------
    add("")
    add("/* Begin PBXBuildFile section */")

    def build_file(target, folder, name):
        file_id = uid("file", folder, name)
        build_id = uid("build", target, folder, name)
        add(f"\t\t{build_id} /* {name} in Sources */ = {{isa = PBXBuildFile; "
            f"fileRef = {file_id} /* {name} */; }};")
        return build_id

    app_sources = [build_file(APP_NAME, "App", n) for n in app]
    app_sources += [build_file(APP_NAME, "Shared", n) for n in shared]
    ext_sources = [build_file(EXT_NAME, "Keyboard", n) for n in ext]
    ext_sources += [build_file(EXT_NAME, "Shared", n) for n in shared]

    assets_build = uid("build", APP_NAME, "App", "Assets.xcassets")
    add(f"\t\t{assets_build} /* Assets.xcassets in Resources */ = {{isa = PBXBuildFile; "
        f"fileRef = {uid('file', 'App', 'Assets.xcassets')} /* Assets.xcassets */; }};")

    embed_build = uid("build", "embed", EXT_PRODUCT)
    add(f"\t\t{embed_build} /* {EXT_PRODUCT} in Embed Foundation Extensions */ = {{isa = PBXBuildFile; "
        f"fileRef = {uid('product', EXT_PRODUCT)} /* {EXT_PRODUCT} */; "
        "settings = {ATTRIBUTES = (RemoveHeadersOnCopy, ); }; };")
    add("/* End PBXBuildFile section */")

    # -- PBXCopyFilesBuildPhase --------------------------------------------
    embed_phase = uid("phase", "embed", APP_NAME)
    add("")
    add("/* Begin PBXCopyFilesBuildPhase section */")
    add(f"\t\t{embed_phase} /* Embed Foundation Extensions */ = {{")
    add("\t\t\tisa = PBXCopyFilesBuildPhase;")
    add("\t\t\tbuildActionMask = 2147483647;")
    add("\t\t\tdstPath = \"\";")
    add("\t\t\tdstSubfolderSpec = 13;")
    add("\t\t\tfiles = (")
    add(f"\t\t\t\t{embed_build} /* {EXT_PRODUCT} in Embed Foundation Extensions */,")
    add("\t\t\t);")
    add("\t\t\tname = \"Embed Foundation Extensions\";")
    add("\t\t\trunOnlyForDeploymentPostprocessing = 0;")
    add("\t\t};")
    add("/* End PBXCopyFilesBuildPhase section */")

    # -- PBXFileReference --------------------------------------------------
    add("")
    add("/* Begin PBXFileReference section */")

    def file_ref(folder, name, file_type="sourcecode.swift"):
        ref = uid("file", folder, name)
        add(f"\t\t{ref} /* {name} */ = {{isa = PBXFileReference; lastKnownFileType = {file_type}; "
            f"path = {quote(name)}; sourceTree = \"<group>\"; }};")
        return ref

    for name in app:
        file_ref("App", name)
    for name in shared:
        file_ref("Shared", name)
    for name in ext:
        file_ref("Keyboard", name)

    file_ref("App", "Assets.xcassets", "folder.assetcatalog")
    file_ref("App", "Info.plist", "text.plist.xml")
    file_ref("App", "VoiceAppV4.entitlements", "text.plist.entitlements")
    file_ref("Keyboard", "Info.plist", "text.plist.xml")
    file_ref("Keyboard", "VoiceKeyboard.entitlements", "text.plist.entitlements")

    app_product = uid("product", APP_PRODUCT)
    ext_product = uid("product", EXT_PRODUCT)
    add(f"\t\t{app_product} /* {APP_PRODUCT} */ = {{isa = PBXFileReference; explicitFileType = "
        f"wrapper.application; includeInIndex = 0; path = {quote(APP_PRODUCT)}; "
        "sourceTree = BUILT_PRODUCTS_DIR; };")
    add(f"\t\t{ext_product} /* {EXT_PRODUCT} */ = {{isa = PBXFileReference; explicitFileType = "
        f"\"wrapper.app-extension\"; includeInIndex = 0; path = {quote(EXT_PRODUCT)}; "
        "sourceTree = BUILT_PRODUCTS_DIR; };")
    add("/* End PBXFileReference section */")

    # -- PBXFrameworksBuildPhase -------------------------------------------
    app_frameworks = uid("phase", "frameworks", APP_NAME)
    ext_frameworks = uid("phase", "frameworks", EXT_NAME)
    add("")
    add("/* Begin PBXFrameworksBuildPhase section */")
    for phase, target in ((app_frameworks, APP_NAME), (ext_frameworks, EXT_NAME)):
        add(f"\t\t{phase} /* Frameworks */ = {{")
        add("\t\t\tisa = PBXFrameworksBuildPhase;")
        add("\t\t\tbuildActionMask = 2147483647;")
        add("\t\t\tfiles = (")
        add("\t\t\t);")
        add("\t\t\trunOnlyForDeploymentPostprocessing = 0;")
        add("\t\t};")
    add("/* End PBXFrameworksBuildPhase section */")

    # -- PBXGroup ----------------------------------------------------------
    main_group = uid("group", "main")
    products_group = uid("group", "Products")
    app_group = uid("group", "App")
    shared_group = uid("group", "Shared")
    ext_group = uid("group", "Keyboard")

    add("")
    add("/* Begin PBXGroup section */")

    add(f"\t\t{main_group} = {{")
    add("\t\t\tisa = PBXGroup;")
    add("\t\t\tchildren = (")
    add(f"\t\t\t\t{app_group} /* App */,")
    add(f"\t\t\t\t{ext_group} /* Keyboard */,")
    add(f"\t\t\t\t{shared_group} /* Shared */,")
    add(f"\t\t\t\t{products_group} /* Products */,")
    add("\t\t\t);")
    add("\t\t\tsourceTree = \"<group>\";")
    add("\t\t};")

    def group(ref, name, path, children):
        add(f"\t\t{ref} /* {name} */ = {{")
        add("\t\t\tisa = PBXGroup;")
        add("\t\t\tchildren = (")
        for child_folder, child_name in children:
            add(f"\t\t\t\t{uid('file', child_folder, child_name)} /* {child_name} */,")
        add("\t\t\t);")
        if path is not None:
            add(f"\t\t\tpath = {quote(path)};")
        else:
            add(f"\t\t\tname = {quote(name)};")
        add("\t\t\tsourceTree = \"<group>\";")
        add("\t\t};")

    group(app_group, "App", "App",
          [("App", n) for n in app]
          + [("App", "Assets.xcassets"), ("App", "Info.plist"), ("App", "VoiceAppV4.entitlements")])
    group(ext_group, "Keyboard", "Keyboard",
          [("Keyboard", n) for n in ext]
          + [("Keyboard", "Info.plist"), ("Keyboard", "VoiceKeyboard.entitlements")])
    group(shared_group, "Shared", "Shared", [("Shared", n) for n in shared])

    add(f"\t\t{products_group} /* Products */ = {{")
    add("\t\t\tisa = PBXGroup;")
    add("\t\t\tchildren = (")
    add(f"\t\t\t\t{app_product} /* {APP_PRODUCT} */,")
    add(f"\t\t\t\t{ext_product} /* {EXT_PRODUCT} */,")
    add("\t\t\t);")
    add("\t\t\tname = Products;")
    add("\t\t\tsourceTree = \"<group>\";")
    add("\t\t};")
    add("/* End PBXGroup section */")

    # -- PBXNativeTarget ---------------------------------------------------
    app_target = uid("target", APP_NAME)
    ext_target = uid("target", EXT_NAME)
    app_sources_phase = uid("phase", "sources", APP_NAME)
    ext_sources_phase = uid("phase", "sources", EXT_NAME)
    app_resources_phase = uid("phase", "resources", APP_NAME)
    ext_resources_phase = uid("phase", "resources", EXT_NAME)
    app_config_list = uid("configlist", APP_NAME)
    ext_config_list = uid("configlist", EXT_NAME)
    dependency = uid("dependency", EXT_NAME)

    add("")
    add("/* Begin PBXNativeTarget section */")

    add(f"\t\t{app_target} /* {APP_NAME} */ = {{")
    add("\t\t\tisa = PBXNativeTarget;")
    add(f"\t\t\tbuildConfigurationList = {app_config_list} /* Build configuration list for PBXNativeTarget \"{APP_NAME}\" */;")
    add("\t\t\tbuildPhases = (")
    add(f"\t\t\t\t{app_sources_phase} /* Sources */,")
    add(f"\t\t\t\t{app_frameworks} /* Frameworks */,")
    add(f"\t\t\t\t{app_resources_phase} /* Resources */,")
    add(f"\t\t\t\t{embed_phase} /* Embed Foundation Extensions */,")
    add("\t\t\t);")
    add("\t\t\tbuildRules = (")
    add("\t\t\t);")
    add("\t\t\tdependencies = (")
    add(f"\t\t\t\t{dependency} /* PBXTargetDependency */,")
    add("\t\t\t);")
    add(f"\t\t\tname = {APP_NAME};")
    add(f"\t\t\tproductName = {APP_NAME};")
    add(f"\t\t\tproductReference = {app_product} /* {APP_PRODUCT} */;")
    add("\t\t\tproductType = \"com.apple.product-type.application\";")
    add("\t\t};")

    add(f"\t\t{ext_target} /* {EXT_NAME} */ = {{")
    add("\t\t\tisa = PBXNativeTarget;")
    add(f"\t\t\tbuildConfigurationList = {ext_config_list} /* Build configuration list for PBXNativeTarget \"{EXT_NAME}\" */;")
    add("\t\t\tbuildPhases = (")
    add(f"\t\t\t\t{ext_sources_phase} /* Sources */,")
    add(f"\t\t\t\t{ext_frameworks} /* Frameworks */,")
    add(f"\t\t\t\t{ext_resources_phase} /* Resources */,")
    add("\t\t\t);")
    add("\t\t\tbuildRules = (")
    add("\t\t\t);")
    add("\t\t\tdependencies = (")
    add("\t\t\t);")
    add(f"\t\t\tname = {EXT_NAME};")
    add(f"\t\t\tproductName = {EXT_NAME};")
    add(f"\t\t\tproductReference = {ext_product} /* {EXT_PRODUCT} */;")
    add("\t\t\tproductType = \"com.apple.product-type.app-extension\";")
    add("\t\t};")
    add("/* End PBXNativeTarget section */")

    # -- PBXProject --------------------------------------------------------
    project = uid("project", APP_NAME)
    project_config_list = uid("configlist", "project")
    add("")
    add("/* Begin PBXProject section */")
    add(f"\t\t{project} /* Project object */ = {{")
    add("\t\t\tisa = PBXProject;")
    add("\t\t\tattributes = {")
    add("\t\t\t\tBuildIndependentTargetsInParallel = 1;")
    add("\t\t\t\tLastSwiftUpdateCheck = 1620;")
    add("\t\t\t\tLastUpgradeCheck = 1620;")
    add("\t\t\t\tTargetAttributes = {")
    add(f"\t\t\t\t\t{app_target} = {{ CreatedOnToolsVersion = 16.2; }};")
    add(f"\t\t\t\t\t{ext_target} = {{ CreatedOnToolsVersion = 16.2; }};")
    add("\t\t\t\t};")
    add("\t\t\t};")
    add(f"\t\t\tbuildConfigurationList = {project_config_list} /* Build configuration list for PBXProject \"{APP_NAME}\" */;")
    add("\t\t\tcompatibilityVersion = \"Xcode 14.0\";")
    add("\t\t\tdevelopmentRegion = en;")
    add("\t\t\thasScannedForEncodings = 0;")
    add("\t\t\tknownRegions = (")
    add("\t\t\t\ten,")
    add("\t\t\t\tBase,")
    add("\t\t\t);")
    add(f"\t\t\tmainGroup = {main_group};")
    add(f"\t\t\tproductRefGroup = {products_group} /* Products */;")
    add("\t\t\tprojectDirPath = \"\";")
    add("\t\t\tprojectRoot = \"\";")
    add("\t\t\ttargets = (")
    add(f"\t\t\t\t{app_target} /* {APP_NAME} */,")
    add(f"\t\t\t\t{ext_target} /* {EXT_NAME} */,")
    add("\t\t\t);")
    add("\t\t};")
    add("/* End PBXProject section */")

    # -- PBXResourcesBuildPhase --------------------------------------------
    add("")
    add("/* Begin PBXResourcesBuildPhase section */")
    add(f"\t\t{app_resources_phase} /* Resources */ = {{")
    add("\t\t\tisa = PBXResourcesBuildPhase;")
    add("\t\t\tbuildActionMask = 2147483647;")
    add("\t\t\tfiles = (")
    add(f"\t\t\t\t{assets_build} /* Assets.xcassets in Resources */,")
    add("\t\t\t);")
    add("\t\t\trunOnlyForDeploymentPostprocessing = 0;")
    add("\t\t};")
    add(f"\t\t{ext_resources_phase} /* Resources */ = {{")
    add("\t\t\tisa = PBXResourcesBuildPhase;")
    add("\t\t\tbuildActionMask = 2147483647;")
    add("\t\t\tfiles = (")
    add("\t\t\t);")
    add("\t\t\trunOnlyForDeploymentPostprocessing = 0;")
    add("\t\t};")
    add("/* End PBXResourcesBuildPhase section */")

    # -- PBXSourcesBuildPhase ----------------------------------------------
    add("")
    add("/* Begin PBXSourcesBuildPhase section */")
    for phase, files in ((app_sources_phase, app_sources), (ext_sources_phase, ext_sources)):
        add(f"\t\t{phase} /* Sources */ = {{")
        add("\t\t\tisa = PBXSourcesBuildPhase;")
        add("\t\t\tbuildActionMask = 2147483647;")
        add("\t\t\tfiles = (")
        for build_id in files:
            add(f"\t\t\t\t{build_id} /* in Sources */,")
        add("\t\t\t);")
        add("\t\t\trunOnlyForDeploymentPostprocessing = 0;")
        add("\t\t};")
    add("/* End PBXSourcesBuildPhase section */")

    # -- PBXTargetDependency -----------------------------------------------
    proxy = uid("proxy", EXT_NAME)
    add("")
    add("/* Begin PBXTargetDependency section */")
    add(f"\t\t{dependency} /* PBXTargetDependency */ = {{")
    add("\t\t\tisa = PBXTargetDependency;")
    add(f"\t\t\ttarget = {ext_target} /* {EXT_NAME} */;")
    add(f"\t\t\ttargetProxy = {proxy} /* PBXContainerItemProxy */;")
    add("\t\t};")
    add("/* End PBXTargetDependency section */")

    add("")
    add("/* Begin PBXContainerItemProxy section */")
    add(f"\t\t{proxy} /* PBXContainerItemProxy */ = {{")
    add("\t\t\tisa = PBXContainerItemProxy;")
    add(f"\t\t\tcontainerPortal = {project} /* Project object */;")
    add("\t\t\tproxyType = 1;")
    add(f"\t\t\tremoteGlobalIDString = {ext_target};")
    add(f"\t\t\tremoteInfo = {EXT_NAME};")
    add("\t\t};")
    add("/* End PBXContainerItemProxy section */")

    # -- XCBuildConfiguration ----------------------------------------------
    add("")
    add("/* Begin XCBuildConfiguration section */")

    def configuration(ref, name, settings):
        add(f"\t\t{ref} /* {name} */ = {{")
        add("\t\t\tisa = XCBuildConfiguration;")
        add("\t\t\tbuildSettings = {")
        for key in sorted(settings):
            add(f"\t\t\t\t{key} = {settings[key]};")
        add("\t\t\t};")
        add(f"\t\t\tname = {name};")
        add("\t\t};")

    project_common = {
        "ALWAYS_SEARCH_USER_PATHS": "NO",
        "CLANG_ENABLE_MODULES": "YES",
        "CLANG_ENABLE_OBJC_ARC": "YES",
        "ENABLE_STRICT_OBJC_MSGSEND": "YES",
        "ENABLE_USER_SCRIPT_SANDBOXING": "YES",
        "GCC_C_LANGUAGE_STANDARD": "gnu17",
        "IPHONEOS_DEPLOYMENT_TARGET": DEPLOYMENT_TARGET,
        "LOCALIZATION_PREFERS_STRING_CATALOGS": "YES",
        "MTL_FAST_MATH": "YES",
        "SDKROOT": "iphoneos",
        "SWIFT_EMIT_LOC_STRINGS": "YES",
        "SWIFT_VERSION": "5.0",
    }
    debug_only = {
        "DEBUG_INFORMATION_FORMAT": "dwarf",
        "ENABLE_TESTABILITY": "YES",
        "GCC_OPTIMIZATION_LEVEL": "0",
        "GCC_PREPROCESSOR_DEFINITIONS": "(\n\t\t\t\t\t\"DEBUG=1\",\n\t\t\t\t\t\"$(inherited)\",\n\t\t\t\t)",
        "MTL_ENABLE_DEBUG_INFO": "INCLUDE_SOURCE",
        "ONLY_ACTIVE_ARCH": "YES",
        "SWIFT_ACTIVE_COMPILATION_CONDITIONS": "\"DEBUG $(inherited)\"",
        "SWIFT_OPTIMIZATION_LEVEL": "\"-Onone\"",
    }
    release_only = {
        "DEBUG_INFORMATION_FORMAT": "\"dwarf-with-dsym\"",
        "ENABLE_NS_ASSERTIONS": "NO",
        "MTL_ENABLE_DEBUG_INFO": "NO",
        "SWIFT_COMPILATION_MODE": "wholemodule",
        "VALIDATE_PRODUCT": "YES",
    }

    configuration(uid("config", "project", "Debug"), "Debug", {**project_common, **debug_only})
    configuration(uid("config", "project", "Release"), "Release", {**project_common, **release_only})

    target_common = {
        "ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME": "AccentColor",
        "CODE_SIGN_STYLE": "Automatic",
        "CURRENT_PROJECT_VERSION": "1",
        "GENERATE_INFOPLIST_FILE": "NO",
        "MARKETING_VERSION": MARKETING_VERSION,
        "SWIFT_EMIT_LOC_STRINGS": "YES",
        "TARGETED_DEVICE_FAMILY": "\"1,2\"",
    }
    app_settings = {
        **target_common,
        "ASSETCATALOG_COMPILER_APPICON_NAME": "AppIcon",
        "CODE_SIGN_ENTITLEMENTS": "App/VoiceAppV4.entitlements",
        "ENABLE_PREVIEWS": "YES",
        "INFOPLIST_FILE": "App/Info.plist",
        "LD_RUNPATH_SEARCH_PATHS": "(\n\t\t\t\t\t\"$(inherited)\",\n\t\t\t\t\t\"@executable_path/Frameworks\",\n\t\t\t\t)",
        "PRODUCT_BUNDLE_IDENTIFIER": APP_BUNDLE_ID,
        "PRODUCT_NAME": "\"Voice App V4\"",
    }
    ext_settings = {
        **target_common,
        "CODE_SIGN_ENTITLEMENTS": "Keyboard/VoiceKeyboard.entitlements",
        "ENABLE_PREVIEWS": "YES",
        "INFOPLIST_FILE": "Keyboard/Info.plist",
        "LD_RUNPATH_SEARCH_PATHS": "(\n\t\t\t\t\t\"$(inherited)\",\n\t\t\t\t\t\"@executable_path/Frameworks\",\n\t\t\t\t\t\"@executable_path/../../Frameworks\",\n\t\t\t\t)",
        "PRODUCT_BUNDLE_IDENTIFIER": EXT_BUNDLE_ID,
        "PRODUCT_NAME": "\"$(TARGET_NAME)\"",
        "SKIP_INSTALL": "YES",
    }

    configuration(uid("config", APP_NAME, "Debug"), "Debug", app_settings)
    configuration(uid("config", APP_NAME, "Release"), "Release", app_settings)
    configuration(uid("config", EXT_NAME, "Debug"), "Debug", ext_settings)
    configuration(uid("config", EXT_NAME, "Release"), "Release", ext_settings)
    add("/* End XCBuildConfiguration section */")

    # -- XCConfigurationList -----------------------------------------------
    add("")
    add("/* Begin XCConfigurationList section */")

    def configuration_list(ref, label, debug, release):
        add(f"\t\t{ref} /* Build configuration list for {label} */ = {{")
        add("\t\t\tisa = XCConfigurationList;")
        add("\t\t\tbuildConfigurations = (")
        add(f"\t\t\t\t{debug} /* Debug */,")
        add(f"\t\t\t\t{release} /* Release */,")
        add("\t\t\t);")
        add("\t\t\tdefaultConfigurationIsVisible = 0;")
        add("\t\t\tdefaultConfigurationName = Release;")
        add("\t\t};")

    configuration_list(
        project_config_list, f"PBXProject \"{APP_NAME}\"",
        uid("config", "project", "Debug"), uid("config", "project", "Release"),
    )
    configuration_list(
        app_config_list, f"PBXNativeTarget \"{APP_NAME}\"",
        uid("config", APP_NAME, "Debug"), uid("config", APP_NAME, "Release"),
    )
    configuration_list(
        ext_config_list, f"PBXNativeTarget \"{EXT_NAME}\"",
        uid("config", EXT_NAME, "Debug"), uid("config", EXT_NAME, "Release"),
    )
    add("/* End XCConfigurationList section */")

    add("\t};")
    add(f"\trootObject = {project} /* Project object */;")
    add("}")

    PROJECT_DIR.mkdir(parents=True, exist_ok=True)
    (PROJECT_DIR / "project.pbxproj").write_text("\n".join(lines) + "\n")

    scheme_dir = PROJECT_DIR / "xcshareddata/xcschemes"
    scheme_dir.mkdir(parents=True, exist_ok=True)
    (scheme_dir / f"{APP_NAME}.xcscheme").write_text(scheme(app_target, ext_target))

    print(f"wrote {PROJECT_DIR/'project.pbxproj'} "
          f"({len(app)} app + {len(ext)} keyboard + {len(shared)} shared sources)")


def quote(value):
    """Quotes a pbxproj value only when it needs it."""
    if value and all(c.isalnum() or c in "._/" for c in value):
        return value
    return '"%s"' % value.replace('"', '\\"')


def scheme(app_target, ext_target):
    return f"""<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion = "1620" version = "1.7">
   <BuildAction parallelizeBuildables = "YES" buildImplicitDependencies = "YES">
      <BuildActionEntries>
         <BuildActionEntry buildForTesting = "YES" buildForRunning = "YES" buildForProfiling = "YES" buildForArchiving = "YES" buildForAnalyzing = "YES">
            <BuildableReference
               BuildableIdentifier = "primary"
               BlueprintIdentifier = "{app_target}"
               BuildableName = "{APP_PRODUCT}"
               BlueprintName = "{APP_NAME}"
               ReferencedContainer = "container:{APP_NAME}.xcodeproj">
            </BuildableReference>
         </BuildActionEntry>
      </BuildActionEntries>
   </BuildAction>
   <LaunchAction buildConfiguration = "Debug" selectedDebuggerIdentifier = "Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier = "Xcode.DebuggerFoundation.Launcher.LLDB" launchStyle = "0" useCustomWorkingDirectory = "NO" ignoresPersistentStateOnLaunch = "NO" debugDocumentVersioning = "YES" debugServiceExtension = "internal" allowLocationSimulation = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "{app_target}"
            BuildableName = "{APP_PRODUCT}"
            BlueprintName = "{APP_NAME}"
            ReferencedContainer = "container:{APP_NAME}.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </LaunchAction>
   <ProfileAction buildConfiguration = "Release" shouldUseLaunchSchemeArgsEnv = "YES" savedToolIdentifier = "" useCustomWorkingDirectory = "NO" debugDocumentVersioning = "YES">
      <BuildableProductRunnable runnableDebuggingMode = "0">
         <BuildableReference
            BuildableIdentifier = "primary"
            BlueprintIdentifier = "{app_target}"
            BuildableName = "{APP_PRODUCT}"
            BlueprintName = "{APP_NAME}"
            ReferencedContainer = "container:{APP_NAME}.xcodeproj">
         </BuildableReference>
      </BuildableProductRunnable>
   </ProfileAction>
   <AnalyzeAction buildConfiguration = "Debug"></AnalyzeAction>
   <ArchiveAction buildConfiguration = "Release" revealArchiveInOrganizer = "YES"></ArchiveAction>
</Scheme>
"""


if __name__ == "__main__":
    main()
