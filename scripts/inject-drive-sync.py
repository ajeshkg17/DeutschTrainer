from pathlib import Path

p = Path('appv4/src/main/java/com/ajesh/deutschtrainer/v4/MainActivity.kt')
s = p.read_text(encoding='utf-8')

# Add a separate preference handle used only to notice a cloud restore.
needle = '    private val prefs by lazy { getSharedPreferences("html_v4_secure", MODE_PRIVATE) }\n'
insert = needle + '    private val drivePrefs by lazy { getSharedPreferences(DriveSyncActivity.PREFS, MODE_PRIVATE) }\n'
if 'private val drivePrefs by lazy' not in s:
    if needle not in s:
        raise SystemExit('Could not find MainActivity prefs insertion point')
    s = s.replace(needle, insert, 1)

# Reload the HTML database after DriveSyncActivity restored a remote snapshot.
needle = '    override fun onInit(status: Int) {\n'
resume = '''    override fun onResume() {\n        super.onResume()\n        if (::webView.isInitialized && drivePrefs.getBoolean("restore_applied", false)) {\n            drivePrefs.edit().putBoolean("restore_applied", false).apply()\n            webView.reload()\n        }\n    }\n\n'''
if 'drivePrefs.getBoolean("restore_applied"' not in s:
    if needle not in s:
        raise SystemExit('Could not find MainActivity onResume insertion point')
    s = s.replace(needle, resume + needle, 1)

# Expose the HTML button to the native Drive screen.
needle = '        @JavascriptInterface\n        fun startSpeech(requestId: String) {\n'
bridge = '''        @JavascriptInterface\n        fun openDriveSync() {\n            runOnUiThread {\n                startActivity(Intent(this@MainActivity, DriveSyncActivity::class.java))\n            }\n        }\n\n'''
if 'fun openDriveSync()' not in s:
    if needle not in s:
        raise SystemExit('Could not find MainActivity bridge insertion point')
    s = s.replace(needle, bridge + needle, 1)

p.write_text(s, encoding='utf-8')
print('Drive sync bridge injected into MainActivity.kt')
