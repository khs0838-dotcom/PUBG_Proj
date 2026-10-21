#!/usr/bin/env python3
"""Compile/run the production Java matcher on desktop OpenCV (no Android emulator).

Uses synthetic crops/perspective warps, not a measurement of real camera accuracy.
Java 17+ with javac is required. Test-only desktop JNI is not bundled in the APK.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
CACHE = Path(os.environ.get('PUBGMORTAR_TEST_CACHE', str(Path(tempfile.gettempdir()) / 'pubg-map-matcher')))
URL = 'https://repo.maven.apache.org/maven2/org/openpnp/opencv/4.9.0-0/opencv-4.9.0-0.jar'
SHA256 = '17823d249cb8ba18bc3cdb878db84936694d1cfaeac0f95517e320c8a8c458d9'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--markers-only',action='store_true',help='Run only multi-target marker regressions')
    args=parser.parse_args()
    CACHE.mkdir(parents=True, exist_ok=True)
    jar = CACHE / 'opencv.jar'
    if not jar.exists():
        staging = CACHE / 'opencv.download'
        urllib.request.urlretrieve(URL, staging)
        if hashlib.sha256(staging.read_bytes()).hexdigest() != SHA256:
            raise RuntimeError('OpenCV test dependency checksum mismatch')
        staging.replace(jar)
    if hashlib.sha256(jar.read_bytes()).hexdigest() != SHA256:
        raise RuntimeError('OpenCV test dependency checksum mismatch')
    java_home = os.environ.get('JAVA_HOME')
    def executable(name):
        path = str(Path(java_home) / 'bin' / name) if java_home else shutil.which(name)
        if not path or not Path(path).is_file():
            raise RuntimeError(f'{name} is required (use a full JDK)')
        return path
    classes = CACHE / 'classes'
    classes.mkdir(exist_ok=True)
    sources = [ROOT / 'app/src/main/java/com/example/pubgmortar' / name for name in ['MapFeatureMatcher.java','MarkerFeatureDetector.java','MortarMath.java']]
    sources += sorted((ROOT / 'tools/native-tests').glob('*.java'))
    subprocess.run([executable('javac'), '-cp', str(jar), '-d', str(classes), *map(str, sources)], check=True)
    command=[executable('java'), '-Xmx1g', '-cp', os.pathsep.join([str(classes), str(jar)])]
    if not args.markers_only:
        subprocess.run(command+['MapMatcherRegression', str(ROOT / 'app/src/main/assets/maps/erangel.jpg'),
                               str(ROOT / 'app/src/main/assets/maps/miramar.jpg')],check=True,cwd=ROOT)
    subprocess.run(command+['MarkerDetectorRegression'],check=True,cwd=ROOT)


if __name__ == '__main__':
    main()
