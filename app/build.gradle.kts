plugins {
    id("com.android.application")
}

android {
    namespace = "com.zipgrade.omrscanner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.zipgrade.omrscanner"
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "2.5.0-dev"
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
