"""Coordinator guard regression tests (stdlib-only)."""
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from integration_guard import inspect_browser


class IntegrationGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.app = self.root / "app"
        self.gradle = self.app / "build.gradle.kts"
        self.gradle.parent.mkdir(parents=True, exist_ok=True)
        self.gradle.write_text(
            'android { namespace = "com.night.browser"\n'
            'defaultConfig { applicationId = "com.night.browser"\n'
            'minSdk = 26 } }\n'
            'dependencies { implementation("org.mozilla.geckoview:geckoview:157.0") }\n',
            encoding="utf-8",
        )
        self.manifest = self.app / "src/main/AndroidManifest.xml"
        self.manifest.parent.mkdir(parents=True, exist_ok=True)
        self.manifest.write_text(
            '<manifest xmlns:android="http://schemas.android.com/apk/res/android">'
            '<uses-permission android:name="android.permission.INTERNET"/>'
            '<application android:allowBackup="false" android:usesCleartextTraffic="false">'
            '<activity android:name=".MainActivity"/>'
            '<provider android:name="androidx.core.content.FileProvider" '
            'android:authorities="com.night.browser.files" android:exported="false"/>'
            '</application></manifest>', encoding="utf-8",
        )
        self.kt = self.app / "src/main/java/com/night/browser/MainActivity.kt"
        self.kt.parent.mkdir(parents=True, exist_ok=True)
        self.kt.write_text("package com.night.browser\nclass MainActivity\n", encoding="utf-8")

    def test_clean_project(self):
        self.assertEqual([], inspect_browser(self.root))

    def test_both_approved_ids_supported_if_consistent(self):
        self.gradle.write_text(
            self.gradle.read_text().replace("com.night.browser", "com.nice.browser"),
            encoding="utf-8",
        )
        self.manifest.write_text(
            self.manifest.read_text().replace("com.night.browser", "com.nice.browser"),
            encoding="utf-8",
        )
        self.kt.write_text("package com.nice.browser\nclass MainActivity\n", encoding="utf-8")
        self.assertEqual([], inspect_browser(self.root))

    def test_detects_mismatched_id(self):
        self.gradle.write_text(self.gradle.read_text().replace(
            'applicationId = "com.night.browser"', 'applicationId = "com.nice.browser"'
        ), encoding="utf-8")
        self.assertTrue(any("differ" in error for error in inspect_browser(self.root)))

    def test_detects_insecure_provider(self):
        self.manifest.write_text(
            self.manifest.read_text().replace('android:exported="false"', 'android:exported="true"'),
            encoding="utf-8",
        )
        self.assertTrue(any("Provider must not be exported" in error
                            for error in inspect_browser(self.root)))

    def test_detects_backup_and_cleartext(self):
        self.manifest.write_text(
            self.manifest.read_text().replace('android:allowBackup="false"',
                                             'android:allowBackup="true"').replace(
                                             'android:usesCleartextTraffic="false"',
                                             'android:usesCleartextTraffic="true"'),
            encoding="utf-8",
        )
        errors = inspect_browser(self.root)
        self.assertTrue(any("allowBackup" in error for error in errors))
        self.assertTrue(any("usesCleartextTraffic" in error for error in errors))

    def test_detects_permission_and_foreign_kotlin_package(self):
        self.manifest.write_text(self.manifest.read_text().replace(
            "<application", '<uses-permission android:name="android.permission.READ_SMS"/><application'
        ), encoding="utf-8")
        self.kt.write_text("package com.example.browser\n", encoding="utf-8")
        errors = inspect_browser(self.root)
        self.assertTrue(any("READ_SMS" in error for error in errors))
        self.assertTrue(any("foreign/missing Kotlin package" in error for error in errors))

    def test_detects_min_sdk_change(self):
        self.gradle.write_text(
            self.gradle.read_text().replace("minSdk = 26", "minSdk = 28"),
            encoding="utf-8",
        )
        self.assertTrue(any("minSdk 26" in error for error in inspect_browser(self.root)))


if __name__ == "__main__":
    unittest.main()
