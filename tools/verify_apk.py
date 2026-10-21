#!/usr/bin/env python3
"""Inspect the exact APK produced by Gradle, not only the source folder."""
import json
import sys
from pathlib import Path
from zipfile import ZipFile


def verify(apk):
    with ZipFile(apk) as z:
        files = set(z.namelist())
        catalog = 'assets/maps/catalog.json'
        if catalog not in files:
            raise ValueError('APK 안에 지도 목록 assets/maps/catalog.json 없음')
        maps = json.loads(z.read(catalog))['maps']
        if not maps:
            raise ValueError('APK 안에 기준 지도가 0개')
        heights = 0
        for m in maps:
            image = 'assets/' + m['image']
            if image not in files or z.getinfo(image).file_size < 10000:
                raise ValueError('APK 안에 기준 지도 누락/손상: '+image)
            if not z.read(image)[:3] == b'\xff\xd8\xff':
                raise ValueError('JPEG가 아닌 이미지: '+image)
            if m.get('height'):
                height = 'assets/' + m['height']
                if height not in files or z.read(height)[:4] != b'HMAP':
                    raise ValueError('APK 안에 고도 파일 누락/손상: '+height)
                heights += 1
        native = sorted(f for f in files if f.startswith('lib/') and 'opencv_java' in f and f.endswith('.so'))
        if not native:
            raise ValueError('APK 안에 OpenCV 네이티브 .so 파일 없음 (Gradle 의존성/ABI 확인)')
        print('APK VALIDATED:', apk)
        print('  내장 지도:', len(maps), [m['id'] for m in maps])
        print('  고도 파일:', heights, '(정합성/단위 미검증)')
        print('  OpenCV 라이브러리:', native)
        print('  주의: APK 내부 파일 검사이지 휴대폰 런타임 동작 검사는 아닙니다.')

if __name__ == '__main__':
    if len(sys.argv) != 2:
        print('Usage: verify_apk.py path/to/app-debug.apk')
        sys.exit(2)
    try:
        verify(Path(sys.argv[1]))
    except Exception as exc:
        print('APK VALIDATION FAILED:', exc, file=sys.stderr)
        sys.exit(1)
