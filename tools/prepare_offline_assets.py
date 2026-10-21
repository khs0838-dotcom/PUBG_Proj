#!/usr/bin/env python3
"""Build-time downloader: ship PUBG tactical maps *inside APK*, never ask player to import files.

Internet is used by GitHub Actions at APK build time, not by the user while playing.
Terrain sources are historical. If missing/incompatible, elevation intentionally unavailable.
Maps are distributed by PUBG for developers subject to their Terms of Use.
"""
import io
import json
import os
import re
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image, ImageOps

ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'app/src/main/assets/maps'
DEST.mkdir(parents=True,exist_ok=True)
HEADERS={'User-Agent':'PUBG-Mortar-Android-Build-Asset-Fetch/0.2','Accept':'application/vnd.github+json'}
if os.environ.get('GITHUB_TOKEN'):
    HEADERS['Authorization']='Bearer '+os.environ['GITHUB_TOKEN']

# The dimensions are *nominal* 1km-grid map extents. Map image margins/version affect precision.
MAPS=[
    ('erangel','에란겔','Baltic_Main',8000),
    ('miramar','미라마','Desert_Main',8000),
    ('sanhok','사녹','Savage_Main',4000),
    ('vikendi','비켄디','DihorOtok_Main',6000),
    ('taego','태이고','Tiger_Main',8000),
    ('deston','데스턴','Kiki_Main',8000),
    ('rondo','론도','Neon_Main',8000),
    ('karakin','카라킨','Summerland_Main',2000),
    ('paramo','파라모','Chimera_Main',3000),
]
LEGACY_HEIGHT_IDS={'erangel','miramar','sanhok','vikendi','karakin'}

def request(url,limit=160*1024*1024):
    req=urllib.request.Request(url,headers=HEADERS)
    with urllib.request.urlopen(req,timeout=90) as r:
        content=io.BytesIO()
        while True:
            chunk=r.read(1024*1024)
            if not chunk:break
            content.write(chunk)
            if content.tell()>limit:raise ValueError('Asset exceeds per-file limit: '+url)
        return content.getvalue()

def json_api(url): return json.loads(request(url,12*1024*1024))

def pick_map(map_files,prefix):
    # Some assets have No_Text / different pixel sizes. Use a consistent latest non-overlay image.
    choices=[prefix+'_Low_Res.png',prefix+'_No_Text_Low_Res.png',
             prefix+'_High_Res.png',prefix+'_No_Text_High_Res.png']
    for filename in choices:
        if filename in map_files:return map_files[filename]['download_url']
    return None

def prep_one_map(entry,map_files):
    map_id,title,prefix,meters=entry
    url=pick_map(map_files,prefix)
    if url is None:
        print('SKIP: official tactical map not published:',prefix,flush=True)
        return None
    print('Fetching official tactical map:',prefix,flush=True)
    data=request(url)
    image=Image.open(io.BytesIO(data))
    image.load() # reject LFS pointers, HTML, and truncated assets
    if image.width<256 or image.height<256:
        raise ValueError('Map appears to be a placeholder: '+prefix)
    img=ImageOps.exif_transpose(image).convert('RGB')
    if max(img.size)>2000: img.thumbnail((2000,2000),Image.Resampling.LANCZOS)
    fname=map_id+'.jpg'
    img.save(DEST/fname,'JPEG',quality=91,subsampling=0,optimize=True)
    return {'id':map_id,'title':title,'image':'maps/'+fname,
            'world_meters':meters,'height':'','height_scale_m':0.0,
            'height_verified':False,'map_original':prefix}

def height_tree():
    # Old extraction release, NOT confirmed accurate for new terrain / world position.
    url='https://api.github.com/repos/cgcostume/pubg-maps/git/trees/v5.7.6?recursive=1'
    try:
        tree=json_api(url)
        return [n['path'] for n in tree.get('tree',[]) if n.get('type')=='blob']
    except Exception as exc:
        print('WARN: historical height index unavailable:',exc,flush=True)
        return []

def fetch_legacy_height(map_info, paths):
    id=map_info['id']
    if id not in LEGACY_HEIGHT_IDS:return
    candidates=[p for p in paths if p.lower().endswith('.png') and
                re.search(r'height_l16_lod0',p.lower()) and
                (p.lower().startswith(id+'/') or ('_'+id+'_') in p.lower())]
    if len(candidates)!=1:
        print('Height unavailable for',id,'candidate count:',len(candidates),flush=True)
        return
    path=candidates[0]
    # GitHub serves large tracked PNGs through raw.githubusercontent.com.
    url='https://raw.githubusercontent.com/cgcostume/pubg-maps/v5.7.6/'+urllib.parse.quote(path,safe='/')
    try:
        print('Fetching historical elevation:',id,flush=True)
        b=request(url)
        h=Image.open(io.BytesIO(b))
        if h.mode not in ('I;16','I;16L','I'):
            raise ValueError('Height not 16-bit: '+str(h.mode))
        if h.width<1024 or h.height<1024:
            raise ValueError('Height resource looks like low-quality preview')
        # PNG pixels have unsigned raw UE values; preserve data before resampling.
        h=h.convert('I');h.thumbnail((1024,1024),Image.Resampling.BILINEAR)
        px=list(h.getdata())
        if min(px)<0 or max(px)>65535: raise ValueError('Invalid uint16 height')
        fname=id+'.hmap'
        with open(DEST/fname,'wb') as out:
            out.write(b'HMAP'+struct.pack('<II',h.width,h.height))
            # Large maps are ~2 MiB after downscaling and packaging.
            for start in range(0,len(px),16384):
                chunk=px[start:start+16384]
                out.write(struct.pack('<'+'H'*len(chunk),*chunk))
        map_info['height']='maps/'+fname
        # Illustrative assumption for landscape Z-scale 100. NOT verified for each map.
        map_info['height_scale_m']=1.0/128.0
        map_info['height_verified']=False
        print('Bundled unverified historical elevation:',id,flush=True)
    except Exception as exc:
        print('WARN: height for',id,'was not included:',exc,flush=True)
        (DEST/(id+'.hmap')).unlink(missing_ok=True)

def main():
    for asset in DEST.glob('*'):
        if asset.suffix in ('.jpg','.hmap') or asset.name=='catalog.json':asset.unlink()
    files=json_api('https://api.github.com/repos/pubg/api-assets/contents/Assets/Maps?ref=master')
    index={f['name']:f for f in files if f.get('type')=='file'}
    ready=[]
    for item in MAPS:
        try:
            m=prep_one_map(item,index)
            if m:ready.append(m)
        except Exception as exc:
            print('WARN: unable to prepare map',item[0],exc,flush=True)
    if not ready:
        raise RuntimeError('No official map images were downloaded. Failing APK build instead of shipping empty/fictional maps.')
    paths=height_tree() if os.environ.get('FETCH_LEGACY_HEIGHT','1')=='1' else []
    for m in ready:
        fetch_legacy_height(m,paths)
    (DEST/'catalog.json').write_text(json.dumps({'maps':ready,'height_note':
        'Historical terrain samples are NOT verified as aligned to modern tactical imagery.'},
        ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print('Map assets ready:',len(ready),'with historical heights:',sum(bool(m['height']) for m in ready),flush=True)

if __name__=='__main__':main()
