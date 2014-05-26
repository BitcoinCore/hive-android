# Hive for Android #

This is the Hive Android repository. The code is based on the Schildbach Android
wallet. If you would like to get access to beta builds, feel free to request
membership to the G+ community "Hive Android Beta Testers" (
https://plus.google.com/communities/112207854940513664704 ) and follow the
process outlined there to get beta updates via Google Play.

## Building from source ##

These build instructions are written with a Linux environment in mind. Feedback
regarding other build environments is welcome!

You will first need to install the Android SDK from
http://developer.android.com/sdk/index.html . Make sure you have the latest API
level installed (API 19, as of 05/2014) by running tools/android to open the
Android SDK Manager. Then set the environment variable ANDROID\_HOME to point to
the SDK folder.

The tool 'Maven Android SDK Deployer' is used to make the Android SDK components
available to the Maven build system. Clone the repository at
https://github.com/mosabua/maven-android-sdk-deployer and install the required
dependencies via the commands below. You need at least Maven 3.1.

````
cd platforms/android-19; mvn install        # for API 19
cd tools/annotations; mvn install           # for 'Android annotations'
cd extras/compatibility-v4; mvn install     # for 'compatibility-v4'
````

Now you should be ready to compile Hive Android. From the root of the repository
run:

````
mvn clean install
````

If you get any build errors, check first whether the versions of the
dependencies installed be the Android SDK manager match what Maven is looking
for. Then either install the correct versions or update the Maven build file to
the latest versions (pull requests welcome).

Tip: Running the following command inside the wallet subdirectory, will only compile,
package and deploy the core wallet app to any connected emulators or devices:

````
mvn compile package android:deploy
````

## Building a release version ##

The above build instructions will build a testnet version of Hive Android. The
necessary changes to the repository to turn that into a mainnet version are
maintained in the branch `hive-prod`. This branch is rebased against `master`
from time to time.

To perform a release:

- increase version number if necessary
  - in pom.xml: version
  - in AndroidManifest.xml: versionCode and versionName
- git checkout hive-prod
- git rebase -i master
- mvn clean install -Prelease
- the apk can now be founded as wallet/target/wallet-\<version\>-unsigned.apk
- sign with: jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore \<keystore file\> \<apk file\> \<alias\>
- align: zipalign 4 \<input apk\> \<output apk\>

This command can be used to create a release key:

````
keytool -genkey -v -alias hive -keyalg RSA -keysize 2048 -validity 10000 \
            -keystore hive-android-release-key.keystore
````

## Developing with Eclipse ##

Prepare Eclipse workspace via:

````
mvn clean install
mvn eclipse:configure-workspace -Declipse.workspace=/path/to/workspace/
mvn eclipse:eclipse
````

Then use File -> Import -> General -> 'Existing Project into Workspace to import
project' to import both 'integration-android' and 'wallet' from this repository
as two separate Eclipse projects.

Import the code formatting preference file `eclipse-code-format.xml`
via Window -> Preferences -> Java -> Code Style -> Formatter -> Import. Then
configure Eclipse to also apply these settings to Android XML files via Window
-> Preferences -> Android -> Editors -> 'Use Eclipse setting for indentation...'.

You still need to compile the code via Maven, but this setup should tell Eclipse
about all the dependencies and provide you with proper auto-complete.
