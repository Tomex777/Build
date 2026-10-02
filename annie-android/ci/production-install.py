"""Install the exact owner-signed universal release on each supported acceptance API."""
from pathlib import Path
import subprocess
import time
from PIL import Image

out = Path('production-evidence')
out.mkdir(exist_ok=True)
apk = Path('signed/production/Annie-1.0.0-universal-production-signed.apk')
assert apk.is_file()

def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=60)

def launch(label):
    adb('shell', 'am', 'force-stop', 'com.tomex777.annie')
    result = adb('shell', 'am', 'start', '-W', '-n', 'com.tomex777.annie/.MainActivity')
    (out / f'{label}-launch.txt').write_text(result)
    for attempt in range(20):
        adb('shell', 'uiautomator', 'dump', '/sdcard/annie-production.xml')
        xml = adb('shell', 'cat', '/sdcard/annie-production.xml')
        if 'Annie' in xml:
            break
        time.sleep(.5)
    else:
        raise AssertionError('Production Annie UI did not render')
    (out / f'{label}-ui.xml').write_text(xml)
    activities = adb('shell', 'dumpsys', 'activity', 'activities')
    (out / f'{label}-activities.txt').write_text(activities)
    assert any('com.tomex777.annie/.MainActivity' in line and 'ResumedActivity' in line
               for line in activities.splitlines()), 'Production MainActivity must be resumed'
    with (out / f'{label}.png').open('wb') as f:
        subprocess.run(['adb', 'exec-out', 'screencap', '-p'], stdout=f, check=True)
    image = Image.open(out / f'{label}.png').convert('RGB')
    w, h = image.size
    visible = sum(max(image.getpixel((x, y))) > 100
                  for y in range(h//20, h*19//20, 4) for x in range(w//10, w*9//10, 4))
    assert visible > 100, 'Production screenshot must contain actual app content'

try:
    (out / 'install.txt').write_text(adb('install', str(apk)))
    launch('fresh-install')
    (out / 'reinstall.txt').write_text(adb('install', '-r', str(apk)))
    launch('reinstall')
    (out / 'package.txt').write_text(adb('shell', 'dumpsys', 'package', 'com.tomex777.annie'))
    (out / 'ACCEPTANCE.txt').write_text('Permanent-key production APK installed, rendered and reinstalled successfully.\n')
finally:
    (out / 'logcat.txt').write_text(adb('logcat', '-d'))
