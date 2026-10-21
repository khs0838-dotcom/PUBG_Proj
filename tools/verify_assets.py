#!/usr/bin/env python3
"""Refuse a release with missing/offline-invalid tactical maps.

This inspects build-time assets. tools/verify_apk.py validates FINAL packaging separately.
The optional --require-height flag requires >=1 historical height file to be embedded.
It does NOT attest elevation scale or alignment to modern PUBG maps.
"""
import argparse
import json
import struct
import sys
from pathlib import Path
from PIL import Image


def validate(base: Path, require_height: bool = False):
    cat = base / 'maps/catalog.json'
    if not cat.is_file():
        raise ValueError('maps/catalog.json 누락: 지도 다운로드 스크립트를 먼저 실행해야 합니다.')
    maps = json.loads(cat.read_text(encoding='utf-8')).get('maps', [])
    if not maps:
        raise ValueError('maps/catalog.json의 지도가 0개입니다.')
    heights = 0
    for item in maps:
        rel = item['image']
        file = base / rel
        if not file.is_file() or file.stat().st_size < 10000:
            raise ValueError(f"기준 지도 파일 누락/손상: {rel}")
        with Image.open(file) as image:
            image.verify()
        with Image.open(file) as image:
            if image.format != 'JPEG' or min(image.size) < 256:
                raise ValueError(f"지도 파일 형식/해상도 오류: {rel}")
        if not (float(item['world_meters']) > 100):
            raise ValueError(f"맵 너비 설정 오류: {item['id']}")
        elev = item.get('height', '')
        if elev:
            h = base / elev
            if not h.is_file():
                raise ValueError(f"고도 파일 누락: {elev}")
            with h.open('rb') as f:
                magic = f.read(4)
                dims = f.read(8)
                if magic != b'HMAP' or len(dims) != 8:
                    raise ValueError(f'고도 파일 헤더 오류: {elev}')
                width, height = struct.unpack('<II', dims)
                if not (2 <= width <= 4096 and 2 <= height <= 4096):
                    raise ValueError(f'고도맵 크기 오류: {elev}')
                if h.stat().st_size != 12 + width * height * 2:
                    raise ValueError(f'고도 파일 바이트 수 오류: {elev}')
            heights += 1
    if require_height and heights == 0:
        raise ValueError('고도 데이터가 0개입니다. 고도 계산이 필수라면 APK를 배포할 수 없습니다.')
    print(f'OK: 지도 {len(maps)}개, 역사적 고도 파일 {heights}개 검증됨')
    if heights == 0:
        print('WARNING: 고도맵 0개. 거리 계산만 가능하며 고도 보정은 불가능합니다.')
    else:
        print('WARNING: 고도맵의 현재 게임 버전 정합성과 실제 고도 스케일은 검증되지 않았습니다.')
    return len(maps), heights


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('asset_root', type=Path, help='앱 내 assets 디렉터리 (maps/ 하위 포함)')
    parser.add_argument('--require-height', action='store_true')
    args = parser.parse_args()
    try:
        validate(args.asset_root, args.require_height)
    except Exception as exc:
        print('ASSET VALIDATION FAILED:', exc, file=sys.stderr)
        sys.exit(1)
