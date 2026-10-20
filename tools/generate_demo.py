#!/usr/bin/env python3
"""Create clearly synthetic demo map/height file/photo for offline workflow testing."""
from pathlib import Path
import numpy as np
import cv2
import struct
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]/'demo'
ROOT.mkdir(exist_ok=True)
n=1024
rng=np.random.default_rng(20261020)
y,x=np.indices((n,n))
# Synthetic distinguishable terrain texture with settlements, coast and winding roads.
texture=cv2.resize(rng.integers(0,256,(140,140),dtype=np.uint8),(n,n),interpolation=cv2.INTER_CUBIC)
texture=cv2.GaussianBlur(texture,(3,3),0)
elev=(45 + 65*np.exp(-((x-600)**2+(y-340)**2)/(2*190**2)) + 90*np.exp(-((x-320)**2+(y-730)**2)/(2*120**2))
      + 20*np.sin(x/70)*np.sin(y/83)).astype(np.float32)
img=np.zeros((n,n,3),np.uint8)
img[:,:,0]=np.clip(65+texture*0.14+elev*.35,0,255).astype('uint8')
img[:,:,1]=np.clip(83+texture*0.22+elev*.45,0,255).astype('uint8')
img[:,:,2]=np.clip(47+texture*0.16+elev*.24,0,255).astype('uint8')
cv2.circle(img,(860,150),175,(140,90,46),-1)
cv2.ellipse(img,(130,120),(160,230),-30,0,360,(157,105,55),-1)
for i in range(20):
 a=(i*37 + 130)%n;b=(i*97+220)%n
 cv2.rectangle(img,(a,b),(min(a+9+(i%3)*9,1023),min(b+12+(i%4)*7,1023)),(115,130,150),-1)
for k in range(7):
 pp=[]
 for t in np.linspace(0,n-1,55):
  xp=int(t); yp=int(120+k*115+60*np.sin(t/132+k*1.2))
  pp.append((xp,max(0,min(n-1,yp))))
 cv2.polylines(img,[np.array(pp,np.int32)],False,(140,165,183),5,cv2.LINE_AA)
for k in range(5):
 pp=[]
 for t in np.linspace(0,n-1,55):
  yp=int(t); xp=int(145+k*140+43*np.cos(t/92+k*.8))
  pp.append((max(0,min(n-1,xp)),yp))
 cv2.polylines(img,[np.array(pp,np.int32)],False,(158,174,192),5,cv2.LINE_AA)
for i in range(1,10):
 cv2.line(img,(i*n//10,0),(i*n//10,n-1),(90,104,92),1)
 cv2.line(img,(0,i*n//10),(n-1,i*n//10),(90,104,92),1)
for text,pt in [('SYNTHETIC MAP',(170,125)),('NOT REAL PUBG',(370,630)),('DEMO 8000m',(70,945))]:
 cv2.putText(img,text,pt,cv2.FONT_HERSHEY_SIMPLEX,1,(240,245,220),3,cv2.LINE_AA)
cv2.imwrite(str(ROOT/'demo_reference.png'),img)
raw=np.clip(np.round(32768+elev*128),0,65535).astype('<u2')
with (ROOT/'demo_height.hmap').open('wb') as file:
 file.write(b'HMAP'+struct.pack('<II',n,n)+raw.tobytes(order='C'))
# Simulate an enlarged PUBG-map screen section, slight monitor perspective & photographic noise.
# 640px crop from reference, projected onto phone camera shot with background.
crop=img[175:815,200:890]
canvas=np.full((1050,1340,3), (21,21,29), dtype=np.uint8)
pts_src=np.float32([[0,0],[crop.shape[1]-1,0],[crop.shape[1]-1,crop.shape[0]-1],[0,crop.shape[0]-1]])
pts_dst=np.float32([[160,90],[1195,115],[1160,950],[180,875]])
warp=cv2.getPerspectiveTransform(pts_src,pts_dst)
layer=cv2.warpPerspective(crop,warp,(1340,1050))
mask=cv2.warpPerspective(np.ones(crop.shape[:2],np.uint8)*255,warp,(1340,1050))
canvas[mask>0]=layer[mask>0]
cv2.rectangle(canvas,(60,25),(1280,1015),(75,75,78),6)
# photo style sensor noise
noise=rng.normal(0,2.5,canvas.shape)
canvas=np.clip(canvas.astype(np.float32)+noise,0,255).astype('uint8')
cv2.imwrite(str(ROOT/'demo_camera_photo.jpg'),canvas,[cv2.IMWRITE_JPEG_QUALITY,93])
print('Generated synthetic demo files; calibration: width=8000, raw unit = 0.0078125m; NOT GAME ASSETS')
